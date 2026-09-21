package com.oma.chat.data.remote.dto

import com.google.gson.annotations.SerializedName
import com.oma.chat.domain.model.Conversation
import com.oma.chat.domain.model.Message
import com.oma.chat.domain.model.MessageStatus
import com.oma.chat.domain.model.MessageType
import com.oma.chat.domain.model.User

data class ChatConversationDto(
    @SerializedName("id") val id: String,
    @SerializedName("name") val name: String? = null,
    @SerializedName("username") val username: String? = null,
    @SerializedName("avatar") val avatar: String? = null,
    @SerializedName("lastMsg") val lastMsg: String? = null,
    @SerializedName("timestamp") val timestamp: Any? = null,
    @SerializedName("type") val type: String? = "user",
    @SerializedName("status") val status: String? = null,
    @SerializedName("lastSeen") val lastSeen: Any? = null,
    @SerializedName("unreadCount") val unreadCount: Int? = 0
) {
    fun toDomain(ownerUserId: String): Conversation {
        val parsedTs = when (timestamp) {
            is Number -> timestamp.toLong()
            is String -> timestamp.toLongOrNull() ?: 0L
            else -> 0L
        }
        val parsedLastSeen = when (lastSeen) {
            is Number -> lastSeen.toLong()
            is String -> lastSeen.toLongOrNull() ?: 0L
            else -> 0L
        }
        return Conversation(
            id = id,
            name = name ?: username ?: "User",
            username = username,
            avatar = avatar ?: "https://cdn.pixabay.com/photo/2015/10/05/22/37/blank-profile-picture-973460_1280.png",
            lastMsg = lastMsg ?: "",
            timestamp = parsedTs,
            type = type ?: "user",
            unreadCount = unreadCount ?: 0,
            isOnline = false,
            lastSeen = parsedLastSeen
        )
    }
}

data class ChatMessageDto(
    @SerializedName("id") val id: String,
    @SerializedName("tempId") val tempId: String? = null,
    @SerializedName("chatId") val chatId: String? = null,
    @SerializedName("senderId") val senderId: String? = null,
    @SerializedName("senderName") val senderName: String? = null,
    @SerializedName("avatar") val avatar: String? = null,
    @SerializedName("receiverId") val receiverId: String? = null,
    @SerializedName("content") val content: String? = null,
    @SerializedName("type") val type: String? = "text",
    @SerializedName("timestamp") val timestamp: Any? = null,
    @SerializedName("status") val status: String? = "sent",
    @SerializedName("replyToId") val replyToId: String? = null,
    @SerializedName("isStarred") val isStarred: Boolean? = false,
    @SerializedName("isPinned") val isPinned: Boolean? = false,
    @SerializedName("isDeleted") val isDeleted: Boolean? = false,
    @SerializedName("isEdited") val isEdited: Boolean? = false
) {
    fun toDomain(ownerUserId: String, currentChatId: String? = null): Message {
        val effectiveChatId = chatId ?: (if (senderId == ownerUserId) receiverId else senderId) ?: currentChatId ?: ""
        val parsedTs = when (timestamp) {
            is Number -> timestamp.toLong()
            is String -> timestamp.toLongOrNull() ?: System.currentTimeMillis()
            else -> System.currentTimeMillis()
        }
        val domainStatus = when (status?.lowercase()) {
            "pending" -> MessageStatus.PENDING
            "sending" -> MessageStatus.SENDING
            "sent" -> MessageStatus.SENT
            "delivered" -> MessageStatus.DELIVERED
            "seen", "read" -> MessageStatus.SEEN
            "failed" -> MessageStatus.FAILED
            else -> MessageStatus.SENT
        }
        return Message(
            id = id,
            tempId = tempId,
            chatId = effectiveChatId,
            senderId = senderId ?: ownerUserId,
            senderName = senderName ?: "User",
            avatar = avatar ?: "https://cdn.pixabay.com/photo/2015/10/05/22/37/blank-profile-picture-973460_1280.png",
            content = content ?: "",
            type = MessageType.fromValue(type ?: "text"),
            timestamp = parsedTs,
            status = domainStatus,
            replyToId = replyToId,
            isStarred = isStarred == true,
            isPinned = isPinned == true,
            isDeleted = isDeleted == true,
            isEdited = isEdited == true
        )
    }
}

data class SendMessageRequest(
    @SerializedName("content") val content: String,
    @SerializedName("type") val type: String = "text",
    @SerializedName("receiverId") val receiverId: String,
    @SerializedName("replyToId") val replyToId: String? = null,
    @SerializedName("tempId") val tempId: String? = null
)

data class ReadChatRequest(
    @SerializedName("chatId") val chatId: String
)

data class DeliverMessagesRequest(
    @SerializedName("messageIds") val messageIds: List<String>
)

data class UserSearchDto(
    @SerializedName("id") val id: String,
    @SerializedName("name") val name: String? = null,
    @SerializedName("username") val username: String,
    @SerializedName("avatar") val avatar: String? = null,
    @SerializedName("bio") val bio: String? = null,
    @SerializedName("lastSeen") val lastSeen: Any? = null
) {
    fun toDomain(): User {
        val parsedLastSeen = when (lastSeen) {
            is Number -> lastSeen.toLong()
            is String -> lastSeen.toLongOrNull() ?: 0L
            else -> 0L
        }
        return User(
            id = id,
            username = username,
            name = name ?: username,
            avatar = avatar ?: "https://cdn.pixabay.com/photo/2015/10/05/22/37/blank-profile-picture-973460_1280.png",
            bio = bio,
            lastSeen = parsedLastSeen
        )
    }
}
