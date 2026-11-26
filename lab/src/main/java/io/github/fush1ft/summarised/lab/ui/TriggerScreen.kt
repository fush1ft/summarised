@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package io.github.fush1ft.summarised.lab.ui

import android.Manifest
import android.app.role.RoleManager
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.ThumbDown
import androidx.compose.material.icons.rounded.ThumbUp
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.fush1ft.summarised.lab.capture.CaptureController
import io.github.fush1ft.summarised.lab.capture.Haptics
import io.github.fush1ft.summarised.lab.capture.Scenarios
import io.github.fush1ft.summarised.lab.capture.Settings
import io.github.fush1ft.summarised.lab.log.EventLog
import io.github.fush1ft.summarised.lab.log.Results
import kotlinx.coroutines.delay

@Composable
fun TriggerScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val state by CaptureController.state.collectAsStateWithLifecycle()
    val results by Results.all.collectAsStateWithLifecycle()
    val log by EventLog.lines.collectAsStateWithLifecycle()
    var elapsed by remember { mutableLongStateOf(0L) }
    var tick by remember { mutableIntStateOf(0) }
    var startMode by remember { mutableStateOf(Settings.startMode(context)) }
    var usage by remember { mutableStateOf(Haptics.usage(context)) }
    var gestureDone by remember { mutableStateOf(Settings.prefs(context).getBoolean("gesture_done", false)) }

    // Re-check permissions and the assistant role whenever we come back from Settings.
    LaunchedEffect(Unit) {
        while (true) {
            elapsed = state.elapsedMs()
            tick++
            delay(500)
        }
    }
    val micOk = remember(tick) { context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED }
    val notifOk = remember(tick) { context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED }
    val assistantOk = remember(tick / 4) { context.getSystemService(RoleManager::class.java).isRoleHeld(RoleManager.ROLE_ASSISTANT) }
    val verdicts = remember(results) { Scenarios.verdicts() }

    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
        EventLog.add("APP", "oprávnění: " + r.entries.joinToString { "${it.key.substringAfterLast('.')}=${it.value}" })
    }

    ScreenHeader(
        "Tlačítko zapínání",
        "Nejdřív telefon jednou nastavíme, pak vyzkoušíš pět situací a u každé klepneš, jestli fungovala.",
        onBack,
    )

    // Live status
    Surface(
        color = if (state.phase == CaptureController.Phase.RECORDING) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(28.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            when (state.phase) {
                CaptureController.Phase.RECORDING -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        PulsingDot(16)
                        Spacer(Modifier.width(12.dp))
                        Text("Nahrávám", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    }
                    Text(
                        CaptureController.formatMs(elapsed),
                        style = MaterialTheme.typography.displayMedium,
                        fontFamily = FontFamily.Monospace,
                    )
                    Text(
                        if (state.bookmarksMs.isEmpty()) "Zatím žádná záložka. Podrž vypínač a jedna přibude."
                        else "Záložky: " + state.bookmarksMs.joinToString("  ") { CaptureController.formatMs(it) },
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    if (state.silenced) {
                        Hint("Systém posílá ticho: Android zablokoval mikrofon. Napiš to do výsledků.", Icons.Rounded.Warning, Tone.ERROR)
                    }
                    ButtonPair(
                        left = {
                            FilledTonalButton(
                                onClick = {
                                    if (CaptureController.onTrigger(context, "app") is CaptureController.Outcome.Bookmarked) {
                                        Haptics.play(context, Haptics.Pattern.BOOKMARK)
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                            ) {
                                Icon(Icons.Rounded.Bookmark, null)
                                Spacer(Modifier.width(8.dp))
                                Text("Záložka")
                            }
                        },
                        right = {
                            Button(
                                onClick = { CaptureController.stop(context, "app") },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                            ) {
                                Icon(Icons.Rounded.Stop, null)
                                Spacer(Modifier.width(8.dp))
                                Text("Zastavit")
                            }
                        },
                    )
                }
                CaptureController.Phase.STARTING -> Text("Spouštím…", style = MaterialTheme.typography.titleLarge)
                CaptureController.Phase.FAILED -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.ErrorOutline, null, tint = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.width(8.dp))
                        Text("Spuštění selhalo", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    }
                    Text(state.error ?: "", style = MaterialTheme.typography.bodyMedium)
                    BigButton("Zkusit znovu z aplikace", Icons.Rounded.PlayArrow, tonal = true) { CaptureController.onTrigger(context, "app") }
                }
                CaptureController.Phase.IDLE -> {
                    Text("Připraveno", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Nahrávání se spouští podržením vypínače. Pro kontrolu ho můžeš spustit i odsud.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    BigButton("Spustit z aplikace", Icons.Rounded.PlayArrow, tonal = true) { CaptureController.onTrigger(context, "app") }
                }
            }
        }
    }

    // Setup
    val setupDone = micOk && notifOk && assistantOk && gestureDone
    SectionCard("Příprava", "Jednou a hotovo. Fajfky se zaškrtnou samy.", step = 1, done = setupDone) {
        CheckRow(micOk, "Mikrofon", "Aby šlo nahrávat.", "Povolit") {
            permissions.launch(arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.POST_NOTIFICATIONS))
        }
        CheckRow(notifOk, "Upozornění", "Ukáže nahrávání na zamčené obrazovce.", "Povolit") {
            permissions.launch(arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.POST_NOTIFICATIONS))
        }
        CheckRow(assistantOk, "Lab jako asistent", "V seznamu „Digitální asistent“ vyber Summarised Lab.", "Nastavit") {
            val intents = listOf(Intent(AndroidSettings.ACTION_VOICE_INPUT_SETTINGS), Intent(AndroidSettings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))
            if (intents.none { runCatching { context.startActivity(it) }.isSuccess }) EventLog.add("APP", "nastavení asistenta nejde otevřít")
        }
        CheckRow(
            gestureDone, "Podržení vypínače",
            "Nastavení → Systém → Gesta → Podržení vypínače → Digitální asistent.",
            "Otevřít",
        ) { runCatching { context.startActivity(Intent(AndroidSettings.ACTION_SETTINGS)) } }
        if (!gestureDone) {
            TextButton(onClick = {
                gestureDone = true
                Settings.prefs(context).edit().putBoolean("gesture_done", true).apply()
            }) { Text("Gesto mám nastavené ✓") }
        }
        Hint(
            "Gemini tím přestane být výchozí asistent. Po testech ho vrátíš stejnou cestou.",
            Icons.Rounded.Info,
        )
    }

    // Haptics
    SectionCard("Vibrace", "Kterou ucítíš nejlíp i v kapse? Zkus je a jednu vyber.", step = 2, done = verdicts.isNotEmpty()) {
        Haptics.Usage.entries.forEach { u ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = usage == u, onClick = {
                    usage = u
                    Haptics.setUsage(context, u)
                })
                Text(usageLabel(u), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                TextButton(onClick = {
                    val previous = Haptics.usage(context)
                    Haptics.setUsage(context, u)
                    Haptics.play(context, Haptics.Pattern.BOOKMARK)
                    Haptics.setUsage(context, previous)
                }) {
                    Icon(Icons.Rounded.Vibration, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Zkusit")
                }
            }
        }
        Text("Vzorce: start = dvě cvaknutí · záložka = jedno ťuknutí · stop = doznívající bzučení", style = MaterialTheme.typography.bodySmall)
    }

    // Scenarios
    SectionCard(
        "Testy",
        "${verdicts.size} z ${Scenarios.all.size} vyzkoušeno. Pátý (30 minut) je nepovinný.",
        step = 3,
        done = verdicts.size >= Scenarios.all.size - 1,
    ) {
        Scenarios.all.forEachIndexed { i, s ->
            if (i > 0) HorizontalDivider()
            ScenarioItem(s, verdicts[s.id]) { ok ->
                Scenarios.record(s.id, ok, Settings.startMode(context).name)
            }
        }
        if (verdicts.values.any { !it } && startMode == Settings.StartMode.DIRECT) {
            Hint(
                "Něco nefungovalo? Zkus záložní způsob spuštění a zopakuj neúspěšné testy.",
                Icons.Rounded.Lightbulb,
                Tone.WARN,
            )
            BigButton("Přepnout na záložní způsob", null, tonal = true) {
                startMode = Settings.StartMode.TRAMPOLINE
                Settings.setStartMode(context, Settings.StartMode.TRAMPOLINE)
                EventLog.add("APP", "způsob spuštění → TRAMPOLINE")
            }
        }
    }

    // Advanced
    SectionCard("Pro pokročilé") {
        Collapsible("Způsob spuštění mikrofonu") {
            Choice(Settings.StartMode.entries, startMode, { it.label }) {
                startMode = it
                Settings.setStartMode(context, it)
                EventLog.add("APP", "způsob spuštění → ${it.name}")
            }
        }
        Collapsible("Záznam událostí (${log.size})") {
            TextButton(onClick = { EventLog.clear() }) { Text("Vymazat") }
            SelectionContainer {
                Text(
                    log.takeLast(80).reversed().joinToString("\n"),
                    fontFamily = FontFamily.Monospace, fontSize = 11.sp, lineHeight = 14.sp,
                )
            }
        }
    }
}

