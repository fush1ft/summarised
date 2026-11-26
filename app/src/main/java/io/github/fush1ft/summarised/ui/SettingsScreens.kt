@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package io.github.fush1ft.summarised.ui

import android.Manifest
import android.app.role.RoleManager
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.net.toUri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import io.github.fush1ft.summarised.AppPrefs
import io.github.fush1ft.summarised.capture.Haptics
import io.github.fush1ft.summarised.core.designsystem.component.BigButton
import io.github.fush1ft.summarised.core.designsystem.component.CheckRow
import io.github.fush1ft.summarised.core.designsystem.component.Hint
import io.github.fush1ft.summarised.core.designsystem.component.ScreenHeader
import io.github.fush1ft.summarised.core.designsystem.component.SectionCard
import io.github.fush1ft.summarised.core.designsystem.component.Tone
import kotlinx.coroutines.delay

@Composable
fun OnboardingScreen(onDone: () -> Unit) {
    Spacer(Modifier.height(8.dp))
    Text("Vítej v Summarised", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.SemiBold)
    Text(
        "Nahrávej přednášky a schůzky celé hodiny. Když zazní něco důležitého, podrž vypínač a telefon si to označí. " +
            "Pár krátkých kroků a můžeš začít.",
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    SetupSection(step = 1)
    SummaryModeSection(step = 2)
    TranscriptionSection(step = 3)
    HapticsSection(step = 4)
    BigButton("Hotovo, začít", Icons.Rounded.CheckCircle) { onDone() }
    Text(
        "Všechno půjde později změnit v Nastavení.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    ScreenHeader("Nastavení", null, onBack)
    SetupSection(step = null)
    TranscriptionSection(step = null)
    SummaryModeSection(step = null)
    HapticsSection(step = null)
    val version = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "?"
    }
    Text("Summarised $version", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** Permissions, assistant role and the power-button gesture, with live checkmarks. */
@Composable
fun SetupSection(step: Int?) {
    val context = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    var gestureDone by remember { mutableStateOf(AppPrefs.gestureConfirmed(context)) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(700)
            tick++
        }
    }
    val micOk = remember(tick) { context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED }
    val notifOk = remember(tick) { context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED }
    val assistantOk = remember(tick) { context.getSystemService(RoleManager::class.java).isRoleHeld(RoleManager.ROLE_ASSISTANT) }
    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {}
    val askPermissions = { permissions.launch(arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.POST_NOTIFICATIONS)) }

    SectionCard(
        "Nahrávání vypínačem",
        "Fajfky se zaškrtnou samy.",
        step = step,
        done = micOk && notifOk && assistantOk && gestureDone,
    ) {
        CheckRow(micOk, "Mikrofon", "Aby šlo nahrávat.", "Povolit") { askPermissions() }
        CheckRow(notifOk, "Upozornění", "Nahrávání uvidíš i na zamčené obrazovce.", "Povolit") { askPermissions() }
        CheckRow(assistantOk, "Summarised jako asistent", "V seznamu „Digitální asistent“ vyber Summarised.", "Nastavit") {
            val intents = listOf(Intent(Settings.ACTION_VOICE_INPUT_SETTINGS), Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))
            intents.firstOrNull { runCatching { context.startActivity(it) }.isSuccess }
        }
        CheckRow(
            gestureDone, "Podržení vypínače",
            "Nastavení → Systém → Gesta → Podržení vypínače → Digitální asistent.",
            "Otevřít",
        ) { runCatching { context.startActivity(Intent(Settings.ACTION_SETTINGS)) } }
        if (!gestureDone) {
            TextButton(onClick = {
                gestureDone = true
                AppPrefs.setGestureConfirmed(context)
            }) { Text("Gesto mám nastavené ✓") }
        }
        Hint("Gemini tím přestane být výchozí asistent. Vrátíš ho stejnou cestou.", Icons.Rounded.Info)
    }
}

