package io.github.fush1ft.summarised.core.audio

import java.io.Closeable
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Streams PCM data into a WAV file.
 *
 * A WAV header carries two byte counts that are only known once recording stops.
 * Rather than buffer the whole recording in memory, this writes a header with
 * zeroed sizes up front, appends PCM as it arrives, and patches the two length
 * fields on [close]. That means a recording killed mid-way leaves a file whose
 * audio data is intact but whose header claims zero length, which [repairHeader]
 * can fix without touching the samples.
 */
class WavFileWriter(private val file: File) : Closeable {

    private val output = RandomAccessFile(file, "rw")
    private var dataBytesWritten = 0L
    private var closed = false

    /** Bytes of PCM audio written so far. */
    val bytesWritten: Long get() = dataBytesWritten

    init {
        output.setLength(0)
        output.write(buildHeader(dataByteCount = 0))
    }

    fun write(buffer: ByteArray, length: Int) {
        check(!closed) { "Writer is closed" }
        output.write(buffer, 0, length)
        dataBytesWritten += length
    }

    override fun close() {
        if (closed) return
        closed = true
        try {
            patchSizes(output, dataBytesWritten)
        } finally {
            output.close()
        }
    }

    companion object {
        const val HEADER_SIZE = 44

        /**
         * Rewrites the length fields of a WAV file from its actual size on disk.
         * Used to recover a recording whose process died before [close] ran.
         */
        fun repairHeader(file: File) {
            val dataBytes = file.length() - HEADER_SIZE
            if (dataBytes <= 0) return
            RandomAccessFile(file, "rw").use { patchSizes(it, dataBytes) }
        }

        private fun patchSizes(output: RandomAccessFile, dataByteCount: Long) {
            // RIFF chunk size: everything after the first 8 bytes.
            output.seek(4)
            output.write(intLe((HEADER_SIZE - 8 + dataByteCount).toInt()))
            // data chunk size.
            output.seek(40)
            output.write(intLe(dataByteCount.toInt()))
        }

        private fun buildHeader(dataByteCount: Int): ByteArray {
            val byteRate = RecordingFormat.BYTES_PER_SECOND
            val blockAlign = RecordingFormat.CHANNEL_COUNT * RecordingFormat.BYTES_PER_SAMPLE

            return ByteBuffer.allocate(HEADER_SIZE).order(ByteOrder.LITTLE_ENDIAN).apply {
                put("RIFF".toByteArray(Charsets.US_ASCII))
                putInt(HEADER_SIZE - 8 + dataByteCount)
                put("WAVE".toByteArray(Charsets.US_ASCII))

                put("fmt ".toByteArray(Charsets.US_ASCII))
                putInt(16)                                        // PCM fmt chunk size
                putShort(1)                                       // format: PCM
                putShort(RecordingFormat.CHANNEL_COUNT.toShort())
                putInt(RecordingFormat.SAMPLE_RATE_HZ)
                putInt(byteRate)
                putShort(blockAlign.toShort())
                putShort(RecordingFormat.BITS_PER_SAMPLE.toShort())

                put("data".toByteArray(Charsets.US_ASCII))
                putInt(dataByteCount)
            }.array()
        }

        private fun intLe(value: Int): ByteArray =
            ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(value).array()
    }
}
