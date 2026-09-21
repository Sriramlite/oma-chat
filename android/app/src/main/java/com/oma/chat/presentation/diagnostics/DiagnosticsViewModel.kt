package com.oma.chat.presentation.diagnostics

import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.oma.chat.data.diagnostics.DiagnosticLogEntry
import com.oma.chat.data.diagnostics.DiagnosticsLogger
import com.oma.chat.data.diagnostics.LogLevel
import com.oma.chat.data.remote.socket.SocketManager
import com.oma.chat.data.webrtc.WebRtcClient
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.webrtc.Camera2Enumerator
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.PeerConnection
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import javax.inject.Inject
import kotlin.math.sin
import kotlin.math.sqrt

data class DiagnosticsUiState(
    val isMicTesting: Boolean = false,
    val micAmplitude: Float = 0f, // 0.0 to 1.0
    val micDecibels: Float = 0f, // 0 to 90 dB
    val micSourceUsed: String = "",
    val isSpeakerTesting: Boolean = false,
    val isWebRtcTesting: Boolean = false,
    val isSocketTesting: Boolean = false,
    val permissionsGranted: Map<String, Boolean> = emptyMap()
)

@HiltViewModel
class DiagnosticsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    val logger: DiagnosticsLogger,
    private val webRtcClient: WebRtcClient,
    private val socketManager: SocketManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(DiagnosticsUiState())
    val uiState: StateFlow<DiagnosticsUiState> = _uiState.asStateFlow()

    val logs: StateFlow<List<DiagnosticLogEntry>> = logger.logs

    private var micTestJob: Job? = null
    private var speakerTestJob: Job? = null

    init {
        checkPermissions()
        logger.info("System", "Diagnostics Console Initialized. Ready for testing.")
    }

    fun checkPermissions() {
        val perms = mapOf(
            "RECORD_AUDIO" to (ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED),
            "CAMERA" to (ContextCompat.checkSelfPermission(context, android.Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED),
            "MODIFY_AUDIO_SETTINGS" to (ContextCompat.checkSelfPermission(context, android.Manifest.permission.MODIFY_AUDIO_SETTINGS) == PackageManager.PERMISSION_GRANTED)
        )
        _uiState.update { it.copy(permissionsGranted = perms) }
        perms.forEach { (perm, granted) ->
            if (granted) {
                logger.success("Permission", "$perm is GRANTED")
            } else {
                logger.error("Permission", "$perm is DENIED (Must grant to test calls)")
            }
        }
    }

    /**
     * Live Hardware AudioRecord Microphone Test
     * Reads raw PCM audio frames from the hardware microphone and computes RMS Amplitude + Decibels.
     */
    fun toggleMicrophoneTest(audioSource: Int = MediaRecorder.AudioSource.VOICE_COMMUNICATION) {
        if (_uiState.value.isMicTesting) {
            stopMicrophoneTest()
        } else {
            startMicrophoneTest(audioSource)
        }
    }

    private fun startMicrophoneTest(sourceType: Int) {
        val hasAudio = ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (!hasAudio) {
            logger.error("MicTest", "Cannot start mic test: RECORD_AUDIO permission not granted!")
            return
        }

        val sourceName = if (sourceType == MediaRecorder.AudioSource.VOICE_COMMUNICATION) "VOICE_COMMUNICATION" else "MIC"
        logger.info("MicTest", "Initializing hardware AudioRecord test using source: $sourceName")

        _uiState.update { it.copy(isMicTesting = true, micSourceUsed = sourceName) }

        micTestJob?.cancel()
        micTestJob = viewModelScope.launch(Dispatchers.IO) {
            val sampleRate = 48000
            val channelConfig = AudioFormat.CHANNEL_IN_MONO
            val audioFormat = AudioFormat.ENCODING_PCM_16BIT

            val minBufSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
            if (minBufSize <= 0) {
                logger.error("MicTest", "Invalid AudioRecord buffer size: $minBufSize")
                _uiState.update { it.copy(isMicTesting = false) }
                return@launch
            }

            var audioRecord: AudioRecord? = null
            try {
                audioRecord = AudioRecord(
                    sourceType,
                    sampleRate,
                    channelConfig,
                    audioFormat,
                    minBufSize * 2
                )

                if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
                    logger.error("MicTest", "AudioRecord state NOT INITIALIZED! (Code: ${audioRecord.state})")
                    _uiState.update { it.copy(isMicTesting = false) }
                    return@launch
                }

                logger.success("MicTest", "AudioRecord initialized successfully (SampleRate: ${sampleRate}Hz, Buffer: ${minBufSize * 2} bytes)")
                audioRecord.startRecording()
                logger.info("MicTest", "AudioRecord is now recording. Speak into microphone...")

                val buffer = ShortArray(minBufSize)
                var zeroFramesCount = 0

                while (isActive) {
                    val readSamples = audioRecord.read(buffer, 0, buffer.size)
                    if (readSamples > 0) {
                        // Calculate RMS Amplitude
                        var sumSq = 0.0
                        var maxPeak = 0
                        for (i in 0 until readSamples) {
                            val sample = buffer[i].toInt()
                            sumSq += (sample * sample)
                            val abs = kotlin.math.abs(sample)
                            if (abs > maxPeak) maxPeak = abs
                        }
                        val rms = sqrt(sumSq / readSamples)
                        val normAmp = (rms / 32767.0).coerceIn(0.0, 1.0).toFloat()
                        val db = (20 * kotlin.math.log10(rms.coerceAtLeast(1.0))).toFloat()

                        if (maxPeak == 0) {
                            zeroFramesCount++
                            if (zeroFramesCount % 30 == 0) {
                                logger.warn("MicTest", "Warning: 0-amplitude audio frames detected (Silent stream). Max peak: 0")
                            }
                        } else {
                            zeroFramesCount = 0
                        }

                        _uiState.update {
                            it.copy(
                                micAmplitude = normAmp,
                                micDecibels = db
                            )
                        }
                    } else if (readSamples < 0) {
                        val errorStr = when (readSamples) {
                            AudioRecord.ERROR_INVALID_OPERATION -> "ERROR_INVALID_OPERATION"
                            AudioRecord.ERROR_BAD_VALUE -> "ERROR_BAD_VALUE"
                            AudioRecord.ERROR_DEAD_OBJECT -> "ERROR_DEAD_OBJECT"
                            else -> "UNKNOWN_ERROR ($readSamples)"
                        }
                        logger.error("MicTest", "AudioRecord read error: $errorStr")
                    }
                    delay(40)
                }
            } catch (e: Exception) {
                logger.error("MicTest", "Exception in AudioRecord test: ${e.localizedMessage}")
            } finally {
                try {
                    audioRecord?.stop()
                    audioRecord?.release()
                    logger.info("MicTest", "AudioRecord released.")
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                _uiState.update { it.copy(isMicTesting = false, micAmplitude = 0f, micDecibels = 0f) }
            }
        }
    }

    fun stopMicrophoneTest() {
        micTestJob?.cancel()
        micTestJob = null
        _uiState.update { it.copy(isMicTesting = false, micAmplitude = 0f, micDecibels = 0f) }
        logger.info("MicTest", "Microphone test stopped by user.")
    }

    /**
     * Speaker Playback Test (Plays 440Hz Sine Beep Tone)
     */
    fun testSpeakerPlayback() {
        if (_uiState.value.isSpeakerTesting) return
        _uiState.update { it.copy(isSpeakerTesting = true) }
        logger.info("SpeakerTest", "Generating 440Hz test sine tone for AudioTrack playback...")

        speakerTestJob = viewModelScope.launch(Dispatchers.IO) {
            val sampleRate = 44100
            val durationMs = 1200
            val numSamples = (sampleRate * (durationMs / 1000.0)).toInt()
            val generatedSnd = ShortArray(numSamples)
            val freqOfTone = 440.0 // Hz

            for (i in 0 until numSamples) {
                val dVal = sin(2 * Math.PI * i / (sampleRate / freqOfTone))
                generatedSnd[i] = (dVal * 32767).toInt().toShort()
            }

            var track: AudioTrack? = null
            try {
                val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                audioManager.mode = AudioManager.MODE_NORMAL
                audioManager.isSpeakerphoneOn = true

                val attributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()

                val format = AudioFormat.Builder()
                    .setSampleRate(sampleRate)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()

                track = AudioTrack.Builder()
                    .setAudioAttributes(attributes)
                    .setAudioFormat(format)
                    .setBufferSizeInBytes(generatedSnd.size * 2)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()

                track.write(generatedSnd, 0, generatedSnd.size)
                track.play()
                logger.success("SpeakerTest", "AudioTrack playing 440Hz tone through device speaker.")

                delay(durationMs.toLong() + 200)
            } catch (e: Exception) {
                logger.error("SpeakerTest", "Speaker test error: ${e.localizedMessage}")
            } finally {
                try {
                    track?.stop()
                    track?.release()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                _uiState.update { it.copy(isSpeakerTesting = false) }
                logger.info("SpeakerTest", "Speaker playback test finished.")
            }
        }
    }

    /**
     * Camera Enumerator Test
     */
    fun testCameraCapturers() {
        logger.info("CameraTest", "Enumerating Camera2 devices and resolutions...")
        try {
            val enumerator = Camera2Enumerator(context)
            val deviceNames = enumerator.deviceNames
            logger.info("CameraTest", "Found ${deviceNames.size} camera devices: ${deviceNames.joinToString(", ")}")

            for (name in deviceNames) {
                val isFront = enumerator.isFrontFacing(name)
                val isBack = enumerator.isBackFacing(name)
                val formats = enumerator.getSupportedFormats(name)
                val formatSummary = formats?.take(3)?.joinToString { "${it.width}x${it.height}@${it.framerate.max / 1000}fps" } ?: "None"
                logger.success(
                    "CameraTest",
                    "Camera [$name] (Front: $isFront, Back: $isBack) Formats: $formatSummary (Total ${formats?.size ?: 0} modes)"
                )
            }
        } catch (e: Exception) {
            logger.error("CameraTest", "Camera enumeration error: ${e.localizedMessage}")
        }
    }

    /**
     * WebRTC STUN Connectivity & PeerConnection Test
     */
    fun testWebRtcPeerConnection() {
        if (_uiState.value.isWebRtcTesting) return
        _uiState.update { it.copy(isWebRtcTesting = true) }
        logger.info("WebRTCTest", "Initializing test PeerConnection with Google & Twilio STUN servers...")

        viewModelScope.launch(Dispatchers.IO) {
            var pc: PeerConnection? = null
            try {
                val factory = webRtcClient.ensurePeerConnectionFactory()
                val iceServers = listOf(
                    PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
                    PeerConnection.IceServer.builder("stun:global.stun.twilio.com:3478").createIceServer()
                )
                val rtcConfig = PeerConnection.RTCConfiguration(iceServers).apply {
                    sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
                    continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
                }

                pc = factory.createPeerConnection(
                    rtcConfig,
                    object : PeerConnection.Observer {
                        override fun onSignalingChange(state: PeerConnection.SignalingState?) {
                            logger.info("WebRTCTest", "Signaling State -> $state")
                        }
                        override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {
                            logger.info("WebRTCTest", "ICE Connection State -> $state")
                        }
                        override fun onIceConnectionReceivingChange(p0: Boolean) {}
                        override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) {
                            logger.info("WebRTCTest", "ICE Gathering State -> $state")
                            if (state == PeerConnection.IceGatheringState.COMPLETE) {
                                logger.success("WebRTCTest", "ICE Gathering COMPLETED successfully!")
                            }
                        }
                        override fun onIceCandidate(candidate: IceCandidate?) {
                            candidate?.let {
                                val type = if (it.sdp.contains("typ host")) "HOST" else if (it.sdp.contains("typ srflx")) "STUN/SRFLX" else "RELAY"
                                logger.success("WebRTCTest", "Found candidate [$type]: ${it.sdp.trim()}")
                            }
                        }
                        override fun onIceCandidatesRemoved(p0: Array<out IceCandidate>?) {}
                        override fun onAddStream(p0: org.webrtc.MediaStream?) {}
                        override fun onRemoveStream(p0: org.webrtc.MediaStream?) {}
                        override fun onDataChannel(p0: org.webrtc.DataChannel?) {}
                        override fun onRenegotiationNeeded() {}
                        override fun onAddTrack(p0: org.webrtc.RtpReceiver?, p1: Array<out org.webrtc.MediaStream>?) {}
                    }
                )

                // Add Audio Track to trigger SDP negotiation
                val audioSource = factory.createAudioSource(MediaConstraints())
                val audioTrack = factory.createAudioTrack("TEST_AUDIO_TRACK", audioSource)
                pc?.addTrack(audioTrack, listOf("TEST_STREAM"))
                logger.info("WebRTCTest", "Added local AudioTrack. Creating SDP Offer...")

                val constraints = MediaConstraints().apply {
                    mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
                }
                pc?.createOffer(object : SdpObserver {
                    override fun onCreateSuccess(desc: SessionDescription?) {
                        logger.success("WebRTCTest", "Offer SDP Created (${desc?.description?.length} chars). Setting LocalDescription...")
                        pc?.setLocalDescription(object : SdpObserver {
                            override fun onCreateSuccess(p0: SessionDescription?) {}
                            override fun onSetSuccess() {
                                logger.success("WebRTCTest", "LocalDescription set! ICE gathering active...")
                            }
                            override fun onCreateFailure(p0: String?) {}
                            override fun onSetFailure(error: String?) {
                                logger.error("WebRTCTest", "SetLocalDescription failed: $error")
                            }
                        }, desc)
                    }
                    override fun onSetSuccess() {}
                    override fun onCreateFailure(error: String?) {
                        logger.error("WebRTCTest", "CreateOffer failed: $error")
                    }
                    override fun onSetFailure(p0: String?) {}
                }, constraints)

                delay(5000)
            } catch (e: Exception) {
                logger.error("WebRTCTest", "WebRTC Test Exception: ${e.localizedMessage}")
            } finally {
                try {
                    pc?.close()
                    pc?.dispose()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                _uiState.update { it.copy(isWebRtcTesting = false) }
                logger.info("WebRTCTest", "WebRTC test completed.")
            }
        }
    }

    /**
     * Socket.IO Live Ping Test
     */
    fun testSocketConnection() {
        if (_uiState.value.isSocketTesting) return
        _uiState.update { it.copy(isSocketTesting = true) }
        logger.info("SocketTest", "Testing Socket.IO connection state to https://api.pdktdev.in...")

        viewModelScope.launch {
            val state = socketManager.connectionState.value
            val isConnected = state is com.oma.chat.data.remote.socket.SocketConnectionState.Connected
            if (isConnected) {
                logger.success("SocketTest", "Socket.IO is CONNECTED! Active online users count: ${socketManager.onlineUsers.value.size}")
            } else {
                logger.warn("SocketTest", "Socket.IO is currently: $state. Requesting reconnect...")
                socketManager.connect()
                delay(2000)
                val newState = socketManager.connectionState.value
                if (newState is com.oma.chat.data.remote.socket.SocketConnectionState.Connected) {
                    logger.success("SocketTest", "Socket.IO reconnected successfully!")
                } else {
                    logger.error("SocketTest", "Socket.IO connection state: $newState")
                }
            }
            _uiState.update { it.copy(isSocketTesting = false) }
        }
    }

    fun clearLogs() {
        logger.clear()
        logger.info("System", "Console logs cleared.")
    }
}
