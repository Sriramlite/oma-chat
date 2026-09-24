package com.oma.chat.data.call

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import com.oma.chat.R
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CallSoundManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var mediaPlayer: MediaPlayer? = null
    private val scope = CoroutineScope(Dispatchers.Main)
    private var fallbackTimeoutJob: Job? = null

    private val ringAudioAttributes = AudioAttributes.Builder()
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION_SIGNALLING)
        .build()

    private val voiceAudioAttributes = AudioAttributes.Builder()
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
        .build()

    private fun createPlayer(rawResId: Int, audioAttributes: AudioAttributes): MediaPlayer? {
        return try {
            val afd = context.resources.openRawResourceFd(rawResId) ?: return null
            MediaPlayer().apply {
                setAudioAttributes(audioAttributes)
                setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                afd.close()
                prepare()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Plays outgoing call dialing tone (outgoingcall.mp3) in a loop.
     */
    @Synchronized
    fun playOutgoingCall() {
        stopAll()
        try {
            val player = createPlayer(R.raw.outgoingcall, ringAudioAttributes)
            if (player != null) {
                player.isLooping = true
                player.start()
                mediaPlayer = player
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Plays busy tone (busytone.mp3) and calls onFinish when the audio finishes playing.
     */
    @Synchronized
    fun playBusyTone(durationMs: Long = 4000L, onFinish: (() -> Unit)? = null) {
        stopAll()
        try {
            val player = createPlayer(R.raw.busytone, ringAudioAttributes)
            if (player == null) {
                onFinish?.invoke()
                return
            }

            mediaPlayer = player
            player.isLooping = false
            player.setOnCompletionListener {
                stopAll()
                onFinish?.invoke()
            }
            player.start()

            // Safety timeout in case completion listener doesn't fire
            fallbackTimeoutJob = scope.launch {
                delay(durationMs)
                stopAll()
                onFinish?.invoke()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            onFinish?.invoke()
        }
    }

    /**
     * Plays callrejected.mp3 followed by busytone.mp3 when a call is rejected/declined.
     */
    @Synchronized
    fun playCallRejectedSequence(onFinish: (() -> Unit)? = null) {
        stopAll()
        try {
            val player = createPlayer(R.raw.callrejected, voiceAudioAttributes)
            if (player == null) {
                playBusyTone(durationMs = 4000L, onFinish = onFinish)
                return
            }

            mediaPlayer = player
            player.isLooping = false
            player.setOnCompletionListener {
                stopAll()
                playBusyTone(durationMs = 4000L, onFinish = onFinish)
            }
            player.start()

            // Fallback timeout in case rejected audio stalls
            fallbackTimeoutJob = scope.launch {
                delay(8000L)
                playBusyTone(durationMs = 4000L, onFinish = onFinish)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            playBusyTone(durationMs = 4000L, onFinish = onFinish)
        }
    }

    /**
     * Plays notreachable_offline.mp3 followed by busytone.mp3 when the user is offline/unreachable.
     */
    @Synchronized
    fun playUnreachableSequence(onFinish: (() -> Unit)? = null) {
        stopAll()
        try {
            val player = createPlayer(R.raw.notreachable_offline, voiceAudioAttributes)
            if (player == null) {
                playBusyTone(durationMs = 4000L, onFinish = onFinish)
                return
            }

            mediaPlayer = player
            player.isLooping = false
            player.setOnCompletionListener {
                stopAll()
                playBusyTone(durationMs = 4000L, onFinish = onFinish)
            }
            player.start()

            // Fallback timeout in case unreachable audio stalls
            fallbackTimeoutJob = scope.launch {
                delay(8000L)
                playBusyTone(durationMs = 4000L, onFinish = onFinish)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            playBusyTone(durationMs = 4000L, onFinish = onFinish)
        }
    }

    /**
     * Stops and releases any currently active media player.
     */
    @Synchronized
    fun stopAll() {
        fallbackTimeoutJob?.cancel()
        fallbackTimeoutJob = null
        try {
            mediaPlayer?.let { player ->
                if (player.isPlaying) {
                    player.stop()
                }
                player.release()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            mediaPlayer = null
        }
    }
}
