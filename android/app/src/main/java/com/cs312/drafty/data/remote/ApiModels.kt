package com.cs312.drafty.data.remote

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class IdeaContainer(
    val id: String,
    @param:Json(name = "draft_id") val draftId: String? = null,
    val title: String,
    val summary: String,
    val metadata: ContentMetadata,
    val assets: Assets,
    @param:Json(name = "creative_direction") val creativeDirection: CreativeDirection,
    val schedule: ScheduleInfo,
)

@JsonClass(generateAdapter = true)
data class ContentMetadata(
    @param:Json(name = "target_platforms") val targetPlatforms: List<String>,
    @param:Json(name = "content_pillar") val contentPillar: String,
    val tone: String,
)

@JsonClass(generateAdapter = true)
data class Assets(
    @param:Json(name = "hook_options") val hookOptions: List<String>,
    @param:Json(name = "primary_caption") val primaryCaption: String,
    val cta: String,
    val hashtags: List<String>,
)

@JsonClass(generateAdapter = true)
data class CreativeDirection(
    @param:Json(name = "visual_type") val visualType: String,
    @param:Json(name = "shot_list") val shotList: List<ShotItem>,
    @param:Json(name = "text_overlays") val textOverlays: List<String>,
)

@JsonClass(generateAdapter = true)
data class ShotItem(
    val timestamp: String,
    val description: String,
)

@JsonClass(generateAdapter = true)
data class ScheduleInfo(
    @param:Json(name = "recommended_day") val recommendedDay: String,
    @param:Json(name = "recommended_date") val recommendedDate: String,
    @param:Json(name = "recommended_time") val recommendedTime: String,
    @param:Json(name = "peak_window_reason") val peakWindowReason: String,
)

@JsonClass(generateAdapter = true)
data class DraftedSyncRequest(
    val title: String? = null,
    val assets: Assets? = null,
    val schedule: ScheduleInfo? = null,
)

@JsonClass(generateAdapter = true)
data class ContainerUpdateRequest(
    val title: String? = null,
    val summary: String? = null,
    val assets: Assets? = null,
    @param:Json(name = "creative_direction") val creativeDirection: CreativeDirection? = null,
    val schedule: ScheduleInfo? = null,
)

@JsonClass(generateAdapter = true)
data class ContainerDeleteResponse(
    @param:Json(name = "container_id") val containerId: String,
    val deleted: Boolean = true,
)

@JsonClass(generateAdapter = true)
data class DraftedSyncResponse(
    @param:Json(name = "container_id") val containerId: String,
    @param:Json(name = "scheduled_for") val scheduledFor: String?,
    @param:Json(name = "sync_status") val syncStatus: String,
)

@JsonClass(generateAdapter = true)
data class UserProfileRequest(
    @param:Json(name = "user_id") val userId: String? = null,
    val email: String,
    @param:Json(name = "brand_name") val brandName: String,
    @param:Json(name = "brand_voice_prompt") val brandVoicePrompt: String = "",
)

@JsonClass(generateAdapter = true)
data class UserProfileResponse(
    val id: String,
    val email: String,
    @param:Json(name = "brand_name") val brandName: String,
    @param:Json(name = "brand_voice_prompt") val brandVoicePrompt: String,
)

@JsonClass(generateAdapter = true)
data class RegenerateHooksRequest(
    val title: String,
    val summary: String = "",
    @param:Json(name = "primary_caption") val primaryCaption: String = "",
    @param:Json(name = "existing_hooks") val existingHooks: List<String> = emptyList(),
    @param:Json(name = "brand_voice") val brandVoice: String = "",
)

@JsonClass(generateAdapter = true)
data class RegenerateHooksResponse(
    @param:Json(name = "hook_options") val hookOptions: List<String>,
)

/** Flat backend error body: {"detail": "...", "code": "..."} */
@JsonClass(generateAdapter = true)
data class ApiError(
    val detail: String,
    val code: String,
)

