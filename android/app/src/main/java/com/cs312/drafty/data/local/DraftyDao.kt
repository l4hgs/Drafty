package com.cs312.drafty.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DraftyDao {
    @Query("SELECT * FROM idea_containers ORDER BY createdAt DESC LIMIT 50")
    fun getRecentContainers(): Flow<List<IdeaContainerEntity>>

    @Query("SELECT * FROM idea_containers WHERE id = :id")
    fun getContainerById(id: String): Flow<IdeaContainerEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertContainer(entity: IdeaContainerEntity)

    @Query("UPDATE idea_containers SET isPushedToDrafted = 1 WHERE id = :id")
    suspend fun markAsPushed(id: String)

    @Query("DELETE FROM idea_containers WHERE id = :id")
    suspend fun deleteContainerById(id: String)

    @Query("DELETE FROM idea_containers")
    suspend fun clearAllContainers()

    // ---- Offline audio queue (FR-02) --------------------------------------

    @Query("SELECT * FROM pending_voice_notes ORDER BY createdAt ASC")
    fun getPendingVoiceNotes(): Flow<List<PendingVoiceNoteEntity>>

    @Query("SELECT * FROM pending_voice_notes ORDER BY createdAt ASC")
    suspend fun getPendingVoiceNotesSnapshot(): List<PendingVoiceNoteEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPendingVoiceNote(entity: PendingVoiceNoteEntity)

    @Query("DELETE FROM pending_voice_notes WHERE id = :id")
    suspend fun deletePendingVoiceNote(id: String)
}
