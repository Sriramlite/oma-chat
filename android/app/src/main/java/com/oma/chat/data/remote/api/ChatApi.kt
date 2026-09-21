package com.oma.chat.data.remote.api

import com.oma.chat.data.remote.dto.ChatConversationDto
import com.oma.chat.data.remote.dto.ChatMessageDto
import com.oma.chat.data.remote.dto.DeliverMessagesRequest
import com.oma.chat.data.remote.dto.ReadChatRequest
import com.oma.chat.data.remote.dto.SendMessageRequest
import com.oma.chat.data.remote.dto.SimpleMessageResponseDto
import com.oma.chat.data.remote.dto.UserSearchDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

interface ChatApi {

    @GET("chat/list")
    suspend fun getChatList(): Response<List<ChatConversationDto>>

    @GET("chat/history")
    suspend fun getChatHistory(
        @Query("chatId") chatId: String,
        @Query("since") since: Long? = null,
        @Query("type") type: String? = null
    ): Response<List<ChatMessageDto>>

    @POST("chat/send")
    suspend fun sendMessage(
        @Body request: SendMessageRequest
    ): Response<ChatMessageDto>

    @POST("chat/read")
    suspend fun markAsRead(
        @Body request: ReadChatRequest
    ): Response<SimpleMessageResponseDto>

    @POST("chat/deliver")
    suspend fun markAsDelivered(
        @Body request: DeliverMessagesRequest
    ): Response<SimpleMessageResponseDto>

    @GET("user/search")
    suspend fun searchUsers(
        @Query("q") query: String
    ): Response<List<UserSearchDto>>

    @POST("chat/actions")
    suspend fun performAction(
        @Body request: com.oma.chat.data.remote.dto.MessageActionRequest
    ): Response<SimpleMessageResponseDto>

    @POST("chat/delete_chat")
    suspend fun deleteChat(
        @Body request: com.oma.chat.data.remote.dto.DeleteChatRequest
    ): Response<SimpleMessageResponseDto>
}
