package io.github.fush1ft.summarised.core.designsystem.util

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DurationFormatTest {

    @Test
    fun `formats sub-minute durations without a leading hour`() {
        assertThat(formatDuration(0)).isEqualTo("0:00")
        assertThat(formatDuration(9_000)).isEqualTo("0:09")
        assertThat(formatDuration(59_999)).isEqualTo("0:59")
    }

    @Test
    fun `formats minutes and seconds`() {
        assertThat(formatDuration(60_000)).isEqualTo("1:00")
        assertThat(formatDuration(11 * 60_000 + 5_000)).isEqualTo("11:05")
    }

    @Test
    fun `adds an hours field only once an hour is reached`() {
        assertThat(formatDuration(59 * 60_000 + 59_000)).isEqualTo("59:59")
        assertThat(formatDuration(60 * 60_000)).isEqualTo("1:00:00")
        assertThat(formatDuration(2 * 3_600_000 + 3 * 60_000 + 4_000)).isEqualTo("2:03:04")
    }

    @Test
    fun `treats negative input as zero rather than rendering a negative timer`() {
        assertThat(formatDuration(-5_000)).isEqualTo("0:00")
        assertThat(formatTimer(-5_000)).isEqualTo("00:00")
    }

    @Test
    fun `timer pads minutes so the display width stays stable`() {
        assertThat(formatTimer(9_000)).isEqualTo("00:09")
        assertThat(formatTimer(9 * 60_000)).isEqualTo("09:00")
        assertThat(formatTimer(3_600_000)).isEqualTo("01:00:00")
    }
}
