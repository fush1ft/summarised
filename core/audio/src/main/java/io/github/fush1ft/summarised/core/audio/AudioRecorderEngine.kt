package io.github.fush1ft.summarised.core.audio

import android.Manifest
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.annotation.RequiresPermission
import java.io.File
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Wraps [AudioRecord] and writes what it reads into a WAV file.
 *
 * This is deliberately a plain blocking class with no coroutines or state of its
 * own beyond the recorder: [RecorderController] owns the lifecycle and decides
 * what to do with the numbers coming out of here.
 */
internal class AudioRecorderEngine {

    private var record: AudioRecord? = null
    private var writer: WavFileWriter? = null
    private lateinit var buffer: ByteArray

    val bytesWritten: Long get() = writer?.bytesWritten ?: 0

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    fun start(outputFile: File) {
        val minBufferSize = AudioRecord.getMinBufferSize(
            RecordingFormat.SAMPLE_RATE_HZ,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        check(minBufferSize > 0) { "This device cannot record 16 kHz mono PCM" }

        // Read in ~100 ms chunks so the waveform updates at a natural rate, but
        // never below what the device asks for.
        val chunkBytes = RecordingFormat.BYTES_PER_SECOND / 10
        val bufferSize = maxOf(minBufferSize, chunkBytes)
        buffer = ByteArray(chunkBytes)

        val recorder = AudioRecord(
            // VOICE_RECOGNITION avoids the aggressive AGC and noise suppression the
            // communication sources apply, which hurt transcription accuracy.
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            RecordingFormat.SAMPLE_RATE_HZ,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize,
        )
        check(recorder.state == AudioRecord.STATE_INITIALIZED) {
            "Could not initialise the microphone"
        }

        writer = WavFileWriter(outputFile)
        record = recorder
        recorder.startRecording()
    }

    /**
     * Reads one chunk. Returns its normalised amplitude (0f..1f), or null if the
     * read failed. When [discard] is true the samples are measured but not
     * written, which is how pause works without tearing down the recorder.
     */
    fun readChunk(discard: Boolean): Float? {
        val recorder = record ?: return null
        val read = recorder.read(buffer, 0, buffer.size)
        if (read <= 0) return null
        if (!discard) writer?.write(buffer, read)
        return normalisedAmplitude(buffer, read)
    }

    fun stop() {
        record?.let { recorder ->
            runCatching { recorder.stop() }
            recorder.release()
        }
        record = null
        writer?.close()
        writer = null
    }

    /**
     * RMS of the chunk, mapped onto 0f..1f.
     *
     * RMS rather than peak: a single click would otherwise pin the waveform to
     * full height and make speech look flat by comparison.
     */
    private fun normalisedAmplitude(bytes: ByteArray, length: Int): Float {
        var sumOfSquares = 0.0
        var sampleCount = 0
        var i = 0
        while (i + 1 < length) {
            // Little-endian 16-bit signed.
            val sample = ((bytes[i + 1].toInt() shl 8) or (bytes[i].toInt() and 0xFF)).toShort()
            val value = sample.toDouble() / Short.MAX_VALUE
            sumOfSquares += value * value
            sampleCount++
            i += 2
        }
        if (sampleCount == 0) return 0f

        val rms = sqrt(sumOfSquares / sampleCount)
        // Speech RMS sits well below 1.0, so scale up before clamping; without this
        // the waveform is a barely visible line for normal conversation.
        return (rms * AMPLITUDE_GAIN).coerceIn(0.0, 1.0).toFloat()
    }

    private companion object {
        const val AMPLITUDE_GAIN = 4.0
    }
}

/** Peak amplitude helper kept for tests and diagnostics. */
internal fun peakAmplitude(samples: ShortArray): Float =
    (samples.maxOfOrNull { abs(it.toInt()) } ?: 0).toFloat() / Short.MAX_VALUE
