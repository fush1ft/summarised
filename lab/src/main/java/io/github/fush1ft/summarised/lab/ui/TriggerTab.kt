@file:OptIn(ExperimentalMaterial3Api::class)

package io.github.fush1ft.summarised.lab.ui

import android.Manifest
import android.app.role.RoleManager
import android.content.Intent
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.fush1ft.summarised.lab.capture.CaptureController
import io.github.fush1ft.summarised.lab.capture.Haptics
import io.github.fush1ft.summarised.lab.capture.Settings
import io.github.fush1ft.summarised.lab.log.EventLog
import kotlinx.coroutines.delay

@Composable
internal fun TriggerTab() {
    val context = LocalContext.current
    val state by CaptureController.state.collectAsStateWithLifecycle()
    val log by EventLog.lines.collectAsStateWithLifecycle()
    var now by remember { mutableLongStateOf(0L) }
    var isAssistant by remember { mutableStateOf(false) }
    var startMode by remember { mutableStateOf(Settings.startMode(context)) }
    var usage by remember { mutableStateOf(Haptics.usage(context)) }

    LaunchedEffect(Unit) {
        while (true) {
            now = state.elapsedMs()
            isAssistant = context.getSystemService(RoleManager::class.java).isRoleHeld(RoleManager.ROLE_ASSISTANT)
            delay(500)
        }
    }

    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        EventLog.add("APP", "permissions: " + result.entries.joinToString { "${it.key.substringAfterLast('.')}=${it.value}" })
    }

    Section("Stav") {
        val phase = when (state.phase) {
            CaptureController.Phase.IDLE -> "Nenahrává se"
            CaptureController.Phase.STARTING -> "Spouští se…"
            CaptureController.Phase.RECORDING -> "● Nahrává se ${CaptureController.formatMs(now)}"
            CaptureController.Phase.FAILED -> "✕ Chyba: ${state.error}"
        }
        Text(phase, style = MaterialTheme.typography.headlineSmall)
        Text("Záložky: ${state.bookmarksMs.joinToString { CaptureController.formatMs(it) }.ifEmpty { "žádné" }}")
        if (state.silenced) Text("⚠ Systém posílá ticho (mikrofon zablokován)", color = MaterialTheme.colorScheme.error)
        Text(if (isAssistant) "✓ Lab je výchozí digitální asistent" else "✗ Lab zatím NENÍ výchozí asistent")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { CaptureController.onTrigger(context, "app-button") }) {
                Text(if (state.phase == CaptureController.Phase.RECORDING) "Záložka" else "Start")
            }
            OutlinedButton(
                onClick = { CaptureController.stop(context, "app-button") },
                enabled = state.phase == CaptureController.Phase.RECORDING,
            ) { Text("Stop") }
        }
    }

    Section("Nastavení (jednou)") {
        Button(
            onClick = {
                permissions.launch(arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.POST_NOTIFICATIONS))
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("1. Povolit mikrofon a notifikace") }
        Button(
            onClick = {
                val intents = listOf(
                    Intent(AndroidSettings.ACTION_VOICE_INPUT_SETTINGS),
                    Intent(AndroidSettings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS),
                )
                val ok = intents.any { runCatching { context.startActivity(it) }.isSuccess }
                if (!ok) EventLog.add("APP", "could not open assistant settings")
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("2. Nastavit Lab jako digitálního asistenta") }
        Text(
            "3. Nastavení → Systém → Gesta → Podržení vypínače → zvol „Digitální asistent“. " +
                "(Gemini tím přestane být výchozí asistent; vrátíš to stejnou cestou.)",
            style = MaterialTheme.typography.bodyMedium,
        )
    }

    Section("Jak se spouští mikrofon z podržení") {
        Choice(Settings.StartMode.entries, startMode, { it.label }) {
            startMode = it
            Settings.setStartMode(context, it)
            EventLog.add("APP", "start mode → ${it.name}")
        }
        Text(
            "Začni s „Přímo“. Když spuštění na zamčené obrazovce selže, přepni na „Přes aktivitu“ a zkus znovu.",
            style = MaterialTheme.typography.bodySmall,
        )
    }

    Section("Haptika") {
        Choice(Haptics.Usage.entries, usage, { it.label }) {
            usage = it
            Haptics.setUsage(context, it)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Haptics.Pattern.entries.forEach { p ->
                TextButton(onClick = { Haptics.play(context, p) }) { Text(p.label) }
            }
        }
        Text(
            "Vyzkoušej vzorce s mobilem v kapse. Vyber typ, který cítíš nejlíp i při zapnutém Nerušit.",
            style = MaterialTheme.typography.bodySmall,
        )
    }

    Section("Testovací scénáře") {
        Text(
            """
            A. Odemčeno, obrazovka zapnutá: podrž vypínač → start. Podrž znovu 3× → 3 záložky.
            B. Zamčeno, obrazovka zapnutá: totéž.
            C. Zamčeno, obrazovka zhasnutá, mobil v kapse: start + 3 záložky naslepo jen podle vibrací.
            D. Záložka a Stop z notifikace na zamčené obrazovce.
            E. Nahrávání 30+ minut se zhasnutou obrazovkou; pak zkontroluj „tichá okna“ v Reportu.
            F. Když něco selže: přepni způsob spuštění na „Přes aktivitu“ a zopakuj B a C.
            """.trimIndent(),
            style = MaterialTheme.typography.bodyMedium,
        )
    }

    Section("Log událostí") {
        TextButton(onClick = { EventLog.clear() }) { Text("Vymazat log") }
        SelectionContainer {
            Text(
                log.takeLast(80).reversed().joinToString("\n"),
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                lineHeight = 14.sp,
            )
        }
    }
}
