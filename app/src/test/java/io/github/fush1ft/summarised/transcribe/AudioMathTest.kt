package io.github.fush1ft.summarised.transcribe

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AudioMathTest {
    private val sr = AudioMath.SAMPLE_RATE

    @Test
    fun cutsInTheQuietGap() {
        // 30 s of "speech" with a silent gap at 23.0–23.2 s.
        val buf = FloatArray(30 * sr) { if (it in (23 * sr) until (23 * sr + sr / 5)) 0f else 0.5f }
        val cut = AudioMath.quietCut(buf, buf.size, 20 * sr, 30 * sr)
        assertThat(cut).isIn(23 * sr..23 * sr + sr / 5)
    }

    @Test
    fun shortBufferIsTakenWhole() {
        val buf = FloatArray(5 * sr) { 0.5f }
        assertThat(AudioMath.quietCut(buf, buf.size, 20 * sr, 30 * sr)).isEqualTo(buf.size)
    }

    @Test
    fun resamples48kTo16k() {
        val input = ShortArray(48_000) { 1000 }
        val out = AudioMath.resampleTo16k(input, input.size, 48_000)
        assertThat(out.size).isEqualTo(16_000)
        assertThat(out.all { it.toInt() == 1000 }).isTrue()
    }
}
