package io.github.fush1ft.summarised.summarise

import android.content.Context
import io.github.fush1ft.summarised.capture.formatDuration
import io.github.fush1ft.summarised.core.database.RecordingStatus
import io.github.fush1ft.summarised.core.database.SegmentEntity
import io.github.fush1ft.summarised.core.database.SummarisedDatabase
import io.github.fush1ft.summarised.transcribe.TranscriptionManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Writes bookmark and whole-recording summaries. The database is the queue:
 * [kick] looks for bookmarks and recordings without a summary, so work left
 * over from a killed process resumes on the next start.
 */
object SummaryManager {
    /** Transcript around a bookmark: two minutes each way. */
    private const val WINDOW_MS = 120_000L
    /** Whole-recording summaries are sent in parts of at most this many characters. */
    private const val PART_CHARS = 10_000
    private const val RECHECK_S = 20L

    data class State(val working: String? = null, val errors: Map<String, String> = emptyMap())

    private val worker = Executors.newSingleThreadScheduledExecutor { r -> Thread(r, "summarised-summary") }
    private val queued = HashSet<String>()
    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state

    fun bookmarkKey(id: Long) = "b$id"
    fun recordingKey(id: Long) = "r$id"

    /** Finds all pending summary work and queues it. Safe to call often. */
    fun kick(context: Context) {
        val app = context.applicationContext
        worker.execute {
            val db = SummarisedDatabase.get(app)
            runBlocking { db.bookmarkDao().unsummarised() }.forEach { queueBookmark(app, it.id, 0) }
            runBlocking { db.recordingDao().needingSummary() }.forEach { queueRecording(app, it.id) }
        }
    }

    fun queueBookmark(context: Context, id: Long, delayS: Long = 0) {
        val app = context.applicationContext
        synchronized(queued) { if (!queued.add(bookmarkKey(id))) return }
        worker.schedule({ runBookmark(app, id) }, delayS, TimeUnit.SECONDS)
    }

    private fun queueRecording(context: Context, id: Long) {
        synchronized(queued) { if (!queued.add(recordingKey(id))) return }
        worker.execute { runRecording(context, id) }
    }

    /** Switch template, forget old summaries and write new ones. */
    fun resummarise(context: Context, recordingId: Long, template: String) {
        val app = context.applicationContext
        worker.execute {
            val db = SummarisedDatabase.get(app)
            runBlocking {
                db.recordingDao().setTemplate(recordingId, template)
                db.recordingDao().setSummary(recordingId, null, null)
                db.bookmarkDao().clearSummaries(recordingId)
            }
            clearErrorsFor(recordingId)
        }
        kick(app)
    }

    fun retry(context: Context, recordingId: Long) {
        clearErrorsFor(recordingId)
        kick(context)
    }

    // --- Bookmarks ---------------------------------------------------------------

    private fun runBookmark(context: Context, id: Long) {
        synchronized(queued) { queued.remove(bookmarkKey(id)) }
        val db = SummarisedDatabase.get(context)
        val b = runBlocking { db.bookmarkDao().get(id) } ?: return
        if (b.summaryJson != null || _state.value.errors.containsKey(bookmarkKey(id))) return
        val rec = runBlocking { db.recordingDao().get(b.recordingId) } ?: return
        val transcribing = TranscriptionManager.progress.value.containsKey(rec.id)
        val covered = rec.transcribedUntilMs >= b.atMs + WINDOW_MS
        if (!covered && (rec.status == RecordingStatus.RECORDING || transcribing)) {
            // The transcript hasn't caught up yet; look again shortly.
            queueBookmark(context, id, RECHECK_S)
            return
        }
        val segments = runBlocking { db.segmentDao().between(rec.id, b.atMs - WINDOW_MS, b.atMs + WINDOW_MS) }
        if (segments.isEmpty()) return // no transcript (yet): picked up again after transcription
        val text = withMarker(segments, b.atMs)
        work("Shrnuji záložku v ${formatDuration(b.atMs)}") {
            val a = Summariser.run(context, Prompts.bookmark(Prompts.Template.of(rec.template), text, formatDuration(b.atMs)), 900)
            val title = a.json.optString("nadpis").takeIf { it.isNotBlank() }
            runBlocking { db.bookmarkDao().setSummary(id, title, a.json.toString(), a.by) }
        }.onFailure { setError(bookmarkKey(id), it.message ?: "chyba") }
    }

