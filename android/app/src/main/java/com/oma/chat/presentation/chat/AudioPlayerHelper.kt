package com.oma.chat.presentation.chat

import android.content.Context
import android.media.MediaPlayer
import android.util.Base64
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

class AudioPlayerHelper(private val context: Context) {
    private var mediaPlayer: MediaPlayer? = null
    var currentlyPlayingMessageId: String? = null
        private set

    val currentPositionMs = kotlinx.coroutines.flow.MutableStateFlow(0)
    private var progressJob: kotlinx.coroutines.Job? = null
    private val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main + kotlinx.coroutines.SupervisorJob())

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
                    progressJob?.cancel()
                    currentPositionMs.value = 0
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

            progressJob?.cancel()
            progressJob = scope.launch {
                while (currentlyPlayingMessageId == messageId && mediaPlayer?.isPlaying == true) {
                    try {
                        currentPositionMs.value = mediaPlayer?.currentPosition ?: 0
                    } catch (e: Exception) {
                        break
                    }
                    kotlinx.coroutines.delay(100)
                }
            }
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
        progressJob?.cancel()
        currentPositionMs.value = 0
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

    companion object {
        private val durationCache = java.util.concurrent.ConcurrentHashMap<String, String>()

        fun extractDurationFormatted(context: Context, dataUriOrBase64: String): String {
            val cached = durationCache[dataUriOrBase64]
            if (cached != null) return cached

            return try {
                val cleanBase64 = if (dataUriOrBase64.contains(",")) {
                    dataUriOrBase64.substringAfter(",")
                } else {
                    dataUriOrBase64
                }
                val decodedBytes = Base64.decode(cleanBase64, Base64.DEFAULT)
                val tempFile = File.createTempFile("meta_", ".m4a", context.cacheDir)
                FileOutputStream(tempFile).use { it.write(decodedBytes) }

                val retriever = android.media.MediaMetadataRetriever()
                retriever.setDataSource(tempFile.absolutePath)
                val durationStr = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)
                retriever.release()
                tempFile.delete()

                val durationMs = durationStr?.toLongOrNull() ?: 0L
                val totalSec = (durationMs / 1000).toInt()
                val mins = totalSec / 60
                val secs = totalSec % 60
                val formatted = String.format(java.util.Locale.getDefault(), "%d:%02d", mins, secs)
                durationCache[dataUriOrBase64] = formatted
                formatted
            } catch (e: Exception) {
                "0:05"
            }
        }
    }
}