/** Easy (Groq → Pollinations → phone) or Advanced. */
@Composable
fun SummaryModeSection(step: Int?) {
    val context = LocalContext.current
    var mode by remember { mutableStateOf(AppPrefs.mode(context)) }
    var key by remember { mutableStateOf(AppPrefs.groqKey(context)) }
    var onDeviceOnly by remember { mutableStateOf(AppPrefs.onDeviceOnly(context)) }
    var useGroq by remember { mutableStateOf(AppPrefs.useGroq(context)) }
    var usePollinations by remember { mutableStateOf(AppPrefs.usePollinations(context)) }
    var useLocal by remember { mutableStateOf(AppPrefs.useLocal(context)) }
    var template by remember { mutableStateOf(io.github.fush1ft.summarised.summarise.Prompts.Template.of(AppPrefs.defaultTemplate(context))) }

    SectionCard("Shrnutí", "Kdo ti bude psát shrnutí záložek a nahrávek.", step = step, done = mode == AppPrefs.Mode.ADVANCED || key.isNotBlank()) {
        ModeOption(
            selected = mode == AppPrefs.Mode.EASY,
            icon = Icons.Rounded.Bolt,
            title = "Snadný (doporučeno)",
            text = "Rychlé a přesné shrnutí z internetu: Groq, když zadáš klíč, jinak Pollinations bez registrace. " +
                "Bez internetu shrnuje telefon sám.",
        ) {
            mode = AppPrefs.Mode.EASY
            AppPrefs.setMode(context, mode)
        }
        ModeOption(
            selected = mode == AppPrefs.Mode.ADVANCED,
            icon = Icons.Rounded.Tune,
            title = "Pokročilý",
            text = "Sám si vybereš poskytovatele a pořadí, nebo necháš všechno jen v telefonu.",
        ) {
            mode = AppPrefs.Mode.ADVANCED
            AppPrefs.setMode(context, mode)
        }

        if (mode == AppPrefs.Mode.ADVANCED) {
            ToggleRow("Jen v telefonu", "Nic se neodesílá. Shrnutí je pomalejší a méně přesné.", onDeviceOnly) {
                onDeviceOnly = it
                AppPrefs.setOnDeviceOnly(context, it)
            }
            if (!onDeviceOnly) {
                ToggleRow("Groq", "Nejrychlejší a nejpřesnější, potřebuje klíč.", useGroq) {
                    useGroq = it
                    AppPrefs.setUseGroq(context, it)
                }
                ToggleRow("Pollinations", "Bez registrace, jeden dotaz za 45 s.", usePollinations) {
                    usePollinations = it
                    AppPrefs.setUsePollinations(context, it)
                }
                ToggleRow("Telefon (Gemma 4)", "Záloha bez internetu, pomalá.", useLocal) {
                    useLocal = it
                    AppPrefs.setUseLocal(context, it)
                }
            }
        }

        if (!(mode == AppPrefs.Mode.ADVANCED && (onDeviceOnly || !useGroq))) {
            Text("Klíč Groq (zdarma, nepovinný)", style = MaterialTheme.typography.titleSmall)
            Text(
                "S klíčem je shrnutí hotové za 1–2 sekundy a je nejpřesnější. Bez něj čekáš na Pollinations " +
                    "(jeden dotaz za 45 s, 100 denně).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = key,
                onValueChange = {
                    key = it.trim()
                    AppPrefs.setGroqKey(context, key)
                },
                label = { Text("gsk_…") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedButton(onClick = {
                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, "https://console.groq.com/keys".toUri())) }
                io.github.fush1ft.summarised.summarise.SummaryManager.kick(context)
            }) { Text("Získat klíč zdarma (1 minuta)") }
        }

        LocalModelRow()

        Text("Výchozí typ nových nahrávek:", style = MaterialTheme.typography.labelLarge)
        io.github.fush1ft.summarised.core.designsystem.component.Choice(
            io.github.fush1ft.summarised.summarise.Prompts.Template.entries,
            template,
            { it.label },
        ) {
            template = it
            AppPrefs.setDefaultTemplate(context, it.name)
        }
        Hint(
            if (mode == AppPrefs.Mode.ADVANCED && onDeviceOnly) {
                "Zvuk, přepis i shrnutí zůstávají v telefonu."
            } else {
                "Zvuk a přepis zůstávají vždy v telefonu. Pro shrnutí se odešle jen text přepisu."
            },
            if (mode == AppPrefs.Mode.ADVANCED && onDeviceOnly) Icons.Rounded.Lock else Icons.Rounded.Cloud,
            Tone.NEUTRAL,
        )
    }
}

@Composable
private fun ToggleRow(title: String, detail: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

/** Optional offline summariser download. */
@Composable
private fun LocalModelRow() {
    val context = LocalContext.current
    val status by io.github.fush1ft.summarised.transcribe.LocalLlm.status.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { io.github.fush1ft.summarised.transcribe.LocalLlm.refresh(context) }
    val local = io.github.fush1ft.summarised.transcribe.LocalLlm
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Shrnutí bez internetu (nepovinné)", style = MaterialTheme.typography.titleSmall)
        when (val st = status) {
            io.github.fush1ft.summarised.transcribe.DownloadableModel.Status.Ready -> Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Gemma 4 E2B je stažená (3,1 GB).", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                TextButton(onClick = { local.delete(context) }) { Text("Smazat", color = MaterialTheme.colorScheme.error) }
            }
            is io.github.fush1ft.summarised.transcribe.DownloadableModel.Status.Downloading -> {
                val f = if (st.total > 0) st.bytes.toFloat() / st.total else 0f
                androidx.compose.material3.LinearProgressIndicator(progress = { f }, modifier = Modifier.fillMaxWidth())
                Text("Stahuji ${st.bytes / 1_000_000} z ${st.total / 1_000_000} MB", style = MaterialTheme.typography.bodySmall)
            }
            else -> {
                Text(
                    "Gemma 4 E2B (Google, Apache-2.0) shrne i bez internetu, ale pomalu (minuty). " +
                        (if (st is io.github.fush1ft.summarised.transcribe.DownloadableModel.Status.Failed) "Stahování se přerušilo: ${st.reason}." else ""),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(onClick = { local.download(context) }) { Text("Stáhnout (3,1 GB)") }
            }
        }
    }
}

@Composable
private fun ModeOption(selected: Boolean, icon: ImageVector, title: String, text: String, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
            Icon(
                if (selected) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                contentDescription = null,
                tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(icon, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                }
                Text(text, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
fun HapticsSection(step: Int?) {
    val context = LocalContext.current
    var usage by remember { mutableStateOf(Haptics.usage(context)) }
    SectionCard("Vibrace", "Kterou ucítíš nejlíp v kapse?", step = step, done = step != null) {
        Haptics.Usage.entries.forEach { u ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = usage == u, onClick = {
                    usage = u
                    AppPrefs.setHapticUsage(context, u.name)
                })
                Text(u.label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                TextButton(onClick = { Haptics.play(context, Haptics.Pattern.BOOKMARK, u) }) {
                    Icon(Icons.Rounded.Vibration, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Zkusit")
                }
            }
        }
        Text(
            "Start = dvě cvaknutí · záložka = jedno ťuknutí · konec = doznívající bzučení",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
