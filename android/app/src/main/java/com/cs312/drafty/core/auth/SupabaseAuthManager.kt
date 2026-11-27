package com.cs312.drafty.core.auth

import android.content.Context
import android.net.Uri
import android.util.Log
import com.cs312.drafty.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class EmailConfirmationRequiredException(val email: String) :
    RuntimeException("Please verify $email using the link we sent to your inbox.")

data class AuthSession(
    val userId: String,
    val email: String,
    val displayName: String,
    val accessToken: String,
    val refreshToken: String,
    val expiresAtEpochSec: Long,
) {
    val isExpired: Boolean
        get() = expiresAtEpochSec > 0L && (System.currentTimeMillis() / 1000L) >= (expiresAtEpochSec - 30L)
}

/**
 * Lightweight Supabase GoTrue Auth client + persistent session store for unified
 * Email/Password and Google OAuth sign-in across Drafty and Drafted.
 */
class SupabaseAuthManager(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .build()

    private val jsonType = "application/json; charset=utf-8".toMediaType()

    private val _session = MutableStateFlow(loadStoredSession())
    val session: StateFlow<AuthSession?> = _session.asStateFlow()

    init {
        activeAccessToken = _session.value?.accessToken
    }

    val isConfigured: Boolean
        get() = BuildConfig.SUPABASE_URL.isNotBlank() && BuildConfig.SUPABASE_ANON_KEY.isNotBlank()

    fun buildGoogleOAuthUri(): Uri {
        val base = BuildConfig.SUPABASE_URL.trimEnd('/')
        return Uri.parse("$base/auth/v1/authorize").buildUpon()
            .appendQueryParameter("provider", "google")
            .appendQueryParameter("redirect_to", REDIRECT_URI)
            .build()
    }

    suspend fun signInWithEmail(email: String, password: String): Result<AuthSession> = withContext(Dispatchers.IO) {
        if (!isConfigured) return@withContext Result.failure(IllegalStateException(NOT_CONFIGURED_MSG))
        try {
            val body = JSONObject().apply {
                put("email", email.trim())
                put("password", password)
            }.toString()
            val req = Request.Builder()
                .url("${BuildConfig.SUPABASE_URL}/auth/v1/token?grant_type=password")
                .addHeader("apikey", BuildConfig.SUPABASE_ANON_KEY)
                .addHeader("Content-Type", "application/json")
                .post(body.toRequestBody(jsonType))
                .build()
            executeAuthRequest(req)
        } catch (e: Exception) {
            Log.e(TAG, "signInWithEmail failed", e)
            Result.failure(RuntimeException("Couldn't sign in right now. Check your connection and try again."))
        }
    }

    suspend fun signUpWithEmail(
        email: String,
        password: String,
        brandName: String,
        brandVoice: String = "",
    ): Result<AuthSession> = withContext(Dispatchers.IO) {
        if (!isConfigured) return@withContext Result.failure(IllegalStateException(NOT_CONFIGURED_MSG))
        try {
            val encodedRedirect = URLEncoder.encode(EMAIL_CONFIRM_REDIRECT_URI, "UTF-8")
            val body = JSONObject().apply {
                put("email", email.trim())
                put("password", password)
                put(
                    "data",
                    JSONObject().apply {
                        put("brand_name", brandName.trim())
                        put("brand_voice_prompt", brandVoice.trim())
                    },
                )
            }.toString()
            val req = Request.Builder()
                .url("${BuildConfig.SUPABASE_URL}/auth/v1/signup?redirect_to=$encodedRedirect")
                .addHeader("apikey", BuildConfig.SUPABASE_ANON_KEY)
                .addHeader("Content-Type", "application/json")
                .post(body.toRequestBody(jsonType))
                .build()
            executeAuthRequest(req, requestedEmail = email.trim())
        } catch (e: Exception) {
            Log.e(TAG, "signUpWithEmail failed", e)
            Result.failure(RuntimeException("Couldn't create your account right now. Please try again."))
        }
    }

    suspend fun resendSignupEmail(email: String): Result<Unit> = withContext(Dispatchers.IO) {
        if (!isConfigured) return@withContext Result.failure(IllegalStateException(NOT_CONFIGURED_MSG))
        try {
            val encodedRedirect = URLEncoder.encode(EMAIL_CONFIRM_REDIRECT_URI, "UTF-8")
            val body = JSONObject().apply {
                put("type", "signup")
                put("email", email.trim())
            }.toString()
            val req = Request.Builder()
                .url("${BuildConfig.SUPABASE_URL}/auth/v1/resend?redirect_to=$encodedRedirect")
                .addHeader("apikey", BuildConfig.SUPABASE_ANON_KEY)
                .addHeader("Content-Type", "application/json")
                .post(body.toRequestBody(jsonType))
                .build()
            http.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    Result.success(Unit)
                } else {
                    Result.failure(RuntimeException("Please wait a moment before resending."))
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "resendSignupEmail failed", e)
            Result.failure(RuntimeException("Couldn't resend email right now."))
        }
    }

    suspend fun refreshSessionIfNeeded(): Result<AuthSession?> = withContext(Dispatchers.IO) {
        val current = _session.value ?: return@withContext Result.success(null)
        if (!current.isExpired || current.refreshToken.isBlank() || !isConfigured) {
            return@withContext Result.success(current)
        }
        try {
            val body = JSONObject().apply { put("refresh_token", current.refreshToken) }.toString()
            val req = Request.Builder()
                .url("${BuildConfig.SUPABASE_URL}/auth/v1/token?grant_type=refresh_token")
                .addHeader("apikey", BuildConfig.SUPABASE_ANON_KEY)
                .addHeader("Content-Type", "application/json")
                .post(body.toRequestBody(jsonType))
                .build()
            executeAuthRequest(req)
        } catch (e: Exception) {
            Log.w(TAG, "refreshSession failed", e)
            Result.failure(e)
        }
    }

    /**
     * Parses `drafty://auth-callback#access_token=...&refresh_token=...` returned from Google OAuth,
     * fetches the verified Supabase user profile, and saves the unified AuthSession.
     */
    suspend fun handleOAuthCallback(uri: Uri): Result<AuthSession> = withContext(Dispatchers.IO) {
        if (!isConfigured) return@withContext Result.failure(IllegalStateException(NOT_CONFIGURED_MSG))
        try {
            val params = parseFragmentOrQuery(uri)
            val errorDesc = params["error_description"] ?: params["error"]
            if (!errorDesc.isNullOrBlank()) {
                return@withContext Result.failure(RuntimeException(errorDesc))
            }
            val accessToken = params["access_token"].orEmpty()
            val refreshToken = params["refresh_token"].orEmpty()
            val expiresIn = params["expires_in"]?.toLongOrNull() ?: 3600L
            if (accessToken.isBlank()) {
                return@withContext Result.failure(RuntimeException("Google sign-in didn't return a session token."))
            }

            val userReq = Request.Builder()
                .url("${BuildConfig.SUPABASE_URL}/auth/v1/user")
                .addHeader("apikey", BuildConfig.SUPABASE_ANON_KEY)
                .addHeader("Authorization", "Bearer $accessToken")
                .get()
                .build()
            http.newCall(userReq).execute().use { resp ->
                val raw = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    return@withContext Result.failure(RuntimeException("Couldn't verify your Google session."))
                }
                val userObj = JSONObject(raw)
                val userId = userObj.optString("id")
                val email = userObj.optString("email")
                val meta = userObj.optJSONObject("user_metadata")
                val displayName = meta?.optString("brand_name").orEmpty()
                    .ifBlank { meta?.optString("full_name").orEmpty() }
                    .ifBlank { meta?.optString("name").orEmpty() }
                    .ifBlank { email.substringBefore("@") }

                val session = AuthSession(
                    userId = userId,
                    email = email,
                    displayName = displayName,
                    accessToken = accessToken,
                    refreshToken = refreshToken,
                    expiresAtEpochSec = (System.currentTimeMillis() / 1000L) + expiresIn,
                )
                saveSession(session)
                Result.success(session)
            }
        } catch (e: Exception) {
            Log.e(TAG, "handleOAuthCallback failed", e)
            Result.failure(RuntimeException("Couldn't complete Google sign-in. Please try again."))
        }
    }

    fun signOut() {
        val token = _session.value?.accessToken
        prefs.edit().clear().apply()
        _session.value = null
        activeAccessToken = null
        if (!token.isNullOrBlank() && isConfigured) {
            runCatching {
                val req = Request.Builder()
                    .url("${BuildConfig.SUPABASE_URL}/auth/v1/logout")
                    .addHeader("apikey", BuildConfig.SUPABASE_ANON_KEY)
                    .addHeader("Authorization", "Bearer $token")
                    .post("{}".toRequestBody(jsonType))
                    .build()
                http.newCall(req).enqueue(object : okhttp3.Callback {
                    override fun onFailure(call: okhttp3.Call, e: java.io.IOException) = Unit
                    override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                        response.close()
                    }
                })
            }
        }
    }

    private fun executeAuthRequest(req: Request, requestedEmail: String = ""): Result<AuthSession> {
        http.newCall(req).execute().use { resp ->
            val raw = resp.body?.string().orEmpty()
            val obj = runCatching { JSONObject(raw) }.getOrNull()
            if (!resp.isSuccessful) {
                val msg = obj?.optString("error_description").orEmpty()
                    .ifBlank { obj?.optString("msg").orEmpty() }
                    .ifBlank { obj?.optString("message").orEmpty() }
                    .ifBlank { "Authentication failed (${resp.code})." }
                return Result.failure(RuntimeException(msg))
            }
            if (obj == null) {
                return Result.failure(RuntimeException("Unexpected auth response."))
            }
            val accessToken = obj.optString("access_token")
            val refreshToken = obj.optString("refresh_token")
            val expiresIn = obj.optLong("expires_in", 3600L)
            val userObj = obj.optJSONObject("user") ?: obj.takeIf { it.has("id") && it.has("email") }
            if (accessToken.isBlank() || userObj == null) {
                val pendingEmail = userObj?.optString("email").orEmpty().ifBlank { requestedEmail }
                return Result.failure(EmailConfirmationRequiredException(pendingEmail))
            }
            val userId = userObj.optString("id")
            val email = userObj.optString("email")
            val meta = userObj.optJSONObject("user_metadata")
            val displayName = meta?.optString("brand_name").orEmpty()
                .ifBlank { meta?.optString("full_name").orEmpty() }
                .ifBlank { email.substringBefore("@") }

            val session = AuthSession(
                userId = userId,
                email = email,
                displayName = displayName,
                accessToken = accessToken,
                refreshToken = refreshToken,
                expiresAtEpochSec = (System.currentTimeMillis() / 1000L) + expiresIn,
            )
            saveSession(session)
            return Result.success(session)
        }
    }

    private fun saveSession(session: AuthSession) {
        prefs.edit()
            .putString(KEY_USER_ID, session.userId)
            .putString(KEY_EMAIL, session.email)
            .putString(KEY_DISPLAY_NAME, session.displayName)
            .putString(KEY_ACCESS_TOKEN, session.accessToken)
            .putString(KEY_REFRESH_TOKEN, session.refreshToken)
            .putLong(KEY_EXPIRES_AT, session.expiresAtEpochSec)
            .apply()
        _session.value = session
        activeAccessToken = session.accessToken
    }

    private fun loadStoredSession(): AuthSession? {
        val uid = prefs.getString(KEY_USER_ID, null)?.takeIf { it.isNotBlank() } ?: return null
        val token = prefs.getString(KEY_ACCESS_TOKEN, null)?.takeIf { it.isNotBlank() } ?: return null
        return AuthSession(
            userId = uid,
            email = prefs.getString(KEY_EMAIL, "").orEmpty(),
            displayName = prefs.getString(KEY_DISPLAY_NAME, "").orEmpty(),
            accessToken = token,
            refreshToken = prefs.getString(KEY_REFRESH_TOKEN, "").orEmpty(),
            expiresAtEpochSec = prefs.getLong(KEY_EXPIRES_AT, 0L),
        )
    }

    private fun parseFragmentOrQuery(uri: Uri): Map<String, String> {
        val raw = uri.fragment?.takeIf { it.isNotBlank() } ?: uri.encodedQuery.orEmpty()
        if (raw.isBlank()) return emptyMap()
        return raw.split("&").mapNotNull { pair ->
            val idx = pair.indexOf('=')
            if (idx <= 0) null
            else {
                val k = URLDecoder.decode(pair.substring(0, idx), "UTF-8")
                val v = URLDecoder.decode(pair.substring(idx + 1), "UTF-8")
                k to v
            }
        }.toMap()
    }

    companion object {
        private const val TAG = "SupabaseAuthManager"
        private const val PREFS_NAME = "drafty_auth_session"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_EMAIL = "email"
        private const val KEY_DISPLAY_NAME = "display_name"
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_REFRESH_TOKEN = "refresh_token"
        private const val KEY_EXPIRES_AT = "expires_at"
        const val REDIRECT_URI = "drafty://auth-callback"
        val EMAIL_CONFIRM_REDIRECT_URI: String
            get() = BuildConfig.API_BASE_URL.trimEnd('/') + "/auth/verified"
        private const val NOT_CONFIGURED_MSG =
            "Supabase Auth isn't configured yet. Add DRAFTY_SUPABASE_URL and DRAFTY_SUPABASE_ANON_KEY to android/local.properties."

        @Volatile
        var activeAccessToken: String? = null
    }
}
