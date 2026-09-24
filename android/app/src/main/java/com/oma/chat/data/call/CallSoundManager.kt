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
    private var timeoutJob: Job? = null

    private val voiceAudioAttributes = AudioAttributes.Builder()
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
        .build()

    private val ringAudioAttributes = AudioAttributes.Builder()
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION_SIGNALLING)
        .build()

    /**
     * Plays outgoing call dialing tone (outgoingcall.mp3) in a loop.
     */
    @Synchronized
    fun playOutgoingCall() {
        stopAll()
        try {
            mediaPlayer = MediaPlayer.create(context, R.raw.outgoingcall)?.apply {
                setAudioAttributes(ringAudioAttributes)
                isLooping = true
                start()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Plays busy tone (busytone.mp3) at the end of an unanswered, rejected, or unreachable call.
     */
    @Synchronized
    fun playBusyTone(durationMs: Long = 3500L, onFinish: (() -> Unit)? = null) {
        stopAll()
        try {
            val player = MediaPlayer.create(context, R.raw.busytone)?.apply {
                setAudioAttributes(ringAudioAttributes)
                isLooping = false
                setOnCompletionListener {
                    stopAll()
                    onFinish?.invoke()
                }
                start()
            }
            mediaPlayer = player

            if (player == null) {
                onFinish?.invoke()
                return
            }

            timeoutJob = scope.launch {
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
            val player = MediaPlayer.create(context, R.raw.callrejected)?.apply {
                setAudioAttributes(voiceAudioAttributes)
                isLooping = false
                setOnCompletionListener {
                    playBusyTone(durationMs = 3500L, onFinish = onFinish)
                }
                start()
            }
            mediaPlayer = player

            if (player == null) {
                playBusyTone(durationMs = 3500L, onFinish = onFinish)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            playBusyTone(durationMs = 3500L, onFinish = onFinish)
        }
    }

    /**
     * Plays notreachable_offline.mp3 followed by busytone.mp3 when the user is offline/unreachable.
     */
    @Synchronized
    fun playUnreachableSequence(onFinish: (() -> Unit)? = null) {
        stopAll()
        try {
            val player = MediaPlayer.create(context, R.raw.notreachable_offline)?.apply {
                setAudioAttributes(voiceAudioAttributes)
                isLooping = false
                setOnCompletionListener {
                    playBusyTone(durationMs = 3500L, onFinish = onFinish)
                }
                start()
            }
            mediaPlayer = player

            if (player == null) {
                playBusyTone(durationMs = 3500L, onFinish = onFinish)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            playBusyTone(durationMs = 3500L, onFinish = onFinish)
        }
    }

    /**
     * Stops and releases any currently active media player.
     */
    @Synchronized
    fun stopAll() {
        timeoutJob?.cancel()
        timeoutJob = null
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
