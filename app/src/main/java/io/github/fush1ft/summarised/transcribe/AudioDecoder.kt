package io.github.fush1ft.summarised.transcribe

import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteOrder

/** Decodes a recorded chunk (Ogg Opus or WAV) to 16 kHz mono 16-bit PCM, block by block. */
object AudioDecoder {

    fun decode(file: File, onBlock: (ShortArray) -> Unit) {
        if (file.extension == "wav") decodeWav(file, onBlock) else decodeCompressed(file, onBlock)
    }

    private fun decodeWav(file: File, onBlock: (ShortArray) -> Unit) {
        RandomAccessFile(file, "r").use { raf ->
            raf.seek(44)
            val bytes = ByteArray(AudioMath.SAMPLE_RATE * 2)
            while (true) {
                val n = raf.read(bytes)
                if (n <= 0) break
                val samples = ShortArray(n / 2)
                java.nio.ByteBuffer.wrap(bytes, 0, n).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(samples)
                onBlock(samples)
            }
        }
    }

    private fun decodeCompressed(file: File, onBlock: (ShortArray) -> Unit) {
        val extractor = MediaExtractor()
        extractor.setDataSource(file.path)
        val track = (0 until extractor.trackCount).firstOrNull {
            extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
        } ?: run {
            extractor.release()
            return
        }
        extractor.selectTrack(track)
        val format = extractor.getTrackFormat(track)
        val codec = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME)!!)
        codec.configure(format, null, null, 0)
        codec.start()
        val info = MediaCodec.BufferInfo()
        var inputDone = false
        var outRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        var channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        var floatPcm = false
        try {
            while (true) {
                if (!inputDone) {
                    val idx = codec.dequeueInputBuffer(10_000)
                    if (idx >= 0) {
                        val buf = codec.getInputBuffer(idx)!!
                        val size = extractor.readSampleData(buf, 0)
                        if (size < 0) {
                            codec.queueInputBuffer(idx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            codec.queueInputBuffer(idx, 0, size, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }
                val out = codec.dequeueOutputBuffer(info, 10_000)
                when {
                    out == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val f = codec.outputFormat
                        outRate = f.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        channels = f.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                        floatPcm = f.containsKey(MediaFormat.KEY_PCM_ENCODING) &&
                            f.getInteger(MediaFormat.KEY_PCM_ENCODING) == AudioFormat.ENCODING_PCM_FLOAT
                    }
                    out >= 0 -> {
                        val buf = codec.getOutputBuffer(out)!!.order(ByteOrder.nativeOrder())
                        buf.position(info.offset)
                        buf.limit(info.offset + info.size)
                        val mono = if (floatPcm) {
                            val fb = buf.asFloatBuffer()
                            val frames = fb.remaining() / channels
                            ShortArray(frames) { i ->
                                var s = 0f
                                for (c in 0 until channels) s += fb.get(i * channels + c)
                                (s / channels * 32767f).toInt().coerceIn(-32768, 32767).toShort()
                            }
                        } else {
                            val sb = buf.asShortBuffer()
                            val frames = sb.remaining() / channels
                            ShortArray(frames) { i ->
                                var s = 0
                                for (c in 0 until channels) s += sb.get(i * channels + c)
                                (s / channels).toShort()
                            }
                        }
                        codec.releaseOutputBuffer(out, false)
                        if (mono.isNotEmpty()) onBlock(AudioMath.resampleTo16k(mono, mono.size, outRate))
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
                    }
                }
            }
        } finally {
            runCatching { codec.stop() }
            codec.release()
            extractor.release()
        }
    }
}
