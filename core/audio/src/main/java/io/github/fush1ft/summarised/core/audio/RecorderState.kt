package io.github.fush1ft.summarised.core.audio

import java.io.File

enum class RecorderStatus { IDLE, RECORDING, PAUSED }

/**
 * Everything the record screen needs to draw itself.
 *
 * [amplitudes] is a rolling window of recent levels rather than the whole
 * history: the waveform only ever shows the last few seconds, and keeping the
 * full history for a two-hour meeting would grow without bound for no benefit.
 */
data class RecorderState(
    val status: RecorderStatus = RecorderStatus.IDLE,
    val elapsedMs: Long = 0,
    val amplitudes: List<Float> = emptyList(),
    val outputFile: File? = null,
    val errorMessage: String? = null,
) {
    val isActive: Boolean get() = status != RecorderStatus.IDLE

    companion object {
        /** Roughly 12 seconds of history at one sample per 100 ms. */
        const val AMPLITUDE_WINDOW = 120
    }
}
