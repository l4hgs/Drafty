package com.cs312.drafty.ui

import android.app.Application
import android.content.Context
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.cs312.drafty.core.audio.AudioRecorder
import com.cs312.drafty.core.auth.AuthSession
import com.cs312.drafty.core.auth.SupabaseAuthManager
import com.cs312.drafty.core.network.OfflineQueueManager
import com.cs312.drafty.data.local.DraftyDatabase
import com.cs312.drafty.data.local.PendingVoiceNoteEntity
import com.cs312.drafty.data.local.toEntity
import com.cs312.drafty.data.local.toIdeaContainer
import com.cs312.drafty.data.remote.ContainerUpdateRequest
import com.cs312.drafty.data.remote.DraftedSyncRequest
import com.cs312.drafty.data.remote.IdeaContainer
import com.cs312.drafty.data.remote.NetworkClient
import com.cs312.drafty.data.remote.RegenerateHooksRequest
import com.cs312.drafty.data.remote.UserProfileRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

enum class ProcessingStep { TRANSCRIBING, STRUCTURING }

sealed interface RecordingState {
    data object Idle : RecordingState
    data class Recording(val elapsedSeconds: Int) : RecordingState
    data class Processing(val step: ProcessingStep) : RecordingState
    data class Success(val containerId: String) : RecordingState
    data class Error(val message: String) : RecordingState
}

sealed interface SyncState {
    data object Idle : SyncState
    data object Syncing : SyncState
    data class Synced(val scheduledFor: String?) : SyncState
    data class Error(val message: String) : SyncState
}

/** A container as shown in the UI, with its local-only state. */
data class IdeaCard(val idea: IdeaContainer, val isPushed: Boolean, val createdAt: Long)

data class UserProfileState(
    val userId: String,
    val email: String = "",
    val brandName: String = "",
    val brandVoicePrompt: String = "",
) {
    val isSignedIn: Boolean get() = email.isNotBlank() && brandName.isNotBlank()
}

class DraftyViewModel(app: Application) : AndroidViewModel(app) {

    private val dao = DraftyDatabase.get(app).dao()
    private val api = NetworkClient.api
    private val recorder = AudioRecorder(app)
    private val offlineQueue = OfflineQueueManager(app, dao, api, viewModelScope)
    private val prefs = app.getSharedPreferences("drafty", Context.MODE_PRIVATE)
    val authManager = SupabaseAuthManager(app)
    val authSession: StateFlow<AuthSession?> = authManager.session
    val isSupabaseAuthConfigured: Boolean get() = authManager.isConfigured

    private val _userProfile = MutableStateFlow(
        UserProfileState(
            userId = authManager.session.value?.userId
                ?: prefs.getString("user_id", null)
                ?: UUID.randomUUID().toString().also { prefs.edit().putString("user_id", it).apply() },
            email = authManager.session.value?.email?.ifBlank { null }
                ?: prefs.getString("user_email", "") ?: "",
            brandName = authManager.session.value?.displayName?.ifBlank { null }
                ?: prefs.getString("brand_name", "") ?: "",
            brandVoicePrompt = prefs.getString("brand_voice_prompt", "") ?: "",
        ),
    )
    val userProfile: StateFlow<UserProfileState> = _userProfile.asStateFlow()

    val userId: String get() = authManager.session.value?.userId ?: _userProfile.value.userId

