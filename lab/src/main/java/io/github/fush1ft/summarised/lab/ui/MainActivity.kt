@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package io.github.fush1ft.summarised.lab.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.fush1ft.summarised.lab.bench.Models
import io.github.fush1ft.summarised.lab.capture.CaptureController
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        Models.refresh(this)
        setContent { LabTheme { LabApp() } }
    }

    override fun onResume() {
        super.onResume()
        Models.refresh(this)
    }
}

enum class Screen { HOME, TRIGGER, ASR, LLM, RESULTS, ADVANCED }

@Composable
private fun LabTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val scheme = if (isSystemInDarkTheme()) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    MaterialTheme(colorScheme = scheme) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface, content = content)
    }
}

@Composable
private fun LabApp() {
    var screen by rememberSaveable { mutableStateOf(Screen.HOME) }
    BackHandler(enabled = screen != Screen.HOME) { screen = Screen.HOME }
    val go: (Screen) -> Unit = { screen = it }
    val back = { screen = Screen.HOME }

    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        if (screen != Screen.TRIGGER) RecordingBanner { screen = Screen.TRIGGER }
        AnimatedContent(
            targetState = screen,
            transitionSpec = {
                val forward = targetState != Screen.HOME
                (slideInHorizontally(tween(260)) { w -> if (forward) w / 6 else -w / 6 } + fadeIn(tween(260))) togetherWith
                    (slideOutHorizontally(tween(200)) { w -> if (forward) -w / 6 else w / 6 } + fadeOut(tween(200)))
            },
            label = "screen",
        ) { target ->
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                when (target) {
                    Screen.HOME -> HomeScreen(go)
                    Screen.TRIGGER -> TriggerScreen(back)
                    Screen.ASR -> AsrScreen(back)
                    Screen.LLM -> LlmScreen(back)
                    Screen.RESULTS -> ResultsScreen(back)
                    Screen.ADVANCED -> AdvancedScreen(back)
                }
                Spacer(Modifier.height(40.dp))
            }
        }
    }
}

/** Always-visible strip while a recording runs, so it's never forgotten. */
@Composable
private fun RecordingBanner(onOpen: () -> Unit) {
    val state by CaptureController.state.collectAsStateWithLifecycle()
    if (state.phase != CaptureController.Phase.RECORDING) return
    var elapsed by remember { mutableLongStateOf(0L) }
    LaunchedEffect(state.startedAtElapsed) {
        while (true) {
            elapsed = state.elapsedMs()
            delay(500)
        }
    }
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            PulsingDot(12)
            Spacer(Modifier.width(10.dp))
            Text(
                "Nahrávám ${CaptureController.formatMs(elapsed)} · ${state.bookmarksMs.size} záložek",
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onOpen) { Text("Otevřít") }
        }
    }
}
