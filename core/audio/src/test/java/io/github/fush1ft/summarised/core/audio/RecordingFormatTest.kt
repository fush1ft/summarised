package io.github.fush1ft.summarised.core.audio

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class RecordingFormatTest {

    @Test
    fun `one second of audio is 32000 bytes`() {
        assertThat(RecordingFormat.BYTES_PER_SECOND).isEqualTo(32_000)
        assertThat(RecordingFormat.durationMsForBytes(32_000)).isEqualTo(1_000)
    }

    @Test
    fun `duration scales linearly with byte count`() {
        assertThat(RecordingFormat.durationMsForBytes(0)).isEqualTo(0)
        assertThat(RecordingFormat.durationMsForBytes(16_000)).isEqualTo(500)
        assertThat(RecordingFormat.durationMsForBytes(32_000L * 60)).isEqualTo(60_000)
    }

    @Test
    fun `a long recording does not overflow`() {
        // Three hours of audio is ~345 MB, well past the range of a 32-bit sample count.
        val threeHoursBytes = RecordingFormat.BYTES_PER_SECOND.toLong() * 3 * 60 * 60
        assertThat(RecordingFormat.durationMsForBytes(threeHoursBytes)).isEqualTo(3 * 60 * 60 * 1000L)
    }
}
