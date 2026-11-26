@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package io.github.fush1ft.summarised.ui

import android.Manifest
import android.app.role.RoleManager
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.fush1ft.summarised.capture.CaptureController
import io.github.fush1ft.summarised.capture.Haptics
import io.github.fush1ft.summarised.capture.formatDuration
import io.github.fush1ft.summarised.core.database.RecordingEntity
import io.github.fush1ft.summarised.core.database.RecordingStatus
import io.github.fush1ft.summarised.core.database.SummarisedDatabase
import io.github.fush1ft.summarised.core.designsystem.component.BigButton
import io.github.fush1ft.summarised.core.designsystem.component.ButtonPair
import io.github.fush1ft.summarised.core.designsystem.component.Hint
import io.github.fush1ft.summarised.core.designsystem.component.IconBadge
import io.github.fush1ft.summarised.core.designsystem.component.Pill
import io.github.fush1ft.summarised.core.designsystem.component.PulsingDot
import io.github.fush1ft.summarised.core.designsystem.component.SectionCard
import io.github.fush1ft.summarised.core.designsystem.component.Tone
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HomeScreen(onOpen: (Long) -> Unit, onSettings: () -> Unit) {
    val context = LocalContext.current
    val db = remember { SummarisedDatabase.get(context) }
    val recordings by remember { db.recordingDao().observeAll() }.collectAsStateWithLifecycle(emptyList())
    val counts by remember { db.bookmarkDao().observeCounts() }.collectAsStateWithLifecycle(emptyList())
    val assistantOk = remember { context.getSystemService(RoleManager::class.java).isRoleHeld(RoleManager.ROLE_ASSISTANT) }

    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("Summarised", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        IconButton(onClick = onSettings) { Icon(Icons.Rounded.Settings, contentDescription = "Nastavení") }
    }
    if (!assistantOk) {
        Hint(
            "Podržení vypínače zatím nefunguje: Summarised není výchozí asistent. Oprav to v Nastavení.",
            Icons.Rounded.PowerSettingsNew,
            Tone.WARN,
        )
    }

    RecordCard()

    if (recordings.isEmpty()) {
        SectionCard("Nahrávky", "Zatím žádné. Podrž vypínač, nebo klepni na Začít nahrávat.") {}
    } else {
        Text("Nahrávky", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        recordings.forEach { r ->
            RecordingRow(r, counts.firstOrNull { it.recordingId == r.id }?.count ?: 0) { onOpen(r.id) }
        }
    }
}

@Composable
private fun RecordCard() {
    val context = LocalContext.current
    val state by CaptureController.state.collectAsStateWithLifecycle()
    var elapsed by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        while (true) {
            elapsed = CaptureController.state.value.elapsedMs()
            delay(500)
        }
    }
    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) CaptureController.onTrigger(context, "aplikace")
    }
    val start = {
        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            CaptureController.onTrigger(context, "aplikace")
        } else {
            micPermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }
    val recording = state.phase == CaptureController.Phase.RECORDING
    Surface(
        color = if (recording) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
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
                    Text(formatDuration(elapsed), style = MaterialTheme.typography.displayMedium, fontFamily = FontFamily.Monospace)
                    Text(
                        if (state.bookmarks.isEmpty()) "Podrž vypínač a přibude záložka."
                        else "Záložky: " + state.bookmarks.joinToString("  ") { formatDuration(it.atMs) },
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    if (state.silenced) {
                        Hint("Android posílá ticho: mikrofon je zablokovaný jinou aplikací.", Icons.Rounded.Warning, Tone.ERROR)
                    }
                    ButtonPair(
                        left = {
                            FilledTonalButton(
                                onClick = {
                                    if (CaptureController.addBookmark(context, "aplikace") is CaptureController.Outcome.Bookmarked) {
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
                                onClick = { CaptureController.stop(context) },
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
                CaptureController.Phase.FAILED, CaptureController.Phase.IDLE -> {
                    if (state.phase == CaptureController.Phase.FAILED) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.ErrorOutline, null, tint = MaterialTheme.colorScheme.error)
                            Spacer(Modifier.width(8.dp))
                            Text(state.error ?: "Nahrávání selhalo", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    Text("Připraveno", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Podrž vypínač, i se zamčeným telefonem. Nebo začni odsud.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    BigButton("Začít nahrávat", Icons.Rounded.Mic) { start() }
                }
            }
        }
    }
}

@Composable
private fun RecordingRow(r: RecordingEntity, bookmarks: Int, onClick: () -> Unit) {
    val date = remember(r.createdAt) { SimpleDateFormat("d. M. yyyy · HH:mm", Locale.forLanguageTag("cs")).format(Date(r.createdAt)) }
    ElevatedCard(onClick = onClick, shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBadge(Icons.Rounded.Mic, if (r.status == RecordingStatus.RECORDING) Tone.ERROR else Tone.ACTIVE, size = 44)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(r.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text("$date · ${formatDuration(r.durationMs)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (bookmarks > 0) Pill("🔖 $bookmarks", Tone.ACTIVE)
                    when (r.status) {
                        RecordingStatus.RECORDING -> Pill("● nahrává se", Tone.ERROR)
                        RecordingStatus.INTERRUPTED -> Pill("přerušeno", Tone.WARN)
                        RecordingStatus.READY -> Unit
                    }
                }
            }
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
