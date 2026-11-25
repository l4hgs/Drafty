package com.cs312.drafty.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Local offline queue entry (FR-02). Holds a recorded `.m4a` file on disk
 * when the device is offline or loses connectivity mid-upload, and retries
 * automatically once connectivity returns.
 */
@Entity(tableName = "pending_voice_notes")
data class PendingVoiceNoteEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val audioFilePath: String,
    val clientTimestamp: String,
    val status: String = STATUS_QUEUED,
    val retryCount: Int = 0,
    val lastError: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
) {
    companion object {
        const val STATUS_QUEUED = "QUEUED"
        const val STATUS_SYNCING = "SYNCING"
        const val STATUS_FAILED = "FAILED"
    }
}

