package io.github.fush1ft.summarised.capture

import android.media.MediaCodec
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Writes 16 kHz mono PCM into one audio file. */
interface ChunkWriter {
    val file: File
    val format: String
    fun write(samples: ShortArray, count: Int)
    fun durationMs(): Long
    fun close()
}

/**
 * Opus in Ogg via the platform encoder and muxer (both API 29+): about 10 MB
 * per hour at 24 kbit/s instead of 115 MB as WAV.
 */
class OpusChunkWriter(override val file: File, private val sampleRate: Int = 16_000, bitrate: Int = 24_000) : ChunkWriter {
    override val format = "opus"
    private val codec: MediaCodec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_OPUS)
    private val muxer = MediaMuxer(file.path, MediaMuxer.OutputFormat.MUXER_OUTPUT_OGG)
    private val info = MediaCodec.BufferInfo()
    private var track = -1
    private var muxerStarted = false
    private var samplesIn = 0L

    init {
        try {
            val fmt = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_OPUS, sampleRate, 1)
            fmt.setInteger(MediaFormat.KEY_BIT_RATE, bitrate)
            codec.configure(fmt, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            codec.start()
        } catch (e: Exception) {
            codec.release()
            muxer.release()
            file.delete()
            throw e
        }
    }

    override fun write(samples: ShortArray, count: Int) {
        var offset = 0
        var spins = 0
        while (offset < count) {
            val idx = codec.dequeueInputBuffer(10_000)
            if (idx >= 0) {
                val buf = codec.getInputBuffer(idx) ?: error("no input buffer")
                buf.clear()
                val n = minOf(count - offset, buf.remaining() / 2)
                buf.order(ByteOrder.nativeOrder()).asShortBuffer().put(samples, offset, n)
                codec.queueInputBuffer(idx, 0, n * 2, samplesIn * 1_000_000L / sampleRate, 0)
                samplesIn += n
                offset += n
            } else if (++spins > 200) {
                error("Opus encoder stopped accepting input")
            }
            drain(endOfStream = false)
        }
    }

    private fun drain(endOfStream: Boolean) {
        var idle = 0
        while (true) {
            val idx = codec.dequeueOutputBuffer(info, if (endOfStream) 10_000 else 0)
            when {
                idx == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                    if (!endOfStream || ++idle > 100) return
                }
                idx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    if (!muxerStarted) {
                        track = muxer.addTrack(codec.outputFormat)
                        muxer.start()
                        muxerStarted = true
                    }
                }
                idx >= 0 -> {
                    val out = codec.getOutputBuffer(idx)
                    val isConfig = info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0
                    if (out != null && info.size > 0 && muxerStarted && !isConfig) {
                        out.position(info.offset)
                        out.limit(info.offset + info.size)
                        muxer.writeSampleData(track, out, info)
                    }
                    codec.releaseOutputBuffer(idx, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) return
                }
            }
        }
    }

    override fun durationMs(): Long = samplesIn * 1000L / sampleRate

    override fun close() {
        try {
            val idx = codec.dequeueInputBuffer(50_000)
            if (idx >= 0) {
                codec.queueInputBuffer(idx, 0, 0, samplesIn * 1_000_000L / sampleRate, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
            }
            drain(endOfStream = true)
        } finally {
            runCatching { codec.stop() }
            codec.release()
            if (muxerStarted) runCatching { muxer.stop() }
            muxer.release()
        }
    }
}

/** Fallback when no Opus encoder is available: plain 16-bit WAV. */
class WavChunkWriter(override val file: File, private val sampleRate: Int = 16_000) : ChunkWriter {
    override val format = "wav"
    private val raf = RandomAccessFile(file, "rw")
    private var dataBytes = 0L

    init {
        raf.setLength(0)
        raf.write(wavHeader(0, sampleRate))
    }

    override fun write(samples: ShortArray, count: Int) {
        val buf = ByteBuffer.allocate(count * 2).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until count) buf.putShort(samples[i])
        raf.write(buf.array())
        dataBytes += count * 2L
    }

    override fun durationMs(): Long = dataBytes * 1000L / (sampleRate * 2L)

    override fun close() {
        raf.seek(0)
        raf.write(wavHeader(dataBytes, sampleRate))
        raf.close()
    }

    companion object {
        fun wavHeader(dataBytes: Long, sampleRate: Int): ByteArray {
            val b = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
            b.put("RIFF".toByteArray()); b.putInt((36 + dataBytes).toInt())
            b.put("WAVE".toByteArray()); b.put("fmt ".toByteArray())
            b.putInt(16); b.putShort(1); b.putShort(1)
            b.putInt(sampleRate); b.putInt(sampleRate * 2); b.putShort(2); b.putShort(16)
            b.put("data".toByteArray()); b.putInt(dataBytes.toInt())
            return b.array()
        }

        /**
         * A WAV chunk cut off by a crash has a header that still says "0 bytes".
         * Rewriting it from the file size makes the audio playable again.
         */
        fun repairHeader(file: File, sampleRate: Int = 16_000): Long {
            if (!file.exists() || file.length() <= 44) return 0
            val data = file.length() - 44
            RandomAccessFile(file, "rw").use { raf ->
                raf.seek(0)
                raf.write(wavHeader(data, sampleRate))
            }
            return data * 1000L / (sampleRate * 2L)
        }
    }
}

fun openChunkWriter(dir: File, recordingId: Long, index: Int): ChunkWriter =
    try {
        OpusChunkWriter(File(dir, "rec-$recordingId-$index.ogg"))
    } catch (e: Exception) {
        android.util.Log.w("summarised", "Opus unavailable, writing WAV: ${e.message}")
        WavChunkWriter(File(dir, "rec-$recordingId-$index.wav"))
    }
