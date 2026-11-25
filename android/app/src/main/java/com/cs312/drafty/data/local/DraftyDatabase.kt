package com.cs312.drafty.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [IdeaContainerEntity::class, PendingVoiceNoteEntity::class],
    version = 2,
    exportSchema = false,
)
abstract class DraftyDatabase : RoomDatabase() {
    abstract fun dao(): DraftyDao

    companion object {
        @Volatile private var instance: DraftyDatabase? = null

        fun get(context: Context): DraftyDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                DraftyDatabase::class.java,
                "drafty.db",
            ).fallbackToDestructiveMigration(true).build().also { instance = it }
        }
    }
}
