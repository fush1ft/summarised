package io.github.fush1ft.summarised.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.annotation.StringRes
import io.github.fush1ft.summarised.R

/** Top-level destinations, in the order they appear in the navigation bar. */
enum class TopLevelDestination(
    val route: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    @StringRes val labelRes: Int,
) {
    RECORD(
        route = "record",
        selectedIcon = Icons.Filled.Mic,
        unselectedIcon = Icons.Outlined.Mic,
        labelRes = R.string.nav_record,
    ),
    LIBRARY(
        route = "library",
        selectedIcon = Icons.Filled.LibraryMusic,
        unselectedIcon = Icons.Outlined.LibraryMusic,
        labelRes = R.string.nav_library,
    ),
    SETTINGS(
        route = "settings",
        selectedIcon = Icons.Filled.Settings,
        unselectedIcon = Icons.Outlined.Settings,
        labelRes = R.string.nav_settings,
    ),
}

/** Detail route, reached from the library rather than the navigation bar. */
object RecordingDetailRoute {
    const val ARG_RECORDING_ID = "recordingId"
    const val ROUTE = "recording/{$ARG_RECORDING_ID}"

    fun createRoute(recordingId: Long): String = "recording/$recordingId"
}
