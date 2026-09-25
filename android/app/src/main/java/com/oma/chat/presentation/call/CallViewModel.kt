package com.oma.chat.presentation.call

import androidx.lifecycle.ViewModel
import com.oma.chat.data.webrtc.WebRtcClient
import com.oma.chat.domain.model.CallState
import com.oma.chat.domain.model.CallType
import com.oma.chat.domain.repository.CallRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class CallViewModel @Inject constructor(
    private val callRepository: CallRepository
) : ViewModel() {

    val callState: StateFlow<CallState> = callRepository.callState
    val isNearEar: StateFlow<Boolean> = callRepository.isNearEar

    val webRtcClient: WebRtcClient
        get() = callRepository.getWebRtcClient()

    fun startCall(
        targetId: String,
        targetName: String,
        targetAvatar: String,
        callType: CallType
    ) {
        callRepository.startCall(targetId, targetName, targetAvatar, callType)
    }

    fun acceptCall(callerId: String, sdp: String, callType: CallType) {
        callRepository.acceptCall(callerId, sdp, callType)
    }

    fun setIncomingCall(callerId: String, callerName: String, callerAvatar: String, sdp: String, callType: CallType) {
        callRepository.setIncomingCall(callerId, callerName, callerAvatar, sdp, callType)
    }

    fun rejectCall(callerId: String) {
        callRepository.rejectCall(callerId)
    }

    fun endCall() {
        callRepository.endCall()
    }

    fun toggleMute(muted: Boolean) {
        callRepository.toggleMute(muted)
    }

    fun toggleVideo(muted: Boolean) {
        callRepository.toggleVideo(muted)
    }

    fun switchCamera() {
        callRepository.switchCamera()
    }

    fun toggleSpeaker(enabled: Boolean) {
        callRepository.toggleSpeaker(enabled)
    }
}
