package io.github.fush1ft.summarised

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import io.github.fush1ft.summarised.core.designsystem.theme.SummarisedTheme
import io.github.fush1ft.summarised.ui.SummarisedApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            SummarisedTheme {
                SummarisedApp()
            }
        }
    }
}
