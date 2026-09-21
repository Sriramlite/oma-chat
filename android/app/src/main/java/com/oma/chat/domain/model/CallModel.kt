package com.oma.chat.domain.model

enum class CallType {
    VOICE,
    VIDEO
}

sealed interface CallState {
    object Idle : CallState

    data class OutgoingRinging(
        val targetId: String,
        val targetName: String,
        val targetAvatar: String,
        val callType: CallType
    ) : CallState

    data class IncomingRinging(
        val callerId: String,
        val callerName: String,
        val callerAvatar: String,
        val sdp: String,
        val callType: CallType
    ) : CallState

    data class Connecting(
        val targetId: String,
        val targetName: String,
        val targetAvatar: String,
        val callType: CallType
    ) : CallState

    data class Connected(
        val targetId: String,
        val targetName: String,
        val targetAvatar: String,
        val callType: CallType,
        val durationSeconds: Long = 0L,
        val isAudioMuted: Boolean = false,
        val isVideoMuted: Boolean = false,
        val isSpeakerOn: Boolean = false,
        val isFrontCamera: Boolean = true
    ) : CallState

    data class Ended(
        val reason: String
    ) : CallState
}
