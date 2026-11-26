package io.github.fush1ft.summarised.transcribe

import kotlin.math.sqrt

/** Pure audio helpers for the transcriber, kept free of Android types for unit tests. */
object AudioMath {
    const val SAMPLE_RATE = 16_000

    /**
     * Where to cut [size] buffered samples so no word is split: the quietest
     * 20 ms frame between [minSamples] and min(size, maxSamples). Returns [size]
     * when the buffer is shorter than [minSamples].
     */
    fun quietCut(buffer: FloatArray, size: Int, minSamples: Int, maxSamples: Int): Int {
        if (size <= minSamples) return size
        val frame = SAMPLE_RATE / 50
        val end = minOf(size, maxSamples) - frame
        var bestAt = minOf(size, maxSamples)
        var best = Double.MAX_VALUE
        var at = minSamples
        while (at <= end) {
            var e = 0.0
            for (i in at until at + frame) e += buffer[i] * buffer[i]
            if (e < best) {
                best = e
                bestAt = at + frame / 2
            }
            at += frame
        }
        return bestAt
    }

    fun rms(buffer: FloatArray, from: Int, to: Int): Double {
        if (to <= from) return 0.0
        var s = 0.0
        for (i in from until to) s += buffer[i] * buffer[i]
        return sqrt(s / (to - from))
    }

    /** Linear-interpolation resampling of mono 16-bit PCM to 16 kHz. */
    fun resampleTo16k(input: ShortArray, count: Int, fromRate: Int): ShortArray {
        if (fromRate == SAMPLE_RATE) return input.copyOf(count)
        val outCount = (count.toLong() * SAMPLE_RATE / fromRate).toInt()
        val out = ShortArray(outCount)
        val step = fromRate.toDouble() / SAMPLE_RATE
        for (i in 0 until outCount) {
            val pos = i * step
            val i0 = pos.toInt().coerceAtMost(count - 1)
            val i1 = (i0 + 1).coerceAtMost(count - 1)
            val frac = pos - i0
            out[i] = (input[i0] * (1 - frac) + input[i1] * frac).toInt().toShort()
        }
        return out
    }
}