    private fun withMarker(segments: List<SegmentEntity>, atMs: Long): String {
        val sb = StringBuilder()
        var marked = false
        for (s in segments) {
            if (!marked && s.startMs >= atMs) {
                sb.append(Prompts.MARKER).append(' ')
                marked = true
            }
            sb.append(s.text).append(' ')
        }
        if (!marked) sb.append(Prompts.MARKER)
        return sb.toString().trim()
    }

    // --- Whole recordings ----------------------------------------------------------

    private fun runRecording(context: Context, id: Long) {
        synchronized(queued) { queued.remove(recordingKey(id)) }
        val db = SummarisedDatabase.get(context)
        val rec = runBlocking { db.recordingDao().get(id) } ?: return
        if (rec.summaryJson != null || rec.status == RecordingStatus.RECORDING) return
        if (_state.value.errors.containsKey(recordingKey(id))) return
        if (TranscriptionManager.progress.value.containsKey(id)) return // kicked again when it finishes
        val segments = runBlocking { db.segmentDao().forRecording(id) }
        if (segments.isEmpty()) return
        val template = Prompts.Template.of(rec.template)
        val bookmarks = runBlocking { db.bookmarkDao().forRecording(id) }.joinToString("\n") { b ->
            "- ${formatDuration(b.atMs)}" + (b.title?.let { ": $it" } ?: "")
        }
        work("Shrnuji celou nahrávku") {
            val parts = split(segments)
            val answer = if (parts.size == 1) {
                Summariser.run(context, Prompts.whole(template, parts[0], bookmarks, isNotes = false))
            } else {
                val notes = parts.mapIndexed { i, part ->
                    setWorking("Shrnuji část ${i + 1} z ${parts.size}")
                    val a = Summariser.run(context, Prompts.section(template, i + 1, parts.size, part), 700)
                    val list = a.json.optJSONArray("poznamky")
                    "Část ${i + 1}:\n" + (0 until (list?.length() ?: 0)).joinToString("\n") { "- " + list!!.optString(it) }
                }
                setWorking("Skládám celkové shrnutí")
                Summariser.run(context, Prompts.whole(template, notes.joinToString("\n\n").take(PART_CHARS), bookmarks, isNotes = true))
            }
            runBlocking { db.recordingDao().setSummary(id, answer.json.toString(), answer.by) }
            answer.json.optString("nadpis").takeIf { it.isNotBlank() && rec.title.startsWith("Nahrávka ") }?.let { t ->
                runBlocking { db.recordingDao().rename(id, t) }
            }
        }.onFailure { setError(recordingKey(id), it.message ?: "chyba") }
    }

    /** Transcript text in parts of at most [PART_CHARS], split between segments. */
    private fun split(segments: List<SegmentEntity>): List<String> {
        val parts = mutableListOf<String>()
        val sb = StringBuilder()
        for (s in segments) {
            if (sb.length + s.text.length + 1 > PART_CHARS && sb.isNotEmpty()) {
                parts += sb.toString().trim()
                sb.clear()
            }
            sb.append(s.text).append(' ')
        }
        if (sb.isNotBlank()) parts += sb.toString().trim()
        return parts
    }

    // --- State ---------------------------------------------------------------------

    private inline fun work(label: String, block: () -> Unit): Result<Unit> {
        setWorking(label)
        return runCatching(block).also { setWorking(null) }
    }

    @Synchronized
    private fun setWorking(label: String?) {
        _state.value = _state.value.copy(working = label)
    }

    @Synchronized
    private fun setError(key: String, message: String) {
        _state.value = _state.value.copy(errors = _state.value.errors + (key to message))
    }

    @Synchronized
    private fun clearErrorsFor(recordingId: Long) {
        // Bookmark errors are cleared together: a retry is a fresh attempt for all of them.
        _state.value = _state.value.copy(errors = _state.value.errors.filterKeys { it != recordingKey(recordingId) && !it.startsWith("b") })
    }
}
