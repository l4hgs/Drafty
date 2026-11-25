package com.cs312.drafty.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "idea_containers")
data class IdeaContainerEntity(
    @PrimaryKey val id: String,
    val title: String,
    val summary: String,
    val rawTranscript: String,
    val metadataJson: String,
    val assetsJson: String,
    val creativeDirectionJson: String,
    val scheduleJson: String,
    val isPushedToDrafted: Boolean,
    val createdAt: Long,
)

