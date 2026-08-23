package io.github.fush1ft.summarised.core.audio

/**
 * The one audio format this app records and processes in.
 *
 * 16 kHz mono PCM-16 is what both the Whisper ASR models and the diarization
 * models expect, so recording directly in it removes a resampling stage from the
 * pipeline and any quality loss that would come with it.
 */
object RecordingFormat {
    const val SAMPLE_RATE_HZ = 16_000
    const val CHANNEL_COUNT = 1
    const val BITS_PER_SAMPLE = 16
    const val BYTES_PER_SAMPLE = BITS_PER_SAMPLE / 8

    const val BYTES_PER_SECOND = SAMPLE_RATE_HZ * CHANNEL_COUNT * BYTES_PER_SAMPLE

    /** Milliseconds of audio represented by [byteCount] bytes of PCM data. */
    fun durationMsForBytes(byteCount: Long): Long =
        byteCount * 1000L / BYTES_PER_SECOND
}
