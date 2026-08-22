package io.github.fush1ft.summarised.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val SummarisedTypography = Typography()

/**
 * The recording timer. Monospace so digits do not jitter as the seconds tick over,
 * which is very visible at this size.
 */
val TimerTextStyle = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontWeight = FontWeight.Light,
    fontSize = 56.sp,
    letterSpacing = (-1).sp,
)

/** Timestamps in the transcript. Same reasoning, smaller. */
val TimestampTextStyle = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontWeight = FontWeight.Normal,
    fontSize = 12.sp,
)
