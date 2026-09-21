package com.oma.chat.domain.model

enum class MessageStatus(val value: String) {
    PENDING("pending"),
    SENDING("sending"),
    SENT("sent"),
    DELIVERED("delivered"),
    SEEN("seen"),
    FAILED("failed");

    companion object {
        fun fromValue(value: String): MessageStatus {
            return entries.find { it.value.equals(value, ignoreCase = true) } ?: SENT
        }
    }
}

enum class MessageType(val value: String) {
    TEXT("text"),
    IMAGE("image"),
    VIDEO("video"),
    AUDIO("audio"),
    FILE("file"),
    GIF("gif"),
    CALL_LOG("call_log"),
    SYSTEM("system");

    companion object {
        fun fromValue(value: String): MessageType {
            return entries.find { it.value.equals(value, ignoreCase = true) } ?: TEXT
        }
    }
}

data class Message(
    val id: String,
    val tempId: String? = null,
    val chatId: String,
    val senderId: String,
    val senderName: String,
    val avatar: String,
    val content: String,
    val type: MessageType = MessageType.TEXT,
    val timestamp: Long,
    val status: MessageStatus = MessageStatus.SENT,
    val replyToId: String? = null,
    val isStarred: Boolean = false,
    val isPinned: Boolean = false,
    val isDeleted: Boolean = false,
    val isEdited: Boolean = false
)

data class Conversation(
    val id: String,
    val name: String,
    val username: String? = null,
    val avatar: String,
    val lastMsg: String,
    val timestamp: Long,
    val type: String = "user",
    val unreadCount: Int = 0,
    val isOnline: Boolean = false,
    val lastSeen: Long = 0L
)
