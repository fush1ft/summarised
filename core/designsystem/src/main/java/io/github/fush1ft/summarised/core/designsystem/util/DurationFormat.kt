package io.github.fush1ft.summarised.core.designsystem.util

import java.util.Locale
import kotlin.time.Duration.Companion.milliseconds

/**
 * Formats a duration for display. Under an hour renders as m:ss, an hour or more
 * as h:mm:ss, so short recordings do not carry a permanent leading "0:".
 */
fun formatDuration(millis: Long): String {
    val total = millis.coerceAtLeast(0).milliseconds
    val hours = total.inWholeHours
    val minutes = total.inWholeMinutes % 60
    val seconds = total.inWholeSeconds % 60
    return if (hours > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%d:%02d", minutes, seconds)
    }
}

/**
 * Zero-padded form used by the recording timer, where a stable width matters more
 * than compactness.
 */
fun formatTimer(millis: Long): String {
    val total = millis.coerceAtLeast(0).milliseconds
    val hours = total.inWholeHours
    val minutes = total.inWholeMinutes % 60
    val seconds = total.inWholeSeconds % 60
    return if (hours > 0) {
        String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }
}
