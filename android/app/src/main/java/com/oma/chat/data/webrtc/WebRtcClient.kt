package com.oma.chat.data.webrtc

import android.content.Context
import android.media.MediaRecorder
import android.util.Log
import com.oma.chat.domain.model.CallType
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import org.webrtc.AudioSource
import org.webrtc.AudioTrack
import org.webrtc.Camera2Enumerator
import org.webrtc.CameraVideoCapturer
import org.webrtc.DataChannel
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.MediaStreamTrack
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpReceiver
import org.webrtc.RtpTransceiver
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.SurfaceTextureHelper
import org.webrtc.VideoCapturer
import org.webrtc.VideoSource
import org.webrtc.VideoTrack
import org.webrtc.audio.JavaAudioDeviceModule
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@Singleton
class WebRtcClient @Inject constructor(
    @ApplicationContext private val context: Context,
    private val errorNotificationManager: com.oma.chat.data.notification.ErrorNotificationManager,
    private val logger: com.oma.chat.data.diagnostics.DiagnosticsLogger
) {
    val eglBase: EglBase by lazy { EglBase.create() }

    private var peerConnectionFactory: PeerConnectionFactory? = null
    private var peerConnection: PeerConnection? = null

    private var localAudioSource: AudioSource? = null
    var localAudioTrack: AudioTrack? = null
        private set

    private var localVideoSource: VideoSource? = null
    var localVideoTrack: VideoTrack? = null
        private set

    private var surfaceTextureHelper: SurfaceTextureHelper? = null
    private var videoCapturer: CameraVideoCapturer? = null

    var remoteVideoTrack: VideoTrack? = null
        private set
    var remoteAudioTrack: AudioTrack? = null
        private set

    private val pendingIceCandidates = mutableListOf<IceCandidate>()
    private var isRemoteDescriptionSet = false

    private val iceServers = listOf(
        PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
        PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer(),
        PeerConnection.IceServer.builder("stun:stun2.l.google.com:19302").createIceServer(),
        PeerConnection.IceServer.builder("stun:stun3.l.google.com:19302").createIceServer(),
        PeerConnection.IceServer.builder("stun:stun4.l.google.com:19302").createIceServer(),
        PeerConnection.IceServer.builder("stun:global.stun.twilio.com:3478").createIceServer(),
        PeerConnection.IceServer.builder("stun:stun.services.mozilla.com:3478").createIceServer(),
        PeerConnection.IceServer.builder("stun:openrelay.metered.ca:80").createIceServer(),
        PeerConnection.IceServer.builder("turn:openrelay.metered.ca:80")
            .setUsername("openrelayproject")
            .setPassword("openrelayproject")
            .createIceServer(),
        PeerConnection.IceServer.builder("turn:openrelay.metered.ca:443")
            .setUsername("openrelayproject")
            .setPassword("openrelayproject")
            .createIceServer(),
        PeerConnection.IceServer.builder("turn:openrelay.metered.ca:443?transport=tcp")
            .setUsername("openrelayproject")
            .setPassword("openrelayproject")
            .createIceServer()
    )

    init {
        val options = PeerConnectionFactory.InitializationOptions.builder(context)
            .setEnableInternalTracer(true)
            .createInitializationOptions()
        PeerConnectionFactory.initialize(options)
    }

    fun ensurePeerConnectionFactory(): PeerConnectionFactory {
        peerConnectionFactory?.let { return it }

        logger.info("AudioConfig", "Configuring WebRTC JavaAudioDeviceModule (AudioSource.MIC + Software AEC/AGC/NS)")

        val audioDeviceModule = JavaAudioDeviceModule.builder(context)
            .setAudioSource(MediaRecorder.AudioSource.MIC)
            .setUseHardwareAcousticEchoCanceler(false)
            .setUseHardwareNoiseSuppressor(false)
            .setUseStereoInput(false)
            .setUseStereoOutput(false)
            .setAudioRecordErrorCallback(object : JavaAudioDeviceModule.AudioRecordErrorCallback {
                override fun onWebRtcAudioRecordInitError(errorMessage: String?) {
                    Log.e("WebRtcClient", "AudioRecord Init Error: $errorMessage")
                    logger.error("AudioRecord", "Init Error: $errorMessage")
                    errorNotificationManager.showError("Microphone Init Error", errorMessage ?: "Failed to open microphone AudioRecord")
                }
                override fun onWebRtcAudioRecordStartError(
                    errorCode: JavaAudioDeviceModule.AudioRecordStartErrorCode?,
                    errorMessage: String?
                ) {
                    Log.e("WebRtcClient", "AudioRecord Start Error: $errorCode - $errorMessage")
                    logger.error("AudioRecord", "Start Error ($errorCode): $errorMessage")
                    errorNotificationManager.showError("Microphone Start Error", "Code: $errorCode. $errorMessage")
                }
                override fun onWebRtcAudioRecordError(errorMessage: String?) {
                    Log.e("WebRtcClient", "AudioRecord Error: $errorMessage")
                    logger.error("AudioRecord", "Record Error: $errorMessage")
                    errorNotificationManager.showError("Microphone Recording Error", errorMessage ?: "AudioRecord error during call")
                }
            })
            .setAudioRecordStateCallback(object : JavaAudioDeviceModule.AudioRecordStateCallback {
                override fun onWebRtcAudioRecordStart() {
                    Log.i("WebRtcClient", "Microphone recording started successfully")
                    logger.success("AudioRecord", "Microphone hardware recording ACTIVE (WebRTC AudioDeviceModule)")
                }
                override fun onWebRtcAudioRecordStop() {
                    Log.i("WebRtcClient", "Microphone recording stopped")
                    logger.info("AudioRecord", "Microphone hardware recording STOPPED")
                }
            })
            .setAudioTrackErrorCallback(object : JavaAudioDeviceModule.AudioTrackErrorCallback {
                override fun onWebRtcAudioTrackInitError(errorMessage: String?) {
                    Log.e("WebRtcClient", "AudioTrack Init Error: $errorMessage")
                    logger.error("AudioTrack", "Init Error: $errorMessage")
                    errorNotificationManager.showError("Audio Playback Init Error", errorMessage ?: "Speaker playback init failed")
                }
                override fun onWebRtcAudioTrackStartError(
                    errorCode: JavaAudioDeviceModule.AudioTrackStartErrorCode?,
                    errorMessage: String?
                ) {
                    Log.e("WebRtcClient", "AudioTrack Start Error: $errorCode - $errorMessage")
                    logger.error("AudioTrack", "Start Error ($errorCode): $errorMessage")
                    errorNotificationManager.showError("Audio Playback Start Error", "Code: $errorCode. $errorMessage")
                }
                override fun onWebRtcAudioTrackError(errorMessage: String?) {
                    Log.e("WebRtcClient", "AudioTrack Error: $errorMessage")
                    logger.error("AudioTrack", "Playback Error: $errorMessage")
                    errorNotificationManager.showError("Audio Playback Error", errorMessage ?: "AudioTrack error during call")
                }
            })
            .createAudioDeviceModule()

        audioDeviceModule.setMicrophoneMute(false)
        audioDeviceModule.setSpeakerMute(false)

        val encoderFactory = DefaultVideoEncoderFactory(
            eglBase.eglBaseContext,
            /* enableIntelVp8Encoder = */ true,
            /* enableH264HighProfile = */ true
        )
        val decoderFactory = DefaultVideoDecoderFactory(eglBase.eglBaseContext)

        val factory = PeerConnectionFactory.builder()
            .setAudioDeviceModule(audioDeviceModule)
            .setVideoEncoderFactory(encoderFactory)
            .setVideoDecoderFactory(decoderFactory)
            .setOptions(PeerConnectionFactory.Options())
            .createPeerConnectionFactory()

        peerConnectionFactory = factory
        return factory
    }

    fun initPeerConnection(
        onIceCandidate: (IceCandidate) -> Unit,
        onRemoteTrack: (MediaStreamTrack) -> Unit
    ) {
        synchronized(pendingIceCandidates) {
            isRemoteDescriptionSet = false
        }

        val factory = ensurePeerConnectionFactory()
        val rtcConfig = PeerConnection.RTCConfiguration(iceServers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE
            rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
            iceCandidatePoolSize = 10
            tcpCandidatePolicy = PeerConnection.TcpCandidatePolicy.ENABLED
            candidateNetworkPolicy = PeerConnection.CandidateNetworkPolicy.ALL
        }

        peerConnection = factory.createPeerConnection(
            rtcConfig,
            object : PeerConnection.Observer {
                override fun onSignalingChange(state: PeerConnection.SignalingState?) {}

                override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {
                    Log.d("WebRtcClient", "ICE Connection State: $state")
                    logger.info("WebRTC", "ICE Connection State -> $state")
                    if (state == PeerConnection.IceConnectionState.FAILED) {
                        logger.warn("WebRTC", "ICE state FAILED. Attempting automatic ICE restart...")
                        try {
                            peerConnection?.restartIce()
                        } catch (e: Exception) {
                            logger.error("WebRTC", "ICE restart failed: ${e.localizedMessage}")
                        }
                    } else if (state == PeerConnection.IceConnectionState.CONNECTED || state == PeerConnection.IceConnectionState.COMPLETED) {
                        logger.success("WebRTC", "P2P DTLS-SRTP Transport Connected! Media streaming live.")
                    }
                }

                override fun onIceConnectionReceivingChange(receiving: Boolean) {}

                override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) {
                    Log.d("WebRtcClient", "ICE Gathering State: $state")
                    logger.info("WebRTC", "ICE Gathering -> $state")
                }

                override fun onIceCandidate(candidate: IceCandidate?) {
                    candidate?.let {
                        logger.info("WebRTC", "Discovered local candidate (${it.sdpMid}): ${it.sdp.take(40)}...")
                        onIceCandidate(it)
                    }
                }

                override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) {}

                override fun onAddStream(stream: MediaStream?) {}

                override fun onRemoveStream(stream: MediaStream?) {}

                override fun onDataChannel(channel: DataChannel?) {}

                override fun onRenegotiationNeeded() {}

                override fun onAddTrack(receiver: RtpReceiver?, streams: Array<out MediaStream>?) {
                    receiver?.track()?.let { track ->
                        if (track is VideoTrack) {
                            remoteVideoTrack = track
                            logger.success("WebRTC", "Received Remote Video Track (${track.id()})")
                        } else if (track is AudioTrack) {
                            remoteAudioTrack = track
                            track.setEnabled(true)
                            logger.success("WebRTC", "Received Remote Audio Track (${track.id()}) - Enabled")
                        }
                        onRemoteTrack(track)
                    }
                }

                override fun onTrack(transceiver: RtpTransceiver?) {
                    transceiver?.receiver?.track()?.let { track ->
                        if (track is VideoTrack) {
                            remoteVideoTrack = track
                            logger.success("WebRTC", "Remote Video Transceiver Active")
                        } else if (track is AudioTrack) {
                            remoteAudioTrack = track
                            track.setEnabled(true)
                            logger.success("WebRTC", "Remote Audio Transceiver Active - Enabled")
                        }
                        onRemoteTrack(track)
                    }
                }
            }
        )
    }

    fun startLocalAudio() {
        try {
            val factory = ensurePeerConnectionFactory()
            val audioConstraints = MediaConstraints().apply {
                optional.add(MediaConstraints.KeyValuePair("googEchoCancellation", "true"))
                optional.add(MediaConstraints.KeyValuePair("googAutoGainControl", "true"))
                optional.add(MediaConstraints.KeyValuePair("googHighpassFilter", "true"))
                optional.add(MediaConstraints.KeyValuePair("googNoiseSuppression", "true"))
            }
            localAudioSource = factory.createAudioSource(audioConstraints)
            localAudioTrack = factory.createAudioTrack("ARDAMSa0", localAudioSource)
            localAudioTrack?.setEnabled(true)
            val sender = peerConnection?.addTrack(localAudioTrack, listOf("ARDAMS"))
            
            // Explicitly force audio transceiver direction to SEND_RECV in Unified Plan
            peerConnection?.transceivers?.forEach { transceiver ->
                if (transceiver.mediaType == MediaStreamTrack.MediaType.MEDIA_TYPE_AUDIO) {
                    transceiver.direction = RtpTransceiver.RtpTransceiverDirection.SEND_RECV
                    Log.i("WebRtcClient", "Audio transceiver configured to SEND_RECV (sender: ${sender?.id()})")
                }
            }
            Log.i("WebRtcClient", "Local audio track created and registered to peerConnection")
            logger.success("WebRTC", "Local Microphone AudioTrack [ARDAMSa0] created, active & transceiver set to SEND_RECV")
        } catch (e: Exception) {
            Log.e("WebRtcClient", "startLocalAudio failed", e)
            logger.error("WebRTC", "Failed to start local audio: ${e.localizedMessage}")
            errorNotificationManager.showError("Microphone Capture Error", e.localizedMessage ?: "Failed to start local audio capture")
        }
    }

    fun startLocalVideo(isFrontFacing: Boolean = true) {
        try {
            val factory = ensurePeerConnectionFactory()
            val cameraEnumerator = Camera2Enumerator(context)
            val deviceNames = cameraEnumerator.deviceNames

            val chosenDevice = deviceNames.firstOrNull {
                if (isFrontFacing) cameraEnumerator.isFrontFacing(it) else cameraEnumerator.isBackFacing(it)
            } ?: deviceNames.firstOrNull() ?: return

            videoCapturer = cameraEnumerator.createCapturer(chosenDevice, null)
            surfaceTextureHelper = SurfaceTextureHelper.create("CaptureThread", eglBase.eglBaseContext)
            localVideoSource = factory.createVideoSource(videoCapturer?.isScreencast == true)
            
            videoCapturer?.initialize(surfaceTextureHelper, context, localVideoSource?.capturerObserver)
            videoCapturer?.startCapture(640, 480, 30)

            localVideoTrack = factory.createVideoTrack("ARDAMSv0", localVideoSource)
            localVideoTrack?.setEnabled(true)
            val sender = peerConnection?.addTrack(localVideoTrack, listOf("ARDAMS"))
            
            // Explicitly force video transceiver direction to SEND_RECV in Unified Plan
            peerConnection?.transceivers?.forEach { transceiver ->
                if (transceiver.mediaType == MediaStreamTrack.MediaType.MEDIA_TYPE_VIDEO) {
                    transceiver.direction = RtpTransceiver.RtpTransceiverDirection.SEND_RECV
                }
            }
            logger.success("WebRTC", "Local Camera VideoTrack [ARDAMSv0] active & registered")
        } catch (e: Exception) {
            e.printStackTrace()
            logger.error("WebRTC", "Failed to start local video: ${e.localizedMessage}")
        }
    }

    suspend fun createOffer(callType: CallType = CallType.VIDEO): SessionDescription = suspendCancellableCoroutine { continuation ->
        val constraints = MediaConstraints()
        peerConnection?.createOffer(object : SdpObserver {
            override fun onCreateSuccess(desc: SessionDescription?) {
                if (desc != null) {
                    logger.info("WebRTC", "Offer SDP created successfully (${desc.description.lines().filter { it.startsWith("m=") || it.startsWith("a=send") || it.startsWith("a=recv") }.joinToString("; ")})")
                    continuation.resume(desc)
                } else {
                    continuation.resumeWithException(Exception("SDP description was null"))
                }
            }

            override fun onSetSuccess() {}
            override fun onCreateFailure(error: String?) {
                logger.error("WebRTC", "Create Offer Failed: $error")
                continuation.resumeWithException(Exception("Create Offer Failed: $error"))
            }
            override fun onSetFailure(error: String?) {}
        }, constraints)
    }

    suspend fun createAnswer(callType: CallType = CallType.VIDEO): SessionDescription = suspendCancellableCoroutine { continuation ->
        val constraints = MediaConstraints()
        peerConnection?.createAnswer(object : SdpObserver {
            override fun onCreateSuccess(desc: SessionDescription?) {
                if (desc != null) {
                    logger.info("WebRTC", "Answer SDP created successfully (${desc.description.lines().filter { it.startsWith("m=") || it.startsWith("a=send") || it.startsWith("a=recv") }.joinToString("; ")})")
                    continuation.resume(desc)
                } else {
                    continuation.resumeWithException(Exception("SDP description was null"))
                }
            }

            override fun onSetSuccess() {}
            override fun onCreateFailure(error: String?) {
                logger.error("WebRTC", "Create Answer Failed: $error")
                continuation.resumeWithException(Exception("Create Answer Failed: $error"))
            }
            override fun onSetFailure(error: String?) {}
        }, constraints)
    }

    suspend fun setLocalDescription(desc: SessionDescription): Unit = suspendCancellableCoroutine { continuation ->
        peerConnection?.setLocalDescription(object : SdpObserver {
            override fun onCreateSuccess(p0: SessionDescription?) {}
            override fun onSetSuccess() {
                continuation.resume(Unit)
            }
            override fun onCreateFailure(p0: String?) {}
            override fun onSetFailure(error: String?) {
                continuation.resumeWithException(Exception("Set Local Description Failed: $error"))
            }
        }, desc)
    }

    suspend fun setRemoteDescription(desc: SessionDescription): Unit = suspendCancellableCoroutine { continuation ->
        peerConnection?.setRemoteDescription(object : SdpObserver {
            override fun onCreateSuccess(p0: SessionDescription?) {}
            override fun onSetSuccess() {
                drainPendingCandidates()
                continuation.resume(Unit)
            }
            override fun onCreateFailure(p0: String?) {}
            override fun onSetFailure(error: String?) {
                continuation.resumeWithException(Exception("Set Remote Description Failed: $error"))
            }
        }, desc)
    }

    fun addIceCandidate(candidate: IceCandidate) {
        synchronized(pendingIceCandidates) {
            if (isRemoteDescriptionSet && peerConnection?.remoteDescription != null) {
                peerConnection?.addIceCandidate(candidate)
            } else {
                pendingIceCandidates.add(candidate)
                Log.d("WebRtcClient", "Queued ICE Candidate (Remote description pending)")
            }
        }
    }

    private fun drainPendingCandidates() {
        synchronized(pendingIceCandidates) {
            isRemoteDescriptionSet = true
            Log.d("WebRtcClient", "Draining ${pendingIceCandidates.size} queued ICE candidates")
            for (candidate in pendingIceCandidates) {
                peerConnection?.addIceCandidate(candidate)
            }
            pendingIceCandidates.clear()
        }
    }

    fun toggleAudio(enabled: Boolean) {
        localAudioTrack?.setEnabled(enabled)
    }

    fun toggleVideo(enabled: Boolean) {
        localVideoTrack?.setEnabled(enabled)
    }

    fun switchCamera() {
        videoCapturer?.switchCamera(null)
    }

    fun close() {
        synchronized(pendingIceCandidates) {
            pendingIceCandidates.clear()
            isRemoteDescriptionSet = false
        }

        try {
            videoCapturer?.stopCapture()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        videoCapturer?.dispose()
        videoCapturer = null

        surfaceTextureHelper?.dispose()
        surfaceTextureHelper = null

        localVideoTrack?.dispose()
        localVideoTrack = null

        localVideoSource?.dispose()
        localVideoSource = null

        localAudioTrack?.dispose()
        localAudioTrack = null

        localAudioSource?.dispose()
        localAudioSource = null

        remoteVideoTrack = null
        remoteAudioTrack = null

        peerConnection?.close()
        peerConnection?.dispose()
        peerConnection = null
    }
}
