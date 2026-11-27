package com.cs312.drafty.data.remote

import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query

interface DraftyApiService {

    @GET("health")
    suspend fun healthCheck(): Response<Unit>

    @GET("api/v1/containers")
    suspend fun listContainers(
        @Query("user_id") userId: String,
    ): List<IdeaContainer>

    @Multipart
    @POST("api/v1/voice/transcribe-and-parse")
    suspend fun transcribeAndParse(
        @Part file: MultipartBody.Part,
        @Part("user_id") userId: RequestBody,
        @Part("client_timestamp") clientTimestamp: RequestBody,
    ): IdeaContainer

    @PATCH("api/v1/containers/{container_id}")
    suspend fun updateContainer(
        @Path("container_id") containerId: String,
        @Body request: ContainerUpdateRequest,
    ): IdeaContainer

    @DELETE("api/v1/containers/{container_id}")
    suspend fun deleteContainer(
        @Path("container_id") containerId: String,
    ): Response<ContainerDeleteResponse>

    @POST("api/v1/containers/{container_id}/schedule-to-drafted")
    suspend fun scheduleToDrafted(
        @Path("container_id") containerId: String,
        @Body request: DraftedSyncRequest? = null,
    ): DraftedSyncResponse

    @POST("api/v1/containers/{container_id}/regenerate-hooks")
    suspend fun regenerateHooks(
        @Path("container_id") containerId: String,
        @Body request: RegenerateHooksRequest,
    ): RegenerateHooksResponse

    @POST("api/v1/users/profile")
    suspend fun upsertUserProfile(
        @Body request: UserProfileRequest,
    ): UserProfileResponse
}

