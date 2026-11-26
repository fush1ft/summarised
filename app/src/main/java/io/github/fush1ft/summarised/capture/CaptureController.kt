package io.github.fush1ft.summarised.capture

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import io.github.fush1ft.summarised.core.database.BookmarkEntity
import io.github.fush1ft.summarised.core.database.SummarisedDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Single source of truth for "are we recording, and where are the bookmarks".
 * The power-button session, the notification and the UI all go through it.
 */
object CaptureController {
    /** A hold takes ~1–1.5 s; the moment worth marking came just before it registered. */
    const val BOOKMARK_LEAD_MS = 1500L
    private const val DEBOUNCE_MS = 2000L

    enum class Phase { IDLE, STARTING, RECORDING, FAILED }

    data class Mark(val id: Long, val atMs: Long)

    data class State(
        val phase: Phase = Phase.IDLE,
        val recordingId: Long = 0,
        val startedAtElapsed: Long = 0,
        val bookmarks: List<Mark> = emptyList(),
        val error: String? = null,
        val silenced: Boolean = false,
    ) {
        fun elapsedMs(): Long = if (phase == Phase.RECORDING) SystemClock.elapsedRealtime() - startedAtElapsed else 0
    }

    sealed interface Outcome {
        data object Starting : Outcome
        data class Bookmarked(val index: Int, val atMs: Long) : Outcome
        data object Debounced : Outcome
        data class Failed(val reason: String) : Outcome
    }

    private val io = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state
    private var lastTrigger = 0L

    /** Start when idle, bookmark when recording. A hold never stops a recording. */
    @Synchronized
    fun onTrigger(context: Context, source: String): Outcome {
        val now = SystemClock.elapsedRealtime()
        if (now - lastTrigger < DEBOUNCE_MS) return Outcome.Debounced
        lastTrigger = now
        return when (_state.value.phase) {
            Phase.RECORDING -> addBookmark(context, source)
            Phase.STARTING -> Outcome.Debounced
            Phase.IDLE, Phase.FAILED -> start(context, source)
        }
    }

    @Synchronized
    fun addBookmark(context: Context, source: String): Outcome {
        val s = _state.value
        if (s.phase != Phase.RECORDING) return Outcome.Failed("nenahrává se")
        val at = (s.elapsedMs() - BOOKMARK_LEAD_MS).coerceAtLeast(0)
        val placeholder = Mark(id = -1, atMs = at)
        _state.value = s.copy(bookmarks = s.bookmarks + placeholder)
        val index = _state.value.bookmarks.size
        val db = SummarisedDatabase.get(context)
        val recordingId = s.recordingId
        io.launch {
            val id = db.bookmarkDao().insert(
                BookmarkEntity(recordingId = recordingId, atMs = at, createdVia = source, createdAt = System.currentTimeMillis()),
            )
            replacePlaceholder(placeholder, id)
            // Summarised once the transcript covers the two minutes after it.
            io.github.fush1ft.summarised.summarise.SummaryManager.queueBookmark(context, id, 30)
        }
        return Outcome.Bookmarked(index, at)
    }

    @Synchronized
    private fun replacePlaceholder(placeholder: Mark, id: Long) {
        val s = _state.value
        _state.value = s.copy(bookmarks = s.bookmarks.map { if (it === placeholder) Mark(id, it.atMs) else it })
    }

    @Synchronized
    fun undoLastBookmark(context: Context) {
        val s = _state.value
        val last = s.bookmarks.lastOrNull() ?: return
        _state.value = s.copy(bookmarks = s.bookmarks.dropLast(1))
        if (last.id > 0) {
            val db = SummarisedDatabase.get(context)
            io.launch { db.bookmarkDao().delete(last.id) }
        }
    }

    private fun start(context: Context, source: String): Outcome {
        _state.value = State(phase = Phase.STARTING)
        return try {
            context.startForegroundService(
                Intent(context, RecordingService::class.java).setAction(RecordingService.ACTION_START).putExtra("source", source),
            )
            Outcome.Starting
        } catch (e: Exception) {
            val reason = "${e.javaClass.simpleName}: ${e.message}"
            fail(reason)
            Outcome.Failed(reason)
        }
    }

    fun stop(context: Context) {
        context.startService(Intent(context, RecordingService::class.java).setAction(RecordingService.ACTION_STOP))
    }

    // Called by RecordingService.
    @Synchronized
    internal fun recordingStarted(recordingId: Long, startedAtElapsed: Long) {
        _state.value = State(phase = Phase.RECORDING, recordingId = recordingId, startedAtElapsed = startedAtElapsed)
    }

    @Synchronized
    internal fun setSilenced(silenced: Boolean) {
        _state.value = _state.value.copy(silenced = silenced)
    }

    @Synchronized
    internal fun recordingStopped() {
        _state.value = State(phase = Phase.IDLE)
    }

    @Synchronized
    internal fun fail(reason: String) {
        android.util.Log.w("summarised", "capture failed: $reason")
        _state.value = State(phase = Phase.FAILED, error = reason)
    }
}

/** "1:02:03" / "2:03". */
fun formatDuration(ms: Long): String {
    val s = ms / 1000
    val h = s / 3600
    return if (h > 0) "%d:%02d:%02d".format(h, (s / 60) % 60, s % 60) else "%d:%02d".format(s / 60, s % 60)
}
