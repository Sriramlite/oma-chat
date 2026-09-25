package com.oma.chat.data.remote.api

import com.oma.chat.data.remote.dto.SimpleMessageResponseDto
import com.oma.chat.data.remote.dto.UserDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

data class UpdateProfileRequest(
    val name: String? = null,
    val bio: String? = null,
    val avatar: String? = null,
    val phone: String? = null,
    val battery: Any? = null,
    val settings: Map<String, Any?>? = null
)

data class BatchUsersRequest(
    val ids: List<String>
)

data class PushTokenRequest(
    val token: String
)

data class BlockUserRequest(
    val userId: String,
    val action: String // "block" or "unblock"
)

data class ReportUserRequest(
    val targetedUserId: String,
    val reason: String
)

interface UserApi {

    @GET("user/me")
    suspend fun getMe(
        @Query("_t") timestamp: Long = System.currentTimeMillis()
    ): Response<UserDto>

    @POST("user/update")
    suspend fun updateProfile(
        @Body request: UpdateProfileRequest
    ): Response<UserDto>

    @GET("user/search")
    suspend fun searchUsers(
        @Query("q") query: String
    ): Response<List<UserDto>>

    @POST("users/batch")
    suspend fun batchGetUsers(
        @Body request: BatchUsersRequest
    ): Response<List<UserDto>>

    @POST("user/push-token")
    suspend fun updatePushToken(
        @Body request: PushTokenRequest
    ): Response<SimpleMessageResponseDto>

    @POST("user/block")
    suspend fun blockUser(
        @Body request: BlockUserRequest
    ): Response<SimpleMessageResponseDto>

    @POST("user/report")
    suspend fun reportUser(
        @Body request: ReportUserRequest
    ): Response<SimpleMessageResponseDto>
}
