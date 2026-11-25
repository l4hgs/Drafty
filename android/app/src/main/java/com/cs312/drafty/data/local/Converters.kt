package com.cs312.drafty.data.local

import com.cs312.drafty.data.remote.Assets
import com.cs312.drafty.data.remote.ContentMetadata
import com.cs312.drafty.data.remote.CreativeDirection
import com.cs312.drafty.data.remote.IdeaContainer
import com.cs312.drafty.data.remote.NetworkClient
import com.cs312.drafty.data.remote.ScheduleInfo

/** Moshi-based JSON <-> model converters for the nested parts of an idea container. */
object Converters {
    private val moshi get() = NetworkClient.moshi

    fun metadataToJson(v: ContentMetadata): String = moshi.adapter(ContentMetadata::class.java).toJson(v)
    fun metadataFromJson(s: String): ContentMetadata? = moshi.adapter(ContentMetadata::class.java).fromJson(s)

    fun assetsToJson(v: Assets): String = moshi.adapter(Assets::class.java).toJson(v)
    fun assetsFromJson(s: String): Assets? = moshi.adapter(Assets::class.java).fromJson(s)

    fun creativeToJson(v: CreativeDirection): String = moshi.adapter(CreativeDirection::class.java).toJson(v)
    fun creativeFromJson(s: String): CreativeDirection? = moshi.adapter(CreativeDirection::class.java).fromJson(s)

    fun scheduleToJson(v: ScheduleInfo): String = moshi.adapter(ScheduleInfo::class.java).toJson(v)
    fun scheduleFromJson(s: String): ScheduleInfo? = moshi.adapter(ScheduleInfo::class.java).fromJson(s)
}

fun IdeaContainer.toEntity(
    createdAt: Long = System.currentTimeMillis(),
    isPushed: Boolean = false,
    rawTranscript: String = "",
) = IdeaContainerEntity(
    id = id,
    title = title,
    summary = summary,
    rawTranscript = rawTranscript,
    metadataJson = Converters.metadataToJson(metadata),
    assetsJson = Converters.assetsToJson(assets),
    creativeDirectionJson = Converters.creativeToJson(creativeDirection),
    scheduleJson = Converters.scheduleToJson(schedule),
    isPushedToDrafted = isPushed,
    createdAt = createdAt,
)

/** Returns null if any stored JSON blob is unreadable. */
fun IdeaContainerEntity.toIdeaContainer(): IdeaContainer? = try {
    IdeaContainer(
        id = id,
        title = title,
        summary = summary,
        metadata = Converters.metadataFromJson(metadataJson)!!,
        assets = Converters.assetsFromJson(assetsJson)!!,
        creativeDirection = Converters.creativeFromJson(creativeDirectionJson)!!,
        schedule = Converters.scheduleFromJson(scheduleJson)!!,
    )
} catch (e: Exception) {
    null
}

