package com.oma.chat.data.repository

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import com.oma.chat.data.local.preferences.AuthPreferences
import com.oma.chat.data.remote.socket.SocketManager
import com.oma.chat.data.webrtc.WebRtcClient
import com.oma.chat.di.IoDispatcher
import com.oma.chat.domain.model.CallState
import com.oma.chat.domain.model.CallType
import com.oma.chat.domain.repository.CallRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.webrtc.IceCandidate
import org.webrtc.SessionDescription
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CallRepositoryImpl @Inject constructor(
    private val webRtcClient: WebRtcClient,
    private val socketManager: SocketManager,
    private val authPreferences: AuthPreferences,
    private val callSoundManager: com.oma.chat.data.call.CallSoundManager,
    private val callProximityManager: com.oma.chat.data.call.CallProximityManager,
    private val errorNotificationManager: com.oma.chat.data.notification.ErrorNotificationManager,
    private val logger: com.oma.chat.data.diagnostics.DiagnosticsLogger,
    @ApplicationContext private val context: Context,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : CallRepository {

    private val scope = CoroutineScope(SupervisorJob() + ioDispatcher)
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private val _callState = MutableStateFlow<CallState>(CallState.Idle)
    override val callState: StateFlow<CallState> = _callState.asStateFlow()
    override val isNearEar: StateFlow<Boolean> = callProximityManager.isNear

    private var timerJob: Job? = null
    private var unansweredJob: Job? = null
    private var activeTargetId: String? = null

    init {
        observeSocketEvents()
    }

    private fun observeSocketEvents() {
        scope.launch {
            socketManager.callOffers.collectLatest { event ->
                if (_callState.value is CallState.Idle) {
                    activeTargetId = event.callerId
                    val type = if (event.type.equals("voice", ignoreCase = true) || event.type.equals("audio", ignoreCase = true)) CallType.VOICE else CallType.VIDEO
                    logger.info("CallSignaling", "Received incoming call offer from ${event.callerName} (${event.callerId}), Type: $type, SDP length: ${event.sdp.length}")
                    _callState.value = CallState.IncomingRinging(
                        callerId = event.callerId,
                        callerName = event.callerName ?: "Incoming Call",
                        callerAvatar = event.callerAvatar ?: "",
                        sdp = event.sdp,
                        callType = type
                    )
                }
            }
        }

        scope.launch {
            socketManager.callAnswers.collectLatest { event ->
                val current = _callState.value
                if (current is CallState.OutgoingRinging || current is CallState.Connecting) {
                    try {
                        unansweredJob?.cancel()
                        unansweredJob = null
                        callSoundManager.stopAll()

                        logger.info("CallSignaling", "Received call answer from peer ${event.targetId}. Setting remote description...")
                        webRtcClient.setRemoteDescription(
                            SessionDescription(SessionDescription.Type.ANSWER, event.sdp)
                        )
                        val callType = if (current is CallState.OutgoingRinging) {
                            current.callType
                        } else if (current is CallState.Connecting) {
                            current.callType
                        } else if (event.type.equals("voice", ignoreCase = true) || event.type.equals("audio", ignoreCase = true)) {
                            CallType.VOICE
                        } else {
                            CallType.VIDEO
                        }
                        logger.success("CallSignaling", "Remote answer set successfully! Transitioning call to Connected.")
                        startConnectedSession(
                            targetId = event.targetId,
                            targetName = if (current is CallState.OutgoingRinging) current.targetName else "Call",
                            targetAvatar = if (current is CallState.OutgoingRinging) current.targetAvatar else "",
                            callType = callType
                        )
                    } catch (e: Exception) {
                        e.printStackTrace()
                        logger.error("CallSignaling", "Failed to set answer description: ${e.localizedMessage}")
                        errorNotificationManager.showError("Call Answer Error", e.localizedMessage ?: "Failed to set answer description")
                    }
                }
            }
        }

        scope.launch {
            socketManager.iceCandidates.collectLatest { event ->
                logger.info("CallSignaling", "Received remote ICE candidate (${event.sdpMid}, mLine:${event.sdpMLineIndex}): ${event.candidate.take(35)}...")
                val candidate = IceCandidate(event.sdpMid, event.sdpMLineIndex, event.candidate)
                webRtcClient.addIceCandidate(candidate)
            }
        }

        scope.launch {
            socketManager.callUnreachableEvents.collectLatest { event ->
                val current = _callState.value
                if (current is CallState.OutgoingRinging && (current.targetId == event.targetId || activeTargetId == event.targetId)) {
                    logger.info("CallSignaling", "Peer ${event.targetId} is unreachable/offline. Playing unreachable tone sequence.")
                    unansweredJob?.cancel()
                    unansweredJob = null
                    handleCallEndedWithSound(
                        reason = "User is unreachable",
                        playSequence = { onFinish -> callSoundManager.playUnreachableSequence(onFinish = onFinish) }
                    )
                }
            }
        }

        scope.launch {
            socketManager.endCallEvents.collectLatest { event ->
                logger.info("CallSignaling", "Received end-call event from peer, reason: ${event.reason}")
                unansweredJob?.cancel()
                unansweredJob = null

                val current = _callState.value
                if (current is CallState.OutgoingRinging) {
                    val isRejected = event.reason?.equals("rejected", ignoreCase = true) == true ||
                            event.reason?.equals("declined", ignoreCase = true) == true ||
                            event.reason?.equals("busy", ignoreCase = true) == true

                    val isOffline = event.reason?.equals("offline", ignoreCase = true) == true ||
                            event.reason?.equals("unreachable", ignoreCase = true) == true

                    if (isRejected) {
                        handleCallEndedWithSound(
                            reason = "Call declined",
                            playSequence = { onFinish -> callSoundManager.playCallRejectedSequence(onFinish = onFinish) }
                        )
                    } else if (isOffline) {
                        handleCallEndedWithSound(
                            reason = "User is unreachable",
                            playSequence = { onFinish -> callSoundManager.playUnreachableSequence(onFinish = onFinish) }
                        )
                    } else {
                        handleCallEndedWithSound(
                            reason = if (event.reason.isNullOrBlank()) "Call ended by remote party" else event.reason!!,
                            playSequence = { onFinish -> callSoundManager.playBusyTone(onFinish = onFinish) }
                        )
                    }
                } else {
                    endCallInternal(reason = if (!event.reason.isNullOrBlank()) event.reason!! else "Call ended by remote party")
                }
            }
        }
    }

    override fun startCall(
        targetId: String,
        targetName: String,
        targetAvatar: String,
        callType: CallType
    ) {
        scope.launch {
            activeTargetId = targetId
            _callState.value = CallState.OutgoingRinging(
                targetId = targetId,
                targetName = targetName,
                targetAvatar = targetAvatar,
                callType = callType
            )

            // Play outgoing dialing ringtone
            callSoundManager.playOutgoingCall()

            // 20-second unanswered timeout
            unansweredJob?.cancel()
            unansweredJob = scope.launch {
                delay(20000L) // 20 seconds timeout
                if (_callState.value is CallState.OutgoingRinging) {
                    logger.info("CallRepository", "Call unanswered after 20s. Ending with busy tone.")
                    socketManager.emitEndCall(targetId, reason = "unanswered")
                    handleCallEndedWithSound(
                        reason = "Call unanswered",
                        playSequence = { onFinish -> callSoundManager.playBusyTone(durationMs = 3500L, onFinish = onFinish) }
                    )
                }
            }

            setupAudioMode(callType == CallType.VIDEO)
            initWebRtc(targetId, isCaller = true, callType = callType)

            try {
                logger.info("CallRepository", "Creating WebRTC Offer for $callType call to $targetId...")
                val offer = webRtcClient.createOffer(callType)
                webRtcClient.setLocalDescription(offer)

                val myUser = authPreferences.getUser()
                val myName = myUser?.name?.ifBlank { myUser.username } ?: ""
                val myAvatar = myUser?.avatar ?: ""
                val wireType = if (callType == CallType.VIDEO) "video" else "audio"
                logger.info("CallRepository", "Emitting offer (SDP ${offer.description.length} chars, Type: $wireType) to socket...")
                socketManager.emitCallOffer(
                    targetId = targetId,
                    sdp = offer.description,
                    type = wireType,
                    callerName = myName,
                    callerAvatar = myAvatar
                )
            } catch (e: Exception) {
                logger.error("CallRepository", "Failed to start call: ${e.localizedMessage}")
                errorNotificationManager.showError("Failed to Start Call", e.localizedMessage ?: "WebRTC offer creation error")
                endCallInternal(reason = e.localizedMessage ?: "Failed to initiate call")
            }
        }
    }

    override fun acceptCall(callerId: String, sdp: String, callType: CallType) {
        scope.launch {
            val current = _callState.value
            val callerName = if (current is CallState.IncomingRinging) current.callerName else "Incoming Call"
            val callerAvatar = if (current is CallState.IncomingRinging) current.callerAvatar else ""

            _callState.value = CallState.Connecting(
                targetId = callerId,
                targetName = callerName,
                targetAvatar = callerAvatar,
                callType = callType
            )

            setupAudioMode(callType == CallType.VIDEO)
            initWebRtc(callerId, isCaller = false, callType = callType)

            try {
                logger.info("CallRepository", "Setting remote offer SDP (${sdp.length} chars)...")
                webRtcClient.setRemoteDescription(
                    SessionDescription(SessionDescription.Type.OFFER, sdp)
                )
                logger.info("CallRepository", "Creating answer for $callType call...")
                val answer = webRtcClient.createAnswer(callType)
                webRtcClient.setLocalDescription(answer)

                val wireType = if (callType == CallType.VIDEO) "video" else "audio"
                logger.info("CallRepository", "Emitting call answer (Type: $wireType) to caller $callerId...")
                socketManager.emitCallAnswer(
                    targetId = callerId,
                    sdp = answer.description,
                    type = wireType
                )

                startConnectedSession(
                    targetId = callerId,
                    targetName = callerName,
                    targetAvatar = callerAvatar,
                    callType = callType
                )
            } catch (e: Exception) {
                logger.error("CallRepository", "Failed to accept call: ${e.localizedMessage}")
                errorNotificationManager.showError("Failed to Accept Call", e.localizedMessage ?: "WebRTC answer creation error")
                endCallInternal(reason = e.localizedMessage ?: "Failed to accept call")
            }
        }
    }

    override fun rejectCall(callerId: String) {
        scope.launch {
            callSoundManager.stopAll()
            socketManager.emitEndCall(callerId, reason = "rejected")
            endCallInternal(reason = "Call declined")
        }
    }

    override fun endCall() {
        scope.launch {
            unansweredJob?.cancel()
            unansweredJob = null
            callSoundManager.stopAll()
            activeTargetId?.let { targetId ->
                socketManager.emitEndCall(targetId, reason = "ended")
            }
            endCallInternal(reason = "Call ended")
        }
    }

    private var audioFocusRequest: AudioFocusRequest? = null

    override fun toggleMute(muted: Boolean) {
        webRtcClient.toggleAudio(!muted)
        try {
            audioManager.isMicrophoneMute = muted
        } catch (e: Exception) {
            e.printStackTrace()
        }
        _callState.update { current ->
            if (current is CallState.Connected) {
                current.copy(isAudioMuted = muted)
            } else current
        }
    }

    override fun toggleVideo(muted: Boolean) {
        webRtcClient.toggleVideo(!muted)
        _callState.update { current ->
            if (current is CallState.Connected) {
                current.copy(isVideoMuted = muted)
            } else current
        }
    }

    override fun switchCamera() {
        webRtcClient.switchCamera()
        _callState.update { current ->
            if (current is CallState.Connected) {
                current.copy(isFrontCamera = !current.isFrontCamera)
            } else current
        }
    }

    override fun toggleSpeaker(enabled: Boolean) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val devices = audioManager.availableCommunicationDevices
                val targetType = if (enabled) {
                    android.media.AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
                } else {
                    android.media.AudioDeviceInfo.TYPE_BUILTIN_EARPIECE
                }
                val device = devices.firstOrNull { it.type == targetType }
                    ?: devices.firstOrNull { it.type == android.media.AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
                if (device != null) {
                    audioManager.setCommunicationDevice(device)
                }
            } else {
                @Suppress("DEPRECATION")
                audioManager.isSpeakerphoneOn = enabled
            }
            if (enabled) {
                callProximityManager.release()
            } else {
                callProximityManager.acquire()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        _callState.update { current ->
            when (current) {
                is CallState.Connected -> current.copy(isSpeakerOn = enabled)
                is CallState.OutgoingRinging -> current.copy(isSpeakerOn = enabled)
                is CallState.Connecting -> current.copy(isSpeakerOn = enabled)
                else -> current
            }
        }
    }

    override fun getWebRtcClient(): WebRtcClient = webRtcClient

    private fun initWebRtc(targetId: String, isCaller: Boolean, callType: CallType) {
        webRtcClient.initPeerConnection(
            onIceCandidate = { candidate ->
                socketManager.emitIceCandidate(
                    targetId = targetId,
                    sdpMid = candidate.sdpMid,
                    sdpMLineIndex = candidate.sdpMLineIndex,
                    candidate = candidate.sdp
                )
            },
            onRemoteTrack = {
                // Track received callback
            }
        )

        webRtcClient.startLocalAudio()
        if (callType == CallType.VIDEO) {
            webRtcClient.startLocalVideo(isFrontFacing = true)
        }
    }

    private fun startConnectedSession(
        targetId: String,
        targetName: String,
        targetAvatar: String,
        callType: CallType
    ) {
        timerJob?.cancel()
        _callState.value = CallState.Connected(
            targetId = targetId,
            targetName = targetName,
            targetAvatar = targetAvatar,
            callType = callType,
            durationSeconds = 0L,
            isSpeakerOn = callType == CallType.VIDEO
        )

        timerJob = scope.launch {
            var seconds = 0L
            while (isActive) {
                delay(1000)
                seconds++
                _callState.update { current ->
                    if (current is CallState.Connected) {
                        current.copy(durationSeconds = seconds)
                    } else current
                }
            }
        }
    }

    private fun setupAudioMode(isSpeaker: Boolean) {
        try {
            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
            audioManager.isMicrophoneMute = false

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val devices = audioManager.availableCommunicationDevices
                val targetType = if (isSpeaker) android.media.AudioDeviceInfo.TYPE_BUILTIN_SPEAKER else android.media.AudioDeviceInfo.TYPE_BUILTIN_EARPIECE
                val device = devices.firstOrNull { it.type == targetType } ?: devices.firstOrNull { it.type == android.media.AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
                if (device != null) {
                    audioManager.setCommunicationDevice(device)
                }
            } else {
                @Suppress("DEPRECATION")
                audioManager.isSpeakerphoneOn = isSpeaker
            }

            if (isSpeaker) {
                callProximityManager.release()
            } else {
                callProximityManager.acquire()
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val playbackAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
                val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                    .setAudioAttributes(playbackAttributes)
                    .setAcceptsDelayedFocusGain(true)
                    .build()
                audioFocusRequest = request
                audioManager.requestAudioFocus(request)
            } else {
                @Suppress("DEPRECATION")
                audioManager.requestAudioFocus(null, AudioManager.STREAM_VOICE_CALL, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun resetAudioMode() {
        try {
            callProximityManager.release()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                audioManager.clearCommunicationDevice()
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
                audioFocusRequest = null
            } else {
                @Suppress("DEPRECATION")
                audioManager.abandonAudioFocus(null)
            }
            audioManager.mode = AudioManager.MODE_NORMAL
            audioManager.isMicrophoneMute = false
            @Suppress("DEPRECATION")
            audioManager.isSpeakerphoneOn = false
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun handleCallEndedWithSound(
        reason: String,
        playSequence: (onFinish: () -> Unit) -> Unit
    ) {
        unansweredJob?.cancel()
        unansweredJob = null
        timerJob?.cancel()
        timerJob = null
        activeTargetId = null

        resetAudioMode()
        webRtcClient.close()

        _callState.value = CallState.Ended(reason)

        playSequence {
            if (_callState.value is CallState.Ended) {
                _callState.value = CallState.Idle
            }
        }
    }

    private fun endCallInternal(reason: String) {
        unansweredJob?.cancel()
        unansweredJob = null
        timerJob?.cancel()
        timerJob = null
        activeTargetId = null

        callSoundManager.stopAll()
        resetAudioMode()
        webRtcClient.close()

        _callState.value = CallState.Ended(reason)

        scope.launch {
            delay(1500)
            if (_callState.value is CallState.Ended) {
                _callState.value = CallState.Idle
            }
        }
    }
}
