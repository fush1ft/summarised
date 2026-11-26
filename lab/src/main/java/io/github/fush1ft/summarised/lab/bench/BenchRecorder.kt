package io.github.fush1ft.summarised.lab.bench

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import io.github.fush1ft.summarised.lab.capture.WavWriter
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
    @Volatile private var running = false

    fun sampleFile(context: Context, passageId: String): File =
        File(File(context.getExternalFilesDir(null), "bench").apply { mkdirs() }, "$passageId.wav")

    fun stop() {
        running = false
    }

    @SuppressLint("MissingPermission") // the UI asks for RECORD_AUDIO first
    suspend fun record(context: Context, passageId: String) = withContext(Dispatchers.IO) {
        val rate = 16_000
        val minBuf = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val record = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION, rate,
            AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(minBuf * 4, rate),
        )
        if (record.state != AudioRecord.STATE_INITIALIZED) {
            record.release()
            return@withContext
        }
        val tmp = File(sampleFile(context, passageId).path + ".tmp")
        val writer = WavWriter(tmp, rate)
        val buf = ShortArray(rate / 10)
        running = true
        _recordingId.value = passageId
        _elapsedMs.value = 0
        record.startRecording()
        try {
            while (running) {
                val n = record.read(buf, 0, buf.size)
                if (n > 0) {
                    writer.write(buf, n)
                    _elapsedMs.value = writer.durationMs()
                }
            }
        } finally {
            runCatching { record.stop() }
            record.release()
            writer.close()
            tmp.renameTo(sampleFile(context, passageId))
            _recordingId.value = null
        }
    }
}
