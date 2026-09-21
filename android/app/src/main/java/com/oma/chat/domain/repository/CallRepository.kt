package com.oma.chat.domain.repository

import com.oma.chat.data.webrtc.WebRtcClient
import com.oma.chat.domain.model.CallState
import com.oma.chat.domain.model.CallType
import kotlinx.coroutines.flow.StateFlow

interface CallRepository {
    val callState: StateFlow<CallState>
    fun startCall(targetId: String, targetName: String, targetAvatar: String, callType: CallType)
    fun acceptCall(callerId: String, sdp: String, callType: CallType)
    fun rejectCall(callerId: String)
    fun endCall()
    fun toggleMute(muted: Boolean)
    fun toggleVideo(muted: Boolean)
    fun switchCamera()
    fun toggleSpeaker(enabled: Boolean)
    fun getWebRtcClient(): WebRtcClient
}
