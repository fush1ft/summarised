package io.github.fush1ft.summarised.transcribe

import android.content.Context
import io.github.fush1ft.summarised.AppPrefs
import io.github.fush1ft.summarised.core.database.SegmentEntity
import io.github.fush1ft.summarised.core.database.SummarisedDatabase
import io.github.fush1ft.summarised.core.inference.ParakeetEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import java.io.File
import java.util.concurrent.Executors

/**
 * Turns audio into timestamped transcript segments with Parakeet.
 *
 * Everything runs on one worker thread, in order: live audio from the recorder
 * and re-transcription of older recordings share the one loaded model. Audio is
 * cut into ~20-30 s stretches at the quietest moment, so words aren't split.
 */
object TranscriptionManager {
    private const val SR = AudioMath.SAMPLE_RATE
    private const val MIN_CUT = 20 * SR
    private const val MAX_CUT = 30 * SR
    private const val THREADS = 4
    /** Below this RMS a stretch is treated as silence and skipped. */
    private const val SILENCE_RMS = 0.004

    data class Progress(val doneMs: Long, val totalMs: Long, val live: Boolean, val error: String? = null)

    private val worker = Executors.newSingleThreadExecutor { r -> Thread(r, "summarised-transcribe") }
    private var engine: ParakeetEngine? = null
    private val sessions = HashMap<Long, Session>()

    private val _progress = MutableStateFlow<Map<Long, Progress>>(emptyMap())
    /** Recordings being transcribed right now. */
    val progress: StateFlow<Map<Long, Progress>> = _progress

    private class Session(val recordingId: Long, val live: Boolean) {
        var buffer = FloatArray(MAX_CUT + SR * 5)
        var size = 0
        /** Recording time of buffer[0]. */
        var baseMs = 0L
    }

    fun liveEnabled(context: Context) = AppPrefs.liveTranscription(context) && SpeechModel.isReady(context)

    // --- Live, fed by the recorder ----------------------------------------------

    fun startLive(context: Context, recordingId: Long) {
        val app = context.applicationContext
        worker.execute {
            if (!ensureEngine(app)) return@execute
            sessions[recordingId] = Session(recordingId, live = true)
            setProgress(recordingId, Progress(0, 0, live = true))
        }
    }

    /** Called from the capture thread with every block of 16 kHz PCM. */
    fun feed(context: Context, recordingId: Long, samples: ShortArray, count: Int) {
        val floats = FloatArray(count) { samples[it] / 32768f }
        val app = context.applicationContext
        worker.execute {
            val s = sessions[recordingId] ?: return@execute
            append(s, floats)
            while (s.size >= MIN_CUT + 5 * SR) cutAndTranscribe(app, s, force = false)
        }
    }

    fun finishLive(context: Context, recordingId: Long) {
        val app = context.applicationContext
        worker.execute {
            val s = sessions.remove(recordingId) ?: return@execute
            while (s.size > 0) cutAndTranscribe(app, s, force = true)
            clearProgress(recordingId)
            closeEngineIfIdle()
        }
    }

    // --- Afterwards, from the saved audio ---------------------------------------

    /** Re-transcribes a whole recording from its audio chunks, replacing any old transcript. */
    fun transcribeRecording(context: Context, recordingId: Long) {
        val app = context.applicationContext
        if (_progress.value.containsKey(recordingId)) return
        setProgress(recordingId, Progress(0, 0, live = false))
        worker.execute {
            val db = SummarisedDatabase.get(app)
            try {
                if (!ensureEngine(app)) {
                    setProgress(recordingId, Progress(0, 0, live = false, error = "Model pro přepis není stažený."))
                    return@execute
                }
                val chunks = runBlocking { db.audioChunkDao().forRecording(recordingId) }
                val total = chunks.sumOf { it.durationMs }
                runBlocking {
                    db.segmentDao().deleteFor(recordingId)
                    db.recordingDao().setTranscribedUntil(recordingId, 0)
                }
                val s = Session(recordingId, live = false)
                setProgress(recordingId, Progress(0, total, live = false))
                for (chunk in chunks) {
                    AudioDecoder.decode(File(chunk.path)) { block ->
                        append(s, FloatArray(block.size) { block[it] / 32768f })
                        while (s.size >= MIN_CUT + 5 * SR) {
                            cutAndTranscribe(app, s, force = false)
                            setProgress(recordingId, Progress(s.baseMs, total, live = false))
                        }
                    }
                }
                while (s.size > 0) cutAndTranscribe(app, s, force = true)
                clearProgress(recordingId)
            } catch (e: Exception) {
                setProgress(recordingId, Progress(0, 0, live = false, error = e.message ?: e.javaClass.simpleName))
            } finally {
                closeEngineIfIdle()
            }
        }
    }

    fun dismissError(recordingId: Long) = clearProgress(recordingId)

    // --- Worker-thread internals ------------------------------------------------

    private fun ensureEngine(context: Context): Boolean {
        if (engine != null) return true
        val f = SpeechModel.file(context)
        if (!f.exists()) return false
        engine = ParakeetEngine.open(f.path)
        return engine != null
    }

    private fun closeEngineIfIdle() {
        if (sessions.isEmpty()) {
            engine?.close()
            engine = null
        }
    }

    private fun append(s: Session, floats: FloatArray) {
        if (s.size + floats.size > s.buffer.size) s.buffer = s.buffer.copyOf(maxOf(s.buffer.size * 2, s.size + floats.size))
        floats.copyInto(s.buffer, s.size)
        s.size += floats.size
    }

    private fun cutAndTranscribe(context: Context, s: Session, force: Boolean) {
        val cut = if (force && s.size <= MAX_CUT) s.size else AudioMath.quietCut(s.buffer, s.size, MIN_CUT, MAX_CUT)
        val startMs = s.baseMs
        val endMs = startMs + cut * 1000L / SR
        if (AudioMath.rms(s.buffer, 0, cut) >= SILENCE_RMS) {
            val segments = runCatching { engine?.transcribe(s.buffer.copyOf(cut), THREADS).orEmpty() }
                .onFailure { android.util.Log.w("summarised", "transcription failed: ${it.message}") }
                .getOrDefault(emptyList())
            val rows = segments.map {
                SegmentEntity(recordingId = s.recordingId, startMs = startMs + it.startMs, endMs = startMs + it.endMs, text = it.text)
            }
            val db = SummarisedDatabase.get(context)
            runBlocking {
                if (rows.isNotEmpty()) db.segmentDao().insertAll(rows)
                db.recordingDao().setTranscribedUntil(s.recordingId, endMs)
            }
        }
        s.buffer.copyInto(s.buffer, 0, cut, s.size)
        s.size -= cut
        s.baseMs = endMs
        if (s.live) setProgress(s.recordingId, Progress(endMs, 0, live = true))
    }

    @Synchronized
    private fun setProgress(id: Long, p: Progress) {
        _progress.value = _progress.value + (id to p)
    }

    @Synchronized
    private fun clearProgress(id: Long) {
        _progress.value = _progress.value - id
    }
}
