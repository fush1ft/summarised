package io.github.fush1ft.summarised.core.designsystem.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Live amplitude meter drawn as vertical bars, newest on the right.
 *
 * [amplitudes] are normalised 0f..1f. The caller owns the history window, so the
 * bar count is whatever it passes; this only draws what it is given.
 */
@Composable
fun Waveform(
    amplitudes: List<Float>,
    modifier: Modifier = Modifier,
    height: Dp = 96.dp,
    barWidth: Dp = 3.dp,
    barGap: Dp = 2.dp,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(height),
    ) {
        if (amplitudes.isEmpty()) return@Canvas

        val barWidthPx = barWidth.toPx()
        val stepPx = barWidthPx + barGap.toPx()
        val centerY = size.height / 2f
        // A bar at full amplitude spans the canvas height; anything quieter is scaled
        // from the centre line so silence reads as a thin spine rather than nothing.
        val minBarHeightPx = barWidthPx

        // Draw right-to-left so the newest sample is always pinned to the right edge,
        // regardless of how many samples the caller kept.
        var x = size.width - barWidthPx / 2f
        for (i in amplitudes.indices.reversed()) {
            if (x < 0f) break
            val amplitude = amplitudes[i].coerceIn(0f, 1f)
            val barHeight = (amplitude * size.height).coerceAtLeast(minBarHeightPx)
            drawLine(
                color = color,
                start = Offset(x, centerY - barHeight / 2f),
                end = Offset(x, centerY + barHeight / 2f),
                strokeWidth = barWidthPx,
                cap = StrokeCap.Round,
            )
            x -= stepPx
        }
    }
}
