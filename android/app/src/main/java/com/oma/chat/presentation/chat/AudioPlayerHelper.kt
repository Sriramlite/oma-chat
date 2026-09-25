package com.oma.chat.presentation.chat

import android.content.Context
import android.media.MediaPlayer
import android.util.Base64
import java.io.File
import java.io.FileOutputStream

class AudioPlayerHelper(private val context: Context) {
    private var mediaPlayer: MediaPlayer? = null
    var currentlyPlayingMessageId: String? = null
        private set

    fun playBase64Audio(
        messageId: String,
        dataUriOrBase64: String,
        onComplete: () -> Unit
    ) {
        stopAudio()
        try {
            val base64Data = if (dataUriOrBase64.contains(",")) {
                dataUriOrBase64.substringAfter(",")
            } else {
                dataUriOrBase64
            }

            val decodedBytes = Base64.decode(base64Data, Base64.DEFAULT)
            val tempAudioFile = File.createTempFile("playback_", ".m4a", context.cacheDir)
            tempAudioFile.deleteOnExit()
            FileOutputStream(tempAudioFile).use { it.write(decodedBytes) }

            val player = MediaPlayer().apply {
                setDataSource(tempAudioFile.absolutePath)
                prepare()
                setOnCompletionListener {
                    currentlyPlayingMessageId = null
                    try {
                        it.release()
                    } catch (e: Exception) {
                        // ignore
                    }
                    tempAudioFile.delete()
                    onComplete()
                }
                start()
            }
            mediaPlayer = player
            currentlyPlayingMessageId = messageId
        } catch (e: Exception) {
            e.printStackTrace()
            stopAudio()
            onComplete()
        }
    }

    fun isPlaying(messageId: String): Boolean {
        return currentlyPlayingMessageId == messageId && mediaPlayer?.isPlaying == true
    }

    fun stopAudio() {
        try {
            mediaPlayer?.apply {
                if (isPlaying) stop()
                release()
            }
        } catch (e: Exception) {
            // ignore
        } finally {
            mediaPlayer = null
            currentlyPlayingMessageId = null
        }
    }
}
