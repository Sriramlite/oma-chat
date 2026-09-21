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
    val lastSeen: Long = 0L
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
    val targetId: String
)
