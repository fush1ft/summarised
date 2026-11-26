package io.github.fush1ft.summarised.lab.capture

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import io.github.fush1ft.summarised.lab.log.EventLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Single source of truth for "are we recording, and where are the bookmarks".
 * Lives in the app process; the assistant session, the notification, the tile
 * and the UI all go through it.
 */
object CaptureController {
    /** A hold is ~0.5–1.5 s long; the moment the user reacted is a bit before it registered. */
    const val BOOKMARK_LEAD_MS = 1500L
    private const val DEBOUNCE_MS = 2000L

    enum class Phase { IDLE, STARTING, RECORDING, FAILED }

    data class State(
        val phase: Phase = Phase.IDLE,
        val startedAtElapsed: Long = 0L,
        val bookmarksMs: List<Long> = emptyList(),
        val error: String? = null,
        val silenced: Boolean = false,
    ) {
        fun elapsedMs(): Long =
            if (phase == Phase.RECORDING) SystemClock.elapsedRealtime() - startedAtElapsed else 0L
    }

    sealed interface Outcome {
        data object Starting : Outcome
        data class Bookmarked(val index: Int, val atMs: Long) : Outcome
        data object Debounced : Outcome
        data class Failed(val reason: String) : Outcome
    }

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state
    private var lastTrigger = 0L

    /**
     * The power-button hold (or any other trigger): start when idle, bookmark
     * when recording. Never stops — a stray press must not end a lecture.
     */
    @Synchronized
    fun onTrigger(context: Context, source: String, startViaActivity: ((Intent) -> Unit)? = null): Outcome {
        val now = SystemClock.elapsedRealtime()
        if (now - lastTrigger < DEBOUNCE_MS) {
            EventLog.add("TRIGGER", "$source: ignored (debounce)")
            return Outcome.Debounced
        }
        lastTrigger = now
        return when (_state.value.phase) {
            Phase.RECORDING -> addBookmark(source)
            Phase.STARTING -> Outcome.Debounced
            Phase.IDLE, Phase.FAILED -> start(context, source, startViaActivity)
        }
    }

    @Synchronized
    fun addBookmark(source: String): Outcome {
        val s = _state.value
        if (s.phase != Phase.RECORDING) return Outcome.Failed("not recording")
        val at = (s.elapsedMs() - BOOKMARK_LEAD_MS).coerceAtLeast(0L)
        _state.value = s.copy(bookmarksMs = s.bookmarksMs + at)
        val index = _state.value.bookmarksMs.size
        EventLog.add("BOOKMARK", "#$index at ${formatMs(at)} via $source")
        return Outcome.Bookmarked(index, at)
    }

    @Synchronized
    fun undoLastBookmark(source: String) {
        val s = _state.value
        if (s.bookmarksMs.isEmpty()) return
        _state.value = s.copy(bookmarksMs = s.bookmarksMs.dropLast(1))
        EventLog.add("BOOKMARK", "undo #${s.bookmarksMs.size} via $source")
    }

    private fun start(context: Context, source: String, startViaActivity: ((Intent) -> Unit)?): Outcome {
        val mode = Settings.startMode(context)
        _state.value = State(phase = Phase.STARTING)
        EventLog.add("TRIGGER", "$source: start requested (mode=${mode.name})")
        return try {
            if (mode == Settings.StartMode.TRAMPOLINE && startViaActivity != null) {
                startViaActivity(
                    Intent(context, io.github.fush1ft.summarised.lab.assist.AssistTrampolineActivity::class.java)
                        .putExtra("source", source),
                )
            } else {
                startService(context, source)
            }
            Outcome.Starting
        } catch (e: Exception) {
            val reason = "${e.javaClass.simpleName}: ${e.message}"
            fail(reason)
            Outcome.Failed(reason)
        }
    }

    /** Throws if Android refuses to start a foreground service from here. */
    fun startService(context: Context, source: String) {
        val intent = Intent(context, RecordingService::class.java)
            .setAction(RecordingService.ACTION_START)
            .putExtra("source", source)
        context.startForegroundService(intent)
        EventLog.add("FGS", "startForegroundService() accepted ($source)")
    }

    fun stop(context: Context, source: String) {
        EventLog.add("TRIGGER", "$source: stop requested")
        context.startService(Intent(context, RecordingService::class.java).setAction(RecordingService.ACTION_STOP))
    }

    // Called by RecordingService.
    @Synchronized
    internal fun recordingStarted(startedAtElapsed: Long) {
        _state.value = State(phase = Phase.RECORDING, startedAtElapsed = startedAtElapsed)
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
        EventLog.add("ERROR", reason)
        _state.value = State(phase = Phase.FAILED, error = reason)
    }

    fun formatMs(ms: Long): String {
        val s = ms / 1000
        return "%d:%02d:%02d".format(s / 3600, (s / 60) % 60, s % 60)
    }
}
