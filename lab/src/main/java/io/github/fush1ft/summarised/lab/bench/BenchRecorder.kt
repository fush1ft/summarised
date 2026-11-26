package io.github.fush1ft.summarised.lab.bench

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import io.github.fush1ft.summarised.lab.capture.WavWriter
import io.github.fush1ft.summarised.lab.log.EventLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.io.File

/** Records a read-aloud sample for the ASR benchmark (in the foreground, no service needed). */
object BenchRecorder {
    private val _recordingId = MutableStateFlow<String?>(null)
    val recordingId: StateFlow<String?> = _recordingId
    private val _elapsedMs = MutableStateFlow(0L)
    val elapsedMs: StateFlow<Long> = _elapsedMs
    private val _level = MutableStateFlow(0f)
    /** Microphone level 0..1 for the live meter. */
    val level: StateFlow<Float> = _level
    @Volatile private var running = false

    fun sampleFile(context: Context, passageId: String): File =
        File(File(context.getExternalFilesDir(null), "bench").apply { mkdirs() }, "$passageId.wav")

    fun stop() {
        running = false
    }

    @SuppressLint("MissingPermission") // the UI asks for RECORD_AUDIO first
    suspend fun record(context: Context, passageId: String) = withContext(Dispatchers.IO) {
        if (_recordingId.value != null) return@withContext
        val rate = 16_000
        // Claimed before anything can fail, released in finally whatever happens,
        // so the UI can never be left thinking a recording is running.
        running = true
        _recordingId.value = passageId
        _elapsedMs.value = 0
        var record: AudioRecord? = null
        var writer: WavWriter? = null
        val tmp = File(sampleFile(context, passageId).path + ".tmp")
        var ok = false
        try {
            val minBuf = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
            record = AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION, rate,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(minBuf * 4, rate),
            )
            if (record.state != AudioRecord.STATE_INITIALIZED) {
                EventLog.add("BENCH", "mikrofon se nepodařilo otevřít")
                return@withContext
            }
            writer = WavWriter(tmp, rate)
            val buf = ShortArray(rate / 10)
            record.startRecording()
            var errors = 0
            while (running) {
                val n = record.read(buf, 0, buf.size)
                if (n <= 0) {
                    // Mic taken away (call, another recorder): stop instead of spinning.
                    if (++errors > 20) {
                        EventLog.add("BENCH", "mikrofon přestal posílat zvuk (read=$n)")
                        break
                    }
                    continue
                }
                errors = 0
                writer.write(buf, n)
                _elapsedMs.value = writer.durationMs()
                var sum = 0.0
                for (i in 0 until n) sum += buf[i].toDouble() * buf[i]
                val rms = kotlin.math.sqrt(sum / n)
                // log scale: room noise (rms ~100) -> 0, normal speech (~1000-3000) -> 0.5-0.75.
                _level.value = ((kotlin.math.log10(1 + rms) - 2.0) / 2.0).toFloat().coerceIn(0f, 1f)
            }
            ok = writer.durationMs() > 1000
        } catch (e: Exception) {
            EventLog.add("BENCH", "nahrávání selhalo: ${e.javaClass.simpleName}: ${e.message}")
        } finally {
            runCatching { record?.stop() }
            runCatching { record?.release() }
            runCatching { writer?.close() }
            if (ok) tmp.renameTo(sampleFile(context, passageId)) else tmp.delete()
            running = false
            _recordingId.value = null
            _level.value = 0f
        }
    }
}
