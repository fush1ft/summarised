package io.github.fush1ft.summarised.core.designsystem.theme

import androidx.compose.ui.graphics.Color

/**
 * Fallback palette, used on devices without dynamic color (below Android 12) and
 * whenever the user turns dynamic color off. Deep indigo carries the brand, teal
 * marks anything live or in progress.
 */
internal val IndigoPrimaryLight = Color(0xFF4A55A2)
internal val IndigoOnPrimaryLight = Color(0xFFFFFFFF)
internal val IndigoContainerLight = Color(0xFFDEE0FF)
internal val IndigoOnContainerLight = Color(0xFF00105C)

internal val TealSecondaryLight = Color(0xFF006A6A)
internal val TealOnSecondaryLight = Color(0xFFFFFFFF)
internal val TealContainerLight = Color(0xFF9CF1F0)
internal val TealOnContainerLight = Color(0xFF002020)

internal val SurfaceLight = Color(0xFFFBF8FF)
internal val OnSurfaceLight = Color(0xFF1B1B21)
internal val SurfaceVariantLight = Color(0xFFE3E1EC)
internal val OnSurfaceVariantLight = Color(0xFF46464F)
internal val OutlineLight = Color(0xFF767680)
internal val ErrorLight = Color(0xFFBA1A1A)
internal val OnErrorLight = Color(0xFFFFFFFF)

internal val IndigoPrimaryDark = Color(0xFFBAC3FF)
internal val IndigoOnPrimaryDark = Color(0xFF1B2570)
internal val IndigoContainerDark = Color(0xFF323C88)
internal val IndigoOnContainerDark = Color(0xFFDEE0FF)

internal val TealSecondaryDark = Color(0xFF80D5D4)
internal val TealOnSecondaryDark = Color(0xFF003737)
internal val TealContainerDark = Color(0xFF004F4F)
internal val TealOnContainerDark = Color(0xFF9CF1F0)

internal val SurfaceDark = Color(0xFF131318)
internal val OnSurfaceDark = Color(0xFFE4E1E9)
internal val SurfaceVariantDark = Color(0xFF46464F)
internal val OnSurfaceVariantDark = Color(0xFFC7C5D0)
internal val OutlineDark = Color(0xFF90909A)
internal val ErrorDark = Color(0xFFFFB4AB)
internal val OnErrorDark = Color(0xFF690005)

/**
 * Colors assigned to speakers, in order. Deliberately kept out of the Material
 * scheme: a speaker's color must stay stable across light and dark mode and must
 * not shift when dynamic color re-themes the rest of the app, because users learn
 * "green is Priya" and that association should survive a theme change.
 *
 * Each pair is (container, onContainer) so speaker chips stay legible in both themes.
 */
val SpeakerColorsLight: List<Pair<Color, Color>> = listOf(
    Color(0xFFDEE0FF) to Color(0xFF00105C), // indigo
    Color(0xFF9CF1F0) to Color(0xFF002020), // teal
    Color(0xFFFFD9E2) to Color(0xFF3E001D), // rose
    Color(0xFFE8DEF8) to Color(0xFF21005D), // violet
    Color(0xFFD3E8D3) to Color(0xFF002106), // green
    Color(0xFFFFDDB3) to Color(0xFF2B1700), // amber
    Color(0xFFCDE5FF) to Color(0xFF001D33), // sky
    Color(0xFFFFDAD6) to Color(0xFF410002), // coral
)

val SpeakerColorsDark: List<Pair<Color, Color>> = listOf(
    Color(0xFF323C88) to Color(0xFFDEE0FF),
    Color(0xFF004F4F) to Color(0xFF9CF1F0),
    Color(0xFF5E1133) to Color(0xFFFFD9E2),
    Color(0xFF4F378B) to Color(0xFFE8DEF8),
    Color(0xFF1F3A21) to Color(0xFFD3E8D3),
    Color(0xFF5B4300) to Color(0xFFFFDDB3),
    Color(0xFF00497D) to Color(0xFFCDE5FF),
    Color(0xFF93000A) to Color(0xFFFFDAD6),
)
