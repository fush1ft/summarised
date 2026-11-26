package io.github.fush1ft.summarised.lab.log

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONObject
import java.io.File

/** Benchmark and capture results, one JSON object per line, kept across restarts. */
object Results {
    private lateinit var file: File
    private val _all = MutableStateFlow<List<JSONObject>>(emptyList())
    val all: StateFlow<List<JSONObject>> = _all

    fun init(context: Context) {
        file = File(context.filesDir, "results.jsonl")
        if (file.exists()) {
            _all.value = file.readLines().mapNotNull { runCatching { JSONObject(it) }.getOrNull() }
        }
    }

    @Synchronized
    fun add(kind: String, data: JSONObject) {
        data.put("kind", kind)
        data.put("at", System.currentTimeMillis())
        data.put("uid", java.util.UUID.randomUUID().toString())
        _all.value = _all.value + data
        runCatching { file.appendText(data.toString() + "\n") }
    }

    /** Changes the newest result matching [match] (e.g. to attach a rating). */
    @Synchronized
    fun update(match: (JSONObject) -> Boolean, change: (JSONObject) -> Unit) {
        val idx = _all.value.indexOfLast(match)
        if (idx < 0) return
        // Swap in a copy: StateFlow drops emissions equal to the current list.
        val copy = JSONObject(_all.value[idx].toString())
        change(copy)
        _all.value = _all.value.toMutableList().also { it[idx] = copy }
        runCatching { file.writeText(_all.value.joinToString("\n", postfix = "\n") { it.toString() }) }
    }

    @Synchronized
    fun clear() {
        _all.value = emptyList()
        runCatching { file.writeText("") }
    }
}
