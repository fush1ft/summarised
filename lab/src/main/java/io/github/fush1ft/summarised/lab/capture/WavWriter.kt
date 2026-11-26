package io.github.fush1ft.summarised.lab.capture

import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** 16-bit mono PCM WAV writer; the header is patched with real sizes on close. */
class WavWriter(file: File, private val sampleRate: Int = 16_000) : AutoCloseable {
    private val raf = RandomAccessFile(file, "rw")
    private var dataBytes = 0L

    init {
        raf.setLength(0)
        raf.write(header(0))
    }

    fun write(samples: ShortArray, count: Int) {
        val buf = ByteBuffer.allocate(count * 2).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until count) buf.putShort(samples[i])
        raf.write(buf.array())
        dataBytes += count * 2L
    }

    fun durationMs(): Long = dataBytes * 1000L / (sampleRate * 2L)

    override fun close() {
        raf.seek(0)
        raf.write(header(dataBytes))
        raf.close()
    }

    private fun header(data: Long): ByteArray {
        val b = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        b.put("RIFF".toByteArray()); b.putInt((36 + data).toInt())
        b.put("WAVE".toByteArray()); b.put("fmt ".toByteArray())
        b.putInt(16); b.putShort(1); b.putShort(1)
        b.putInt(sampleRate); b.putInt(sampleRate * 2); b.putShort(2); b.putShort(16)
        b.put("data".toByteArray()); b.putInt(data.toInt())
        return b.array()
    }

    companion object {
        /** Reads a 16 kHz mono 16-bit WAV written by [WavWriter] as float PCM. */
        fun readFloats(file: File): FloatArray {
            val bytes = file.readBytes()
            if (bytes.size <= 44) return FloatArray(0)
            val bb = ByteBuffer.wrap(bytes, 44, bytes.size - 44).order(ByteOrder.LITTLE_ENDIAN)
            val n = (bytes.size - 44) / 2
            return FloatArray(n) { bb.getShort() / 32768f }
        }
    }
}
