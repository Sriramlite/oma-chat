package com.oma.chat.data.diagnostics

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

enum class LogLevel {
    INFO, SUCCESS, WARN, ERROR
}

data class DiagnosticLogEntry(
    val id: Long = System.nanoTime(),
    val timestamp: String = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date()),
    val tag: String,
    val message: String,
    val level: LogLevel
)

@Singleton
class DiagnosticsLogger @Inject constructor() {

    private val _logs = MutableStateFlow<List<DiagnosticLogEntry>>(emptyList())
    val logs: StateFlow<List<DiagnosticLogEntry>> = _logs.asStateFlow()

    fun log(tag: String, message: String, level: LogLevel = LogLevel.INFO) {
        val entry = DiagnosticLogEntry(tag = tag, message = message, level = level)
        android.util.Log.println(
            when (level) {
                LogLevel.ERROR -> android.util.Log.ERROR
                LogLevel.WARN -> android.util.Log.WARN
                LogLevel.SUCCESS, LogLevel.INFO -> android.util.Log.INFO
            },
            "OMA_$tag",
            message
        )
        _logs.update { current ->
            (current + entry).takeLast(500) // Keep last 500 entries
        }
    }

    fun info(tag: String, message: String) = log(tag, message, LogLevel.INFO)
    fun success(tag: String, message: String) = log(tag, message, LogLevel.SUCCESS)
    fun warn(tag: String, message: String) = log(tag, message, LogLevel.WARN)
    fun error(tag: String, message: String) = log(tag, message, LogLevel.ERROR)

    fun clear() {
        _logs.value = emptyList()
    }

    fun getFullLogText(): String {
        return _logs.value.joinToString("\n") { "[${it.timestamp}] [${it.level.name}] [${it.tag}] ${it.message}" }
    }
}
