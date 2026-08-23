package io.github.fush1ft.summarised.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.NavType
import androidx.navigation.navArgument
import io.github.fush1ft.summarised.navigation.RecordingDetailRoute
import io.github.fush1ft.summarised.feature.library.LibraryRoute
import io.github.fush1ft.summarised.feature.record.RecordRoute
import io.github.fush1ft.summarised.navigation.TopLevelDestination

@Composable
fun SummarisedApp() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    // The bar is hidden on detail screens so the transcript gets the full height.
    val showNavigationBar = TopLevelDestination.entries.any { destination ->
        currentDestination?.hierarchy?.any { it.route == destination.route } == true
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            if (showNavigationBar) {
                NavigationBar {
                    TopLevelDestination.entries.forEach { destination ->
                        val selected = currentDestination?.hierarchy
                            ?.any { it.route == destination.route } == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(destination.route) {
                                    // Keep a single copy of each top-level destination and
                                    // preserve its scroll position when switching tabs.
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                Icon(
                                    imageVector = if (selected) {
                                        destination.selectedIcon
                                    } else {
                                        destination.unselectedIcon
                                    },
                                    contentDescription = null,
                                )
                            },
                            label = { Text(stringResource(destination.labelRes)) },
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = TopLevelDestination.RECORD.route,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(TopLevelDestination.RECORD.route) {
                RecordRoute()
            }
            composable(TopLevelDestination.LIBRARY.route) {
                LibraryRoute()
            }
            composable(TopLevelDestination.SETTINGS.route) {
                PlaceholderScreen("Settings")
            }
            composable(
                route = RecordingDetailRoute.ROUTE,
                arguments = listOf(
                    navArgument(RecordingDetailRoute.ARG_RECORDING_ID) {
                        type = NavType.LongType
                    },
                ),
            ) { entry ->
                val recordingId = entry.arguments
                    ?.getLong(RecordingDetailRoute.ARG_RECORDING_ID) ?: 0L
                PlaceholderScreen("Recording $recordingId")
            }
        }
    }
}