    init {
        viewModelScope.launch {
            authManager.refreshSessionIfNeeded()
        }
        // Silent Render warm-up ping on app launch so the backend is awake before the user records.
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { api.healthCheck() }
        }
    }

    fun signInWithEmail(
        email: String,
        password: String,
        onComplete: (Result<AuthSession>) -> Unit,
    ) {
        viewModelScope.launch {
            val res = authManager.signInWithEmail(email, password)
            res.onSuccess { session -> applyAuthSession(session) }
            onComplete(res)
        }
    }

    fun signUpWithEmail(
        email: String,
        password: String,
        brandName: String,
        brandVoice: String = "",
        onComplete: (Result<AuthSession>) -> Unit,
    ) {
        viewModelScope.launch {
            val res = authManager.signUpWithEmail(email, password, brandName, brandVoice)
            res.onSuccess { session ->
                applyAuthSession(session, brandNameOverride = brandName, brandVoiceOverride = brandVoice)
                saveUserProfile(session.email, brandName.ifBlank { session.displayName }, brandVoice) {}
            }
            onComplete(res)
        }
    }

    fun resendSignupEmail(email: String, onComplete: (Result<Unit>) -> Unit) {
        viewModelScope.launch {
            onComplete(authManager.resendSignupEmail(email))
        }
    }

    fun handleOAuthCallback(uri: Uri, onComplete: (Result<AuthSession>) -> Unit = {}) {
        viewModelScope.launch {
            val res = authManager.handleOAuthCallback(uri)
            res.onSuccess { session ->
                applyAuthSession(session)
                saveUserProfile(
                    email = session.email,
                    brandName = session.displayName.ifBlank { session.email.substringBefore("@") },
                    brandVoicePrompt = _userProfile.value.brandVoicePrompt,
                ) {}
            }
            onComplete(res)
        }
    }

    fun signOut() {
        authManager.signOut()
        val guestId = UUID.randomUUID().toString()
        prefs.edit()
            .putString("user_id", guestId)
            .remove("user_email")
            .remove("brand_name")
            .remove("brand_voice_prompt")
            .apply()
        _userProfile.value = UserProfileState(userId = guestId)
        val queueDir = File(getApplication<Application>().filesDir, "voice_queue")
        viewModelScope.launch(Dispatchers.IO) {
            offlineQueue.clearAll(queueDir)
            dao.clearAllContainers()
        }
    }

    private fun applyAuthSession(
        session: AuthSession,
        brandNameOverride: String = "",
        brandVoiceOverride: String = "",
    ) {
        val resolvedName = brandNameOverride.trim()
            .ifBlank { session.displayName }
            .ifBlank { _userProfile.value.brandName }
            .ifBlank { session.email.substringBefore("@") }
        val resolvedVoice = brandVoiceOverride.trim().ifBlank { _userProfile.value.brandVoicePrompt }
        prefs.edit()
            .putString("user_id", session.userId)
            .putString("user_email", session.email)
            .putString("brand_name", resolvedName)
            .putString("brand_voice_prompt", resolvedVoice)
            .putBoolean("onboarding_completed", true)
            .apply()
        _userProfile.value = UserProfileState(
            userId = session.userId,
            email = session.email,
            brandName = resolvedName,
            brandVoicePrompt = resolvedVoice,
        )
        syncContainersFromCloud(session.userId)
    }

    private fun syncContainersFromCloud(uid: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val remote = api.listContainers(uid)
                remote.forEach { idea ->
                    dao.upsertContainer(idea.toEntity())
                }
            } catch (e: Exception) {
                Log.w(TAG, "Cloud container sync skipped/failed", e)
            }
        }
    }

    val hasCompletedOnboarding: Boolean
        get() = authManager.session.value != null

    fun completeOnboarding() {
        prefs.edit().putBoolean("onboarding_completed", true).apply()
    }

    fun saveProfileLocally(email: String, brandName: String, brandVoicePrompt: String) {
        val deviceUserId = _userProfile.value.userId
        val e = email.trim()
        val b = brandName.trim()
        val v = brandVoicePrompt.trim()
        prefs.edit()
            .putString("user_id", deviceUserId)
            .putString("user_email", e)
            .putString("brand_name", b)
            .putString("brand_voice_prompt", v)
            .putBoolean("onboarding_completed", true)
            .apply()
        _userProfile.value = UserProfileState(
            userId = deviceUserId,
            email = e,
            brandName = b,
            brandVoicePrompt = v,
        )
    }

    private val _recordingState = MutableStateFlow<RecordingState>(RecordingState.Idle)
    val recordingState: StateFlow<RecordingState> = _recordingState.asStateFlow()

    private val _amplitude = MutableStateFlow(0)
    val amplitude: StateFlow<Int> = _amplitude.asStateFlow()

    private val _syncState = MutableStateFlow<SyncState>(SyncState.Idle)
    val syncState: StateFlow<SyncState> = _syncState.asStateFlow()

    val networkLabel: StateFlow<String> = offlineQueue.networkLabel

    val pendingNotes: StateFlow<List<PendingVoiceNoteEntity>> = dao.getPendingVoiceNotes()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val recentIdeas: StateFlow<List<IdeaCard>> = dao.getRecentContainers()
        .map { list ->
            list.mapNotNull { e ->
                e.toIdeaContainer()?.let { IdeaCard(it, e.isPushedToDrafted, e.createdAt) }
            }
        }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private var currentFile: File? = null
    private var recordingStartedAt = 0L
    private var peakAmplitude = 0
    private var amplitudeJob: Job? = null
    private var timerJob: Job? = null
    private var processJob: Job? = null

    /** True while Recording, Processing or Error: drives the sheet without per-second ticks. */
    val sheetVisible: StateFlow<Boolean> = _recordingState
        .map { it is RecordingState.Recording || it is RecordingState.Processing || it is RecordingState.Error }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    fun observeIdea(id: String): Flow<IdeaCard?> = dao.getContainerById(id)
        .map { e -> e?.toIdeaContainer()?.let { IdeaCard(it, e.isPushedToDrafted, e.createdAt) } }
        .flowOn(Dispatchers.Default)

    // ---- Recording --------------------------------------------------------

    fun startRecording() {
        val s = _recordingState.value
        if (s is RecordingState.Recording || s is RecordingState.Processing) return

        val queueDir = File(getApplication<Application>().filesDir, "voice_queue").apply { mkdirs() }
        val file = File(queueDir, "memo_${System.currentTimeMillis()}.m4a")
        try {
            recorder.start(file)
        } catch (e: Exception) {
            _recordingState.value = RecordingState.Error("Couldn't access the microphone.")
            return
        }
        currentFile = file
        recordingStartedAt = SystemClock.elapsedRealtime()
        peakAmplitude = 0
        _recordingState.value = RecordingState.Recording(0)
        // Dedicated coroutine for amplitude: it only touches `_amplitude`, which only the waveform collects.
        amplitudeJob = viewModelScope.launch {
            while (isActive) {
                val amp = recorder.getMaxAmplitude()
                _amplitude.value = amp
                if (amp > peakAmplitude) peakAmplitude = amp
                delay(80)
            }
        }
        // Separate coroutine for the elapsed-seconds timer (emits at most once per second).
        timerJob = viewModelScope.launch {
            while (isActive) {
                val secs = ((SystemClock.elapsedRealtime() - recordingStartedAt) / 1000).toInt()
                _recordingState.value = RecordingState.Recording(secs)
                delay(250)
            }
        }
    }

    private fun stopPolling() {
        amplitudeJob?.cancel()
        timerJob?.cancel()
        amplitudeJob = null
        timerJob = null
        _amplitude.value = 0
    }

    /** Stops recording and discards the audio. */
    fun cancelRecording() {
        stopPolling()
        recorder.stop()
        currentFile?.delete()
        currentFile = null
        _recordingState.value = RecordingState.Idle
    }

    fun stopRecordingAndProcess(userId: String) {
        if (_recordingState.value !is RecordingState.Recording) return
        stopPolling()
        val finalAmp = recorder.getMaxAmplitude()
        if (finalAmp > peakAmplitude) peakAmplitude = finalAmp
        val durationMs = SystemClock.elapsedRealtime() - recordingStartedAt
        val file = recorder.stop() // null if missing / <= 1 KB / stop() failed
        currentFile = null
        if (file == null || durationMs < 1_000) {
            file?.delete()
            _recordingState.value = RecordingState.Error("Recording was too short. Hold and speak your idea.")
            return
        }
        if (peakAmplitude < MIN_SPEECH_AMPLITUDE) {
            file.delete()
            _recordingState.value = RecordingState.Error("We couldn't hear any speech. Hold Drafty and speak clearly into your mic.")
            return
        }

        val timestamp = nowIso()
        _recordingState.value = RecordingState.Processing(ProcessingStep.TRANSCRIBING)
        processJob = viewModelScope.launch {
            // The backend does both stages in one request, so the second step is time-based.
            val stepper = launch {
                delay(1_500)
                if (_recordingState.value is RecordingState.Processing) {
                    _recordingState.value = RecordingState.Processing(ProcessingStep.STRUCTURING)
                }
            }
            var keepForOfflineQueue = false
            try {
                val result = api.transcribeAndParse(
                    NetworkClient.audioPart(file),
                    NetworkClient.textBody(userId),
                    NetworkClient.textBody(timestamp),
                )
                dao.upsertContainer(result.toEntity())
                _recordingState.value = RecordingState.Success(result.id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: HttpException) {
                Log.e(TAG, "transcribeAndParse HTTP ${e.code()}", e)
                _recordingState.value = RecordingState.Error(NetworkClient.describeError(e))
            } catch (e: IOException) {
                Log.w(TAG, "Network unavailable; queueing voice memo for auto-sync", e)
                keepForOfflineQueue = true
                offlineQueue.enqueue(
                    userId = userId,
                    audioFile = file,
                    clientTimestamp = timestamp,
                    reason = "Saved offline — will auto-draft when connected",
                )
                _recordingState.value = RecordingState.Error(
                    "You're offline right now. Your voice note is saved and will draft automatically when you reconnect.",
                )
            } catch (e: Exception) {
                Log.e(TAG, "transcribeAndParse failed", e)
                _recordingState.value = RecordingState.Error("Something went wrong while drafting your idea. Please try again.")
            } finally {
                stepper.cancel()
                if (!keepForOfflineQueue) {
                    file.delete()
                }
            }
        }
    }

    fun retryOfflineQueue() {
        viewModelScope.launch(Dispatchers.IO) {
            offlineQueue.flushQueue()
        }
    }

    fun saveUserProfile(
        email: String,
        brandName: String,
        brandVoicePrompt: String,
        onComplete: (Result<Unit>) -> Unit,
    ) {
        viewModelScope.launch {
            try {
                val deviceUserId = _userProfile.value.userId
                val resp = api.upsertUserProfile(
                    UserProfileRequest(
                        userId = deviceUserId,
                        email = email.trim(),
                        brandName = brandName.trim(),
                        brandVoicePrompt = brandVoicePrompt.trim(),
                    ),
                )
                prefs.edit()
                    .putString("user_id", deviceUserId)
                    .putString("user_email", resp.email)
                    .putString("brand_name", resp.brandName)
                    .putString("brand_voice_prompt", resp.brandVoicePrompt)
                    .putBoolean("onboarding_completed", true)
                    .apply()
                _userProfile.value = UserProfileState(
                    userId = deviceUserId,
                    email = resp.email,
                    brandName = resp.brandName,
                    brandVoicePrompt = resp.brandVoicePrompt,
                )
                viewModelScope.launch(Dispatchers.IO) {
                    offlineQueue.flushQueue()
                }
                onComplete(Result.success(Unit))
            } catch (e: Exception) {
                Log.e(TAG, "saveUserProfile failed", e)
                val msg = if (e is IOException) {
                    "You're offline. Please try saving your profile again when you're connected."
                } else {
                    "Couldn't save your profile right now. Please try again."
                }
                onComplete(Result.failure(RuntimeException(msg)))
            }
        }
    }

    fun discardPendingNote(note: PendingVoiceNoteEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            offlineQueue.discard(note)
        }
    }

    fun resetRecording() {
        _recordingState.value = RecordingState.Idle
    }

    // ---- Editing, deletion & sync -----------------------------------------

    fun updateContainer(idea: IdeaContainer) {
        viewModelScope.launch {
            val existing = dao.getContainerById(idea.id).first() ?: return@launch
            dao.upsertContainer(
                idea.toEntity(
                    createdAt = existing.createdAt,
                    isPushed = existing.isPushedToDrafted,
                    rawTranscript = existing.rawTranscript,
                ),
            )
            try {
                api.updateContainer(
                    idea.id,
                    ContainerUpdateRequest(
                        title = idea.title,
                        summary = idea.summary,
                        assets = idea.assets,
                        creativeDirection = idea.creativeDirection,
                        schedule = idea.schedule,
                    ),
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Background PATCH for container ${idea.id} failed (saved locally)", e)
            }
        }
    }

    fun regenerateHooks(idea: IdeaContainer, onResult: (Result<List<String>>) -> Unit) {
        viewModelScope.launch {
            try {
                val resp = api.regenerateHooks(
                    idea.id,
                    RegenerateHooksRequest(
                        title = idea.title,
                        summary = idea.summary,
                        primaryCaption = idea.assets.primaryCaption,
                        existingHooks = idea.assets.hookOptions,
                        brandVoice = _userProfile.value.brandVoicePrompt,
                    ),
                )
                val updated = idea.copy(assets = idea.assets.copy(hookOptions = resp.hookOptions))
                updateContainer(updated)
                onResult(Result.success(resp.hookOptions))
            } catch (e: CancellationException) {
                throw e
            } catch (e: HttpException) {
                onResult(Result.failure(RuntimeException(NetworkClient.describeError(e))))
            } catch (e: IOException) {
                onResult(Result.failure(RuntimeException("You're offline. Reconnect to generate fresh hooks.")))
            } catch (e: Exception) {
                onResult(Result.failure(RuntimeException("Couldn't generate new hooks right now. Please try again.")))
            }
        }
    }

    fun deleteContainer(containerId: String, onDeleted: () -> Unit) {
        viewModelScope.launch {
            dao.deleteContainerById(containerId)
            onDeleted()
            try {
                val resp = api.deleteContainer(containerId)
                if (!resp.isSuccessful && resp.code() != 404) {
                    Log.w(TAG, "Remote DELETE for container $containerId returned HTTP ${resp.code()}")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Background DELETE for container $containerId failed", e)
            }
        }
    }

    fun scheduleToDrafted(containerId: String, selectedHookIndex: Int = 0) {
        if (authSession.value == null) {
            _syncState.value = SyncState.Error("Sign in to your Drafted account to schedule ideas.")
            return
        }
        if (_syncState.value is SyncState.Syncing) return
        _syncState.value = SyncState.Syncing
        viewModelScope.launch {
            try {
                val localIdea = dao.getContainerById(containerId).first()?.toIdeaContainer()
                val syncBody = localIdea?.let {
                    DraftedSyncRequest(
                        title = it.title,
                        assets = it.assets,
                        schedule = it.schedule,
                        selectedHookIndex = selectedHookIndex,
                    )
                }
                val resp = api.scheduleToDrafted(containerId, syncBody)
                dao.markAsPushed(containerId)
                _syncState.value = SyncState.Synced(resp.scheduledFor)
            } catch (e: CancellationException) {
                throw e
            } catch (e: HttpException) {
                _syncState.value = SyncState.Error(NetworkClient.describeError(e))
            } catch (e: IOException) {
                _syncState.value = SyncState.Error("You're offline. Try scheduling again when you're connected.")
            } catch (e: Exception) {
                _syncState.value = SyncState.Error("Couldn't schedule your draft right now. Please try again.")
            }
        }
    }

    fun resetSync() {
        _syncState.value = SyncState.Idle
    }

    override fun onCleared() {
        stopPolling()
        recorder.stop()
        offlineQueue.close()
        super.onCleared()
    }

    private companion object {
        const val TAG = "DraftyViewModel"
        const val MIN_SPEECH_AMPLITUDE = 400
    }

    private fun nowIso(): String =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US).format(Date())
}
