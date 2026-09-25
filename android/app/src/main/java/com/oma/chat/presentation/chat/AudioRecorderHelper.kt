package com.oma.chat.presentation.chat

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.util.Base64
import java.io.File

class AudioRecorderHelper(private val context: Context) {
    private var mediaRecorder: MediaRecorder? = null
    private var currentOutputFile: File? = null
    var isRecording: Boolean = false
        private set

    fun startRecording(): Boolean {
        return try {
            val cacheDir = context.cacheDir
            val audioFile = File(cacheDir, "voice_record_${System.currentTimeMillis()}.m4a")
            currentOutputFile = audioFile

            val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }

            recorder.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(128000)
                setAudioSamplingRate(44100)
                setOutputFile(audioFile.absolutePath)
                prepare()
                start()
            }

            mediaRecorder = recorder
            isRecording = true
            true
        } catch (e: Exception) {
            e.printStackTrace()
            cancelRecording()
            false
        }
    }

    fun stopAndGetBase64(): String? {
        if (!isRecording || mediaRecorder == null) return null
        return try {
            mediaRecorder?.apply {
                stop()
                release()
            }
            mediaRecorder = null
            isRecording = false

            val file = currentOutputFile
            if (file != null && file.exists() && file.length() > 0) {
                val bytes = file.readBytes()
                val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                file.delete()
                currentOutputFile = null
                "data:audio/mp4;base64,$base64"
            } else {
                currentOutputFile?.delete()
                currentOutputFile = null
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            cancelRecording()
            null
        }
    }

    fun cancelRecording() {
        try {
            mediaRecorder?.apply {
                stop()
                release()
            }
        } catch (e: Exception) {
            // ignore
        } finally {
            mediaRecorder = null
            isRecording = false
            currentOutputFile?.delete()
            currentOutputFile = null
        }
    }
}
