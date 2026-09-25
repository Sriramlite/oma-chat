package com.oma.chat.data.remote.socket

sealed interface SocketConnectionState {
    object Disconnected : SocketConnectionState
    object Connecting : SocketConnectionState
    object Connected : SocketConnectionState
    data class Error(val reason: String) : SocketConnectionState
}

data class UserStatusEvent(
    val userId: String,
    val online: Boolean,
    val lastSeen: Long = 0L,
    val batteryLevel: Int? = null,
    val isCharging: Boolean = false
)

data class TypingEvent(
    val senderId: String,
    val receiverId: String,
    val isTyping: Boolean
)

data class CallOfferEvent(
    val targetId: String,
    val callerId: String,
    val callerName: String?,
    val callerAvatar: String? = null,
    val sdp: String,
    val type: String // "voice" or "video"
)

data class CallAnswerEvent(
    val targetId: String,
    val sdp: String,
    val type: String
)

data class IceCandidateEvent(
    val targetId: String,
    val sdpMid: String?,
    val sdpMLineIndex: Int,
    val candidate: String
)

data class EndCallEvent(
    val targetId: String,
    val reason: String? = null
)

data class CallUnreachableEvent(
    val targetId: String,
    val reason: String = "offline"
)

data class MessageEditedEvent(
    val messageId: String,
    val chatId: String,
    val senderId: String,
    val receiverId: String,
    val newContent: String,
    val isEdited: Boolean = true
)

data class MessageDeletedEvent(
    val messageId: String,
    val chatId: String,
    val senderId: String,
    val receiverId: String,
    val mode: String, // "everyone" or "me"
    val deletedFor: String? = null,
    val isDeleted: Boolean = true,
    val content: String = "🚫 This message was deleted"
)

data class ChatDeletedEvent(
    val chatId: String,
    val deletedBy: String
)

data class MessageStarredEvent(
    val messageId: String,
    val userId: String,
    val isStarred: Boolean
)

data class MessagePinnedEvent(
    val messageId: String,
    val chatId: String,
    val isPinned: Boolean
)

data class MessagesSeenEvent(
    val readerId: String,
    val chatId: String
)
