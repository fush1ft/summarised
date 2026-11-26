package io.github.fush1ft.summarised.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.fush1ft.summarised.AppPrefs

enum class Screen { ONBOARDING, HOME, DETAIL, SETTINGS }

@Composable
fun SummarisedApp() {
    val context = LocalContext.current
    var screen by rememberSaveable { mutableStateOf(if (AppPrefs.onboarded(context)) Screen.HOME else Screen.ONBOARDING) }
    var detailId by rememberSaveable { mutableLongStateOf(0L) }
    BackHandler(enabled = screen == Screen.DETAIL || screen == Screen.SETTINGS) { screen = Screen.HOME }
    val home = { screen = Screen.HOME }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        AnimatedContent(
            targetState = screen,
            transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(160)) },
            label = "screen",
            modifier = Modifier.safeDrawingPadding(),
        ) { target ->
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                when (target) {
                    Screen.ONBOARDING -> OnboardingScreen(onDone = {
                        AppPrefs.setOnboarded(context)
                        screen = Screen.HOME
                    })
                    Screen.HOME -> HomeScreen(
                        onOpen = { id ->
                            detailId = id
                            screen = Screen.DETAIL
                        },
                        onSettings = { screen = Screen.SETTINGS },
                    )
                    Screen.DETAIL -> DetailScreen(detailId, onBack = home)
                    Screen.SETTINGS -> SettingsScreen(onBack = home)
                }
                Spacer(Modifier.height(40.dp))
            }
        }
    }
}
