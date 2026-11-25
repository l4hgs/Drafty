package com.cs312.drafty.core.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.util.Log
import com.cs312.drafty.data.local.DraftyDao
import com.cs312.drafty.data.local.PendingVoiceNoteEntity
import com.cs312.drafty.data.local.toEntity
import com.cs312.drafty.data.remote.DraftyApiService
import com.cs312.drafty.data.remote.NetworkClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import retrofit2.HttpException
import java.io.File
import java.io.IOException
import java.util.UUID

/**
 * Offline queue manager & network state monitor (FR-02 & Week 2/3).
 *
 * - Tracks live network link state (`Wi-Fi`, `Cellular`, or `Offline`).
 * - Stores unuploaded `.m4a` payloads in Room when the device is offline or
 *   experiences a mid-upload network drop.
 * - Automatically flushes the queue when connectivity returns.
 */
class OfflineQueueManager(
    context: Context,
    private val dao: DraftyDao,
    private val api: DraftyApiService,
    private val scope: CoroutineScope,
) {
    private val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val flushMutex = Mutex()

    private val _networkLabel = MutableStateFlow(readCurrentNetworkLabel())
    val networkLabel: StateFlow<String> = _networkLabel.asStateFlow()

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            _networkLabel.value = readCurrentNetworkLabel()
            scope.launch(Dispatchers.IO) { flushQueue() }
        }

        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
            _networkLabel.value = labelFor(caps)
            if (caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
                scope.launch(Dispatchers.IO) { flushQueue() }
            }
        }

        override fun onLost(network: Network) {
            _networkLabel.value = readCurrentNetworkLabel()
        }
    }

    init {
        try {
            cm.registerDefaultNetworkCallback(callback)
        } catch (e: Exception) {
            Log.w(TAG, "Could not register network callback", e)
        }
        // Flush any leftovers from a previous session on startup
        scope.launch(Dispatchers.IO) { flushQueue() }
    }

    suspend fun enqueue(
        userId: String,
        audioFile: File,
        clientTimestamp: String,
        reason: String = "Waiting for network connection",
    ): PendingVoiceNoteEntity {
        val note = PendingVoiceNoteEntity(
            id = UUID.randomUUID().toString(),
            userId = userId,
            audioFilePath = audioFile.absolutePath,
            clientTimestamp = clientTimestamp,
            status = PendingVoiceNoteEntity.STATUS_QUEUED,
            retryCount = 0,
            lastError = reason,
        )
        dao.upsertPendingVoiceNote(note)
        Log.i(TAG, "Queued offline voice note id=${note.id} file=${audioFile.name}")
        return note
    }

    suspend fun discard(note: PendingVoiceNoteEntity) {
        runCatching { File(note.audioFilePath).delete() }
        dao.deletePendingVoiceNote(note.id)
    }

    /** Attempts to upload and parse all queued voice notes in FIFO order. */
    suspend fun flushQueue() {
        if (!flushMutex.tryLock()) return
        try {
            val pending = dao.getPendingVoiceNotesSnapshot()
            for (note in pending) {
                val file = File(note.audioFilePath)
                if (!file.exists() || file.length() <= 1024L) {
                    dao.deletePendingVoiceNote(note.id)
                    continue
                }
                dao.upsertPendingVoiceNote(
                    note.copy(status = PendingVoiceNoteEntity.STATUS_SYNCING, lastError = null),
                )
                try {
                    val result = api.transcribeAndParse(
                        NetworkClient.audioPart(file),
                        NetworkClient.textBody(note.userId),
                        NetworkClient.textBody(note.clientTimestamp),
                    )
                    dao.upsertContainer(result.toEntity())
                    dao.deletePendingVoiceNote(note.id)
                    file.delete()
                    Log.i(TAG, "Synced queued voice note ${note.id} -> container ${result.id}")
                } catch (e: CancellationException) {
                    throw e
                } catch (e: IOException) {
                    dao.upsertPendingVoiceNote(
                        note.copy(
                            status = PendingVoiceNoteEntity.STATUS_QUEUED,
                            retryCount = note.retryCount + 1,
                            lastError = "Waiting for server connection…",
                        ),
                    )
                    break // Still offline; wait for next connectivity callback or manual retry
                } catch (e: HttpException) {
                    val msg = NetworkClient.parseError(e)?.detail ?: "Server error (${e.code()})"
                    if (e.code() in 400..499) {
                        // Permanent 4xx error (e.g. 422 NO_SPEECH on a silent clip): discard so it doesn't loop forever.
                        Log.w(TAG, "Discarding queued note ${note.id} after permanent HTTP ${e.code()}: $msg")
                        dao.deletePendingVoiceNote(note.id)
                        file.delete()
                    } else {
                        dao.upsertPendingVoiceNote(
                            note.copy(
                                status = PendingVoiceNoteEntity.STATUS_FAILED,
                                retryCount = note.retryCount + 1,
                                lastError = msg,
                            ),
                        )
                    }
                } catch (e: Exception) {
                    dao.upsertPendingVoiceNote(
                        note.copy(
                            status = PendingVoiceNoteEntity.STATUS_FAILED,
                            retryCount = note.retryCount + 1,
                            lastError = e.message ?: "Failed to process audio",
                        ),
                    )
                }
            }
        } finally {
            flushMutex.unlock()
        }
    }

    fun close() {
        runCatching { cm.unregisterNetworkCallback(callback) }
    }

    private fun readCurrentNetworkLabel(): String {
        val active = cm.activeNetwork ?: return "Offline"
        val caps = cm.getNetworkCapabilities(active) ?: return "Offline"
        return labelFor(caps)
    }

    private fun labelFor(caps: NetworkCapabilities): String = when {
        !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) -> "Offline"
        caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
        caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Cellular"
        else -> "Online"
    }

    private companion object {
        const val TAG = "OfflineQueueManager"
    }
}