@Composable
private fun ScenarioItem(s: Scenarios.Scenario, verdict: Boolean?, onVerdict: (Boolean) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val (bg, fg) = toneColors(
                when (verdict) {
                    true -> Tone.DONE
                    false -> Tone.ERROR
                    null -> Tone.NEUTRAL
                },
            )
            Surface(color = bg, contentColor = fg, shape = CircleShape) {
                Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) {
                    Text(s.id, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.width(12.dp))
            Text(s.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            when (verdict) {
                true -> Pill("Funguje", Tone.DONE)
                false -> Pill("Nefunguje", Tone.ERROR)
                null -> Unit
            }
        }
        Text(s.steps, style = MaterialTheme.typography.bodyMedium)
        Text("Co má nastat: ${s.expect}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        ButtonPair(
            left = {
                if (verdict == true) {
                    Button(onClick = { onVerdict(true) }, modifier = Modifier.fillMaxWidth()) { VerdictLabel(true) }
                } else {
                    OutlinedButton(onClick = { onVerdict(true) }, modifier = Modifier.fillMaxWidth()) { VerdictLabel(true) }
                }
            },
            right = {
                if (verdict == false) {
                    Button(
                        onClick = { onVerdict(false) },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.fillMaxWidth(),
                    ) { VerdictLabel(false) }
                } else {
                    OutlinedButton(onClick = { onVerdict(false) }, modifier = Modifier.fillMaxWidth()) { VerdictLabel(false) }
                }
            },
        )
    }
}

@Composable
private fun VerdictLabel(ok: Boolean) {
    Icon(if (ok) Icons.Rounded.ThumbUp else Icons.Rounded.ThumbDown, null, modifier = Modifier.size(18.dp))
    Spacer(Modifier.width(8.dp))
    Text(if (ok) "Funguje" else "Nefunguje")
}

private fun usageLabel(u: Haptics.Usage) = when (u) {
    Haptics.Usage.ALARM -> "Silná (jako budík)"
    Haptics.Usage.NOTIFICATION -> "Jako upozornění"
    Haptics.Usage.TOUCH -> "Jemná (jako klávesnice)"
    Haptics.Usage.ACCESSIBILITY -> "Přístupnost"
    Haptics.Usage.MEDIA -> "Jako média"
}
