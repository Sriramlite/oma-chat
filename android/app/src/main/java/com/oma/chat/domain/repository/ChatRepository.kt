package com.oma.chat.domain.repository

import com.oma.chat.data.remote.socket.TypingEvent
import com.oma.chat.domain.model.Conversation
import com.oma.chat.domain.model.Message
import com.oma.chat.domain.model.User
import kotlinx.coroutines.flow.Flow

interface ChatRepository {
    fun getRecentConversations(ownerUserId: String): Flow<List<Conversation>>
    suspend fun refreshRecentConversations(ownerUserId: String): Result<Unit>
    fun getMessages(ownerUserId: String, chatId: String): Flow<List<Message>>
    suspend fun fetchChatHistory(ownerUserId: String, chatId: String, since: Long? = null): Result<List<Message>>
    suspend fun sendMessage(
        ownerUserId: String,
        receiverId: String,
        content: String,
        type: String = "text",
        replyToId: String? = null
    ): Result<Message>
    suspend fun markChatAsRead(ownerUserId: String, chatId: String): Result<Unit>
    suspend fun searchUsers(query: String): Result<List<User>>
    suspend fun editMessage(ownerUserId: String, messageId: String, newContent: String): Result<Unit>
    suspend fun deleteMessage(ownerUserId: String, messageId: String, mode: String): Result<Unit>
    suspend fun starMessage(ownerUserId: String, messageId: String, isStarred: Boolean): Result<Unit>
    suspend fun pinMessage(ownerUserId: String, messageId: String, isPinned: Boolean): Result<Unit>
    suspend fun deleteChat(ownerUserId: String, chatId: String): Result<Unit>
    fun observeIncomingMessages(ownerUserId: String): Flow<Message>
    fun observeOnlineUsers(): Flow<Set<String>>
    fun observeTypingEvents(): Flow<TypingEvent>
    fun sendTyping(receiverId: String)
    fun sendStopTyping(receiverId: String)
    fun connectSocket()
    fun disconnectSocket()
}
