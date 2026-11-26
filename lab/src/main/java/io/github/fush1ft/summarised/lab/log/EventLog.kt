package io.github.fush1ft.summarised.lab.log

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Append-only event log, persisted to a file so it survives process death —
 * the trigger tests are exactly the situations where the process may be killed.
 */
object EventLog {
    private const val MAX_IN_MEMORY = 400
    private lateinit var file: File
    private val format = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
    private val _lines = MutableStateFlow<List<String>>(emptyList())
    val lines: StateFlow<List<String>> = _lines

    fun init(context: Context) {
        file = File(context.filesDir, "events.log")
        if (file.exists()) _lines.value = file.readLines().takeLast(MAX_IN_MEMORY)
    }

    @Synchronized
    fun add(tag: String, message: String) {
        val line = "${format.format(Date())} [$tag] $message"
        android.util.Log.i("summarised-lab", line)
        _lines.value = (_lines.value + line).takeLast(MAX_IN_MEMORY)
        runCatching { file.appendText(line + "\n") }
    }

    @Synchronized
    fun clear() {
        _lines.value = emptyList()
        runCatching { file.writeText("") }
    }
}
