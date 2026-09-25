package com.oma.chat.data.repository

import com.oma.chat.data.local.dao.ConversationDao
import com.oma.chat.data.local.dao.MessageDao
import com.oma.chat.data.local.entity.ConversationEntity
import com.oma.chat.data.local.entity.MessageEntity
import com.oma.chat.data.local.preferences.AuthPreferences
import com.oma.chat.data.remote.api.ChatApi
import com.oma.chat.data.remote.dto.ReadChatRequest
import com.oma.chat.data.remote.dto.SendMessageRequest
import com.oma.chat.data.remote.socket.SocketManager
import com.oma.chat.data.remote.socket.TypingEvent
import com.oma.chat.di.IoDispatcher
import com.oma.chat.domain.model.Conversation
import com.oma.chat.domain.model.Message
import com.oma.chat.domain.model.MessageStatus
import com.oma.chat.domain.model.User
import com.oma.chat.domain.repository.ChatRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatRepositoryImpl @Inject constructor(
    private val chatApi: ChatApi,
    private val conversationDao: ConversationDao,
    private val messageDao: MessageDao,
    private val authPreferences: AuthPreferences,
    private val socketManager: SocketManager,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : ChatRepository {

    private val repositoryScope = CoroutineScope(ioDispatcher)

    init {
        // Automatically start listening for Socket.IO incoming messages and persist them to Room
        repositoryScope.launch {
            socketManager.incomingMessages.collect { msgDto ->
                val ownerUserId = authPreferences.getUserId() ?: return@collect
                val domainMsg = msgDto.toDomain(ownerUserId)
                val effectiveChatId = if (domainMsg.senderId == ownerUserId) {
                    msgDto.receiverId ?: domainMsg.chatId
                } else {
                    domainMsg.senderId
                }

                val entity = MessageEntity(
                    ownerUserId = ownerUserId,
                    id = domainMsg.id,
                    tempId = domainMsg.tempId,
                    chatId = effectiveChatId,
                    senderId = domainMsg.senderId,
                    senderName = domainMsg.senderName,
                    avatar = domainMsg.avatar,
                    content = domainMsg.content,
                    type = domainMsg.type.value,
                    timestamp = domainMsg.timestamp,
                    status = domainMsg.status.name.lowercase(),
                    replyToId = domainMsg.replyToId,
                    isStarred = domainMsg.isStarred,
                    isPinned = domainMsg.isPinned,
                    isDeleted = domainMsg.isDeleted,
                    isEdited = domainMsg.isEdited
                )
                messageDao.reconcileServerMessage(ownerUserId, entity)

                // Update or create conversation preview
                val existingConv = conversationDao.getConversationById(ownerUserId, effectiveChatId)
                val convEntity = ConversationEntity(
                    ownerUserId = ownerUserId,
                    id = effectiveChatId,
                    name = existingConv?.name ?: domainMsg.senderName,
                    username = existingConv?.username,
                    avatar = existingConv?.avatar ?: domainMsg.avatar,
                    lastMsg = domainMsg.content,
                    timestamp = domainMsg.timestamp,
                    type = existingConv?.type ?: "user",
                    unreadCount = if (domainMsg.senderId != ownerUserId) (existingConv?.unreadCount ?: 0) + 1 else 0,
                    isOnline = existingConv?.isOnline ?: false,
                    lastSeen = existingConv?.lastSeen ?: 0L
                )
                conversationDao.insertConversation(convEntity)
            }
        }

        // Listen for user presence updates and reflect them in Room
        repositoryScope.launch {
            socketManager.userStatusEvents.collect { statusEvent ->
                val ownerUserId = authPreferences.getUserId() ?: return@collect
                conversationDao.updatePresence(
                    ownerUserId = ownerUserId,
                    id = statusEvent.userId,
                    isOnline = statusEvent.online,
                    lastSeen = statusEvent.lastSeen
                )
            }
        }

        // Listen for real-time Message Edit events
        repositoryScope.launch {
            socketManager.messageEditedEvents.collect { event ->
                val ownerUserId = authPreferences.getUserId() ?: return@collect
                messageDao.updateMessageContent(
                    ownerUserId = ownerUserId,
                    id = event.messageId,
                    content = event.newContent,
                    isEdited = event.isEdited
                )
            }
        }

        // Listen for real-time Message Delete events
        repositoryScope.launch {
            socketManager.messageDeletedEvents.collect { event ->
                val ownerUserId = authPreferences.getUserId() ?: return@collect
                if (event.mode == "everyone") {
                    messageDao.deleteMessageForEveryone(
                        ownerUserId = ownerUserId,
                        id = event.messageId,
                        placeholder = event.content
                    )
                } else if (event.deletedFor == ownerUserId) {
                    messageDao.deleteMessage(
                        ownerUserId = ownerUserId,
                        id = event.messageId
                    )
                }
            }
        }

        // Listen for real-time Chat Delete events
        repositoryScope.launch {
            socketManager.chatDeletedEvents.collect { event ->
                val ownerUserId = authPreferences.getUserId() ?: return@collect
                messageDao.deleteMessagesForChat(ownerUserId, event.chatId)
                conversationDao.deleteConversation(ownerUserId, event.chatId)
            }
        }

        // Listen for real-time Star events
        repositoryScope.launch {
            socketManager.messageStarredEvents.collect { event ->
                val ownerUserId = authPreferences.getUserId() ?: return@collect
                if (event.userId == ownerUserId) {
                    messageDao.updateMessageStarred(ownerUserId, event.messageId, event.isStarred)
                }
            }
        }

        // Listen for real-time Pin events
        repositoryScope.launch {
            socketManager.messagePinnedEvents.collect { event ->
                val ownerUserId = authPreferences.getUserId() ?: return@collect
                messageDao.updateMessagePinned(ownerUserId, event.messageId, event.isPinned)
            }
        }

        // Listen for real-time Read Receipts (messages_seen)
        repositoryScope.launch {
            socketManager.messagesSeenEvents.collect { event ->
                val ownerUserId = authPreferences.getUserId() ?: return@collect
                messageDao.markOutgoingMessagesAsSeen(ownerUserId, event.readerId)
            }
        }
    }

    override fun getRecentConversations(ownerUserId: String): Flow<List<Conversation>> {
        return conversationDao.getConversations(ownerUserId)
            .map { entities ->
                entities.map { entity ->
                    Conversation(
                        id = entity.id,
                        name = entity.name,
                        username = entity.username,
                        avatar = entity.avatar,
                        lastMsg = entity.lastMsg,
                        timestamp = entity.timestamp,
                        type = entity.type,
                        unreadCount = entity.unreadCount,
                        isOnline = entity.isOnline,
                        lastSeen = entity.lastSeen
                    )
                }
            }
    }

    override suspend fun refreshRecentConversations(ownerUserId: String): Result<Unit> = withContext(ioDispatcher) {
        try {
            val response = chatApi.getChatList()
            if (response.isSuccessful && response.body() != null) {
                val dtos = response.body()!!
                val entities = dtos.map { dto ->
                    val domain = dto.toDomain(ownerUserId)
                    ConversationEntity(
                        ownerUserId = ownerUserId,
                        id = domain.id,
                        name = domain.name,
                        username = domain.username,
                        avatar = domain.avatar,
                        lastMsg = domain.lastMsg,
                        timestamp = domain.timestamp,
                        type = domain.type,
                        unreadCount = domain.unreadCount,
                        isOnline = domain.isOnline,
                        lastSeen = domain.lastSeen
                    )
                }
                conversationDao.insertConversations(entities)
                Result.success(Unit)
            } else {
                Result.failure(Exception("Failed to fetch recent chats: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun getMessages(ownerUserId: String, chatId: String): Flow<List<Message>> {
        return messageDao.getMessagesForChat(ownerUserId, chatId)
            .map { entities ->
                entities.map { entity ->
                    Message(
                        id = entity.id,
                        tempId = entity.tempId,
                        chatId = entity.chatId,
                        senderId = entity.senderId,
                        senderName = entity.senderName,
                        avatar = entity.avatar,
                        content = entity.content,
                        type = com.oma.chat.domain.model.MessageType.fromValue(entity.type),
                        timestamp = entity.timestamp,
                        status = try { MessageStatus.valueOf(entity.status.uppercase()) } catch (e: Exception) { MessageStatus.SENT },
                        replyToId = entity.replyToId,
                        isStarred = entity.isStarred,
                        isPinned = entity.isPinned,
                        isDeleted = entity.isDeleted,
                        isEdited = entity.isEdited
                    )
                }
            }
    }

    override suspend fun fetchChatHistory(
        ownerUserId: String,
        chatId: String,
        since: Long?
    ): Result<List<Message>> = withContext(ioDispatcher) {
        try {
            val response = chatApi.getChatHistory(chatId = chatId, since = since)
            if (response.isSuccessful && response.body() != null) {
                val dtos = response.body()!!
                val domainMessages = dtos.map { it.toDomain(ownerUserId, chatId) }
                val entities = domainMessages.map { msg ->
                    MessageEntity(
                        ownerUserId = ownerUserId,
                        id = msg.id,
                        tempId = msg.tempId,
                        chatId = chatId,
                        senderId = msg.senderId,
                        senderName = msg.senderName,
                        avatar = msg.avatar,
                        content = msg.content,
                        type = msg.type.value,
                        timestamp = msg.timestamp,
                        status = msg.status.name.lowercase(),
                        replyToId = msg.replyToId,
                        isStarred = msg.isStarred,
                        isPinned = msg.isPinned,
                        isDeleted = msg.isDeleted,
                        isEdited = msg.isEdited
                    )
                }
                messageDao.reconcileServerMessages(ownerUserId, entities)
                Result.success(domainMessages)
            } else {
                Result.failure(Exception("Failed to fetch chat history: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun sendMessage(
        ownerUserId: String,
        receiverId: String,
        content: String,
        type: String,
        replyToId: String?
    ): Result<Message> = withContext(ioDispatcher) {
        val tempId = UUID.randomUUID().toString()
        val timestamp = System.currentTimeMillis()
        val currentUser = authPreferences.getUser()

        // 1. Optimistic Local Insert with tempId
        val pendingEntity = MessageEntity(
            ownerUserId = ownerUserId,
            id = tempId,
            tempId = tempId,
            chatId = receiverId,
            senderId = ownerUserId,
            senderName = currentUser?.name ?: "You",
            avatar = currentUser?.avatar ?: "",
            content = content,
            type = type,
            timestamp = timestamp,
            status = "sending",
            replyToId = replyToId
        )
        messageDao.insertMessage(pendingEntity)

        // Optimistically update conversation preview
        val existingConv = conversationDao.getConversationById(ownerUserId, receiverId)
        val convEntity = ConversationEntity(
            ownerUserId = ownerUserId,
            id = receiverId,
            name = existingConv?.name ?: "Chat",
            username = existingConv?.username,
            avatar = existingConv?.avatar ?: "",
            lastMsg = content,
            timestamp = timestamp,
            type = existingConv?.type ?: "user",
            unreadCount = 0,
            isOnline = existingConv?.isOnline ?: false,
            lastSeen = existingConv?.lastSeen ?: 0L
        )
        conversationDao.insertConversation(convEntity)

        try {
            // 2. Network Dispatch
            val request = SendMessageRequest(
                content = content,
                type = type,
                receiverId = receiverId,
                replyToId = replyToId,
                tempId = tempId
            )
            val response = chatApi.sendMessage(request)

            if (response.isSuccessful && response.body() != null) {
                val body = response.body()!!
                val serverMsg = body.toDomain(ownerUserId, receiverId)

                // 3. Reconcile temporary entity with confirmed server entity
                val confirmedEntity = MessageEntity(
                    ownerUserId = ownerUserId,
                    id = serverMsg.id,
                    tempId = tempId,
                    chatId = receiverId,
                    senderId = ownerUserId,
                    senderName = currentUser?.name ?: "You",
                    avatar = currentUser?.avatar ?: "",
                    content = content,
                    type = type,
                    timestamp = serverMsg.timestamp,
                    status = "sent",
                    replyToId = replyToId
                )
                messageDao.reconcileServerMessage(ownerUserId, confirmedEntity)
                Result.success(serverMsg)
            } else {
                messageDao.updateMessageStatus(ownerUserId, tempId, "failed")
                Result.failure(Exception("Send message failed with code: ${response.code()}"))
            }
        } catch (e: Exception) {
            messageDao.updateMessageStatus(ownerUserId, tempId, "failed")
            Result.failure(e)
        }
    }

    override suspend fun markChatAsRead(ownerUserId: String, chatId: String): Result<Unit> = withContext(ioDispatcher) {
        try {
            conversationDao.clearUnreadCount(ownerUserId, chatId)
            socketManager.emitMarkSeen(chatId)
            val response = chatApi.markAsRead(ReadChatRequest(chatId))
            if (response.isSuccessful) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("Mark read failed: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun searchUsers(query: String): Result<List<User>> = withContext(ioDispatcher) {
        try {
            val response = chatApi.searchUsers(query.trim())
            if (response.isSuccessful && response.body() != null) {
                val users = response.body()!!.map { it.toDomain() }
                Result.success(users)
            } else {
                Result.failure(Exception("User search failed: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun editMessage(
        ownerUserId: String,
        messageId: String,
        newContent: String
    ): Result<Unit> = withContext(ioDispatcher) {
        try {
            messageDao.updateMessageContent(ownerUserId, messageId, newContent, isEdited = true)
            val response = chatApi.performAction(
                com.oma.chat.data.remote.dto.MessageActionRequest(
                    action = "edit",
                    messageId = messageId,
                    newContent = newContent
                )
            )
            if (response.isSuccessful) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("Edit failed: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun deleteMessage(
        ownerUserId: String,
        messageId: String,
        mode: String
    ): Result<Unit> = withContext(ioDispatcher) {
        try {
            if (mode == "everyone") {
                messageDao.deleteMessageForEveryone(ownerUserId, messageId)
            } else {
                messageDao.deleteMessage(ownerUserId, messageId)
            }
            val response = chatApi.performAction(
                com.oma.chat.data.remote.dto.MessageActionRequest(
                    action = "delete",
                    messageId = messageId,
                    mode = mode
                )
            )
            if (response.isSuccessful) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("Delete failed: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun starMessage(
        ownerUserId: String,
        messageId: String,
        isStarred: Boolean
    ): Result<Unit> = withContext(ioDispatcher) {
        try {
            messageDao.updateMessageStarred(ownerUserId, messageId, isStarred)
            val response = chatApi.performAction(
                com.oma.chat.data.remote.dto.MessageActionRequest(
                    action = "star",
                    messageId = messageId
                )
            )
            if (response.isSuccessful) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("Star action failed: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun pinMessage(
        ownerUserId: String,
        messageId: String,
        isPinned: Boolean
    ): Result<Unit> = withContext(ioDispatcher) {
        try {
            messageDao.updateMessagePinned(ownerUserId, messageId, isPinned)
            val response = chatApi.performAction(
                com.oma.chat.data.remote.dto.MessageActionRequest(
                    action = "pin",
                    messageId = messageId
                )
            )
            if (response.isSuccessful) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("Pin action failed: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun deleteChat(
        ownerUserId: String,
        chatId: String
    ): Result<Unit> = withContext(ioDispatcher) {
        try {
            conversationDao.deleteConversation(ownerUserId, chatId)
            messageDao.deleteMessagesForChat(ownerUserId, chatId)
            val response = chatApi.deleteChat(com.oma.chat.data.remote.dto.DeleteChatRequest(chatId = chatId))
            if (response.isSuccessful) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("Delete chat failed: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun observeIncomingMessages(ownerUserId: String): Flow<Message> {
        return socketManager.incomingMessages.map { it.toDomain(ownerUserId) }
    }

    override fun observeOnlineUsers(): Flow<Set<String>> {
        return socketManager.onlineUsers
    }

    override fun observeTypingEvents(): Flow<TypingEvent> {
        return socketManager.typingEvents
    }

    override fun sendTyping(receiverId: String) {
        socketManager.emitTyping(receiverId)
    }

    override fun sendStopTyping(receiverId: String) {
        socketManager.emitStopTyping(receiverId)
    }

    override fun connectSocket() {
        socketManager.connect()
    }

    override fun disconnectSocket() {
        socketManager.disconnect()
    }
}
