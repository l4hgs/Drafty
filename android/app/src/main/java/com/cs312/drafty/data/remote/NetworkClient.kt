package com.cs312.drafty.data.remote

import android.util.Log
import com.cs312.drafty.BuildConfig
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

object NetworkClient {
    /**
     * Configured via `-PDRAFTY_API_URL=https://...` in Gradle (`BuildConfig.API_BASE_URL`).
     * Defaults to `http://127.0.0.1:8000/` for local USB/emulator dev (with automatic
     * 10.0.2.2 fallback when host is 127.0.0.1).
     */
    val BASE_URL: String = BuildConfig.API_BASE_URL
    private const val FALLBACK_HOST = "10.0.2.2"

    val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory()) // fallback; generated adapters take priority
        .build()

    private val hostFallbackInterceptor = Interceptor { chain ->
        val req = chain.request()
        try {
            chain.proceed(req)
        } catch (firstErr: IOException) {
            if (req.url.host == "127.0.0.1") {
                val fallbackUrl = req.url.newBuilder().host(FALLBACK_HOST).build()
                Log.w("NetworkClient", "127.0.0.1 unreachable; retrying via $fallbackUrl")
                chain.proceed(req.newBuilder().url(fallbackUrl).build())
            } else {
                throw firstErr
            }
        }
    }

    private val okHttp: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(45, TimeUnit.SECONDS)
            .readTimeout(45, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .addInterceptor(hostFallbackInterceptor)
            .addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC })
            .build()
    }

    val api: DraftyApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttp)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(DraftyApiService::class.java)
    }

    private val textType = "text/plain".toMediaType()
    private val audioType = "audio/m4a".toMediaType()

    fun audioPart(file: File): MultipartBody.Part =
        MultipartBody.Part.createFormData("file", file.name, file.asRequestBody(audioType))

    fun textBody(value: String) = value.toRequestBody(textType)

    /** Parses the backend's flat error body from a failed call, if present. */
    fun parseError(e: HttpException): ApiError? = parseErrorBody(readErrorBody(e))

    private fun readErrorBody(e: HttpException): String? = try {
        e.response()?.errorBody()?.string()
    } catch (_: Exception) {
        null
    }

    private fun parseErrorBody(raw: String?): ApiError? = try {
        raw?.let { moshi.adapter(ApiError::class.java).fromJson(it) }
    } catch (_: Exception) {
        null
    }

    /** Logs full technical details and returns a simple, human-friendly message for users. */
    fun describeError(e: HttpException): String {
        val raw = readErrorBody(e)
        Log.e("NetworkClient", "HTTP ${e.code()} body=$raw")
        val parsed = parseErrorBody(raw)
        return when (parsed?.code) {
            "NO_SPEECH" -> "We couldn't hear any speech. Hold Drafty and speak clearly into your mic."
            "AUDIO_TOO_SHORT" -> "That recording was too short. Hold Drafty and share a few more words."
            "AUDIO_TOO_LARGE" -> "That recording is a bit too long. Try keeping it under a few minutes."
            "UNSUPPORTED_MEDIA_TYPE" -> "Couldn't read that audio format. Please try recording again."
            "ASR_FAILED" -> "We had trouble understanding the audio. Please try again in a moment."
            "PARSE_FAILED" -> "Drafty couldn't structure that idea right now. Please try again."
            "DB_FAILED" -> "Couldn't save your draft right now. Please try again shortly."
            "CONTAINER_NOT_FOUND" -> "That idea could no longer be found."
            else -> when (e.code()) {
                404 -> "We couldn't find that item."
                413 -> "That recording is a bit too long. Please try a shorter note."
                422 -> "We couldn't process that recording. Try speaking a bit closer to your mic."
                in 500..599 -> "Drafty's servers are busy right now. Please try again in a moment."
                else -> "Something went wrong. Please try again."
            }
        }
    }
}
