@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package io.github.fush1ft.summarised.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.fush1ft.summarised.capture.formatDuration
import io.github.fush1ft.summarised.core.database.RecordingStatus
import io.github.fush1ft.summarised.core.database.SummarisedDatabase
import io.github.fush1ft.summarised.core.designsystem.component.Collapsible
import io.github.fush1ft.summarised.core.designsystem.component.Hint
import io.github.fush1ft.summarised.core.designsystem.component.Pill
import io.github.fush1ft.summarised.core.designsystem.component.ScreenHeader
import io.github.fush1ft.summarised.core.designsystem.component.SectionCard
import io.github.fush1ft.summarised.core.designsystem.component.Tone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Seconds of audio played before a bookmark, so you hear what led up to it. */
private const val LEAD_IN_MS = 10_000L

@Composable
fun DetailScreen(recordingId: Long, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val db = remember { SummarisedDatabase.get(context) }
    val recording by remember(recordingId) { db.recordingDao().observe(recordingId) }.collectAsStateWithLifecycle(null)
    val chunks by remember(recordingId) { db.audioChunkDao().observe(recordingId) }.collectAsStateWithLifecycle(emptyList())
    val bookmarks by remember(recordingId) { db.bookmarkDao().observe(recordingId) }.collectAsStateWithLifecycle(emptyList())
    val segments by remember(recordingId) { db.segmentDao().observe(recordingId) }.collectAsStateWithLifecycle(emptyList())
    val player = remember { ChunkPlayer() }
    var position by remember { mutableLongStateOf(0L) }
    var playing by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var dragging by remember { mutableStateOf<Float?>(null) }

    DisposableEffect(Unit) { onDispose { player.release() } }
    LaunchedEffect(chunks) { player.setChunks(chunks) }
    LaunchedEffect(Unit) {
        while (true) {
            position = player.positionMs()
            playing = player.isPlaying
            delay(250)
        }
    }

    val r = recording ?: run {
        ScreenHeader("Nahrávka", "Načítám…", onBack)
        return
    }
    val date = remember(r.createdAt) { SimpleDateFormat("d. M. yyyy · HH:mm", Locale.forLanguageTag("cs")).format(Date(r.createdAt)) }
    ScreenHeader(r.title, "$date · ${formatDuration(r.durationMs)} · ${bookmarks.size} záložek", onBack)
    if (r.status == RecordingStatus.INTERRUPTED) {
        Hint("Nahrávání bylo přerušené (pád aplikace nebo vybitá baterie). Zvuk je zachovaný do posledního uložení.", Icons.Rounded.AutoAwesome, Tone.WARN)
    }

    RecordingSummarySection(r, segments, bookmarks)

    // Player
    val total = maxOf(player.totalMs, r.durationMs, 1L)
    SectionCard("Přehrávání") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            FilledIconButton(onClick = { player.toggle() }, modifier = Modifier.size(56.dp)) {
                Icon(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, contentDescription = if (playing) "Pauza" else "Přehrát")
            }
            Spacer(Modifier.width(16.dp))
            Text(
                "${formatDuration(position)} / ${formatDuration(total)}",
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.titleMedium,
            )
        }
        Slider(
            value = dragging ?: (position.toFloat() / total).coerceIn(0f, 1f),
            onValueChange = { dragging = it },
            onValueChangeFinished = {
                dragging?.let { player.playFrom((it * total).toLong()) }
                dragging = null
            },
            modifier = Modifier.fillMaxWidth(),
        )
    }

    TranscriptSection(
        recordingId = r.id,
        durationMs = r.durationMs,
        segments = segments,
        bookmarks = bookmarks,
        recordingNow = r.status == RecordingStatus.RECORDING,
        onPlay = { player.playFrom(it) },
    )

    // Bookmarks
    SectionCard("Záložky", if (bookmarks.isEmpty()) "V téhle nahrávce žádné nejsou." else "Klepnutím přehraješ ${LEAD_IN_MS / 1000} s před záložkou.") {
        bookmarks.forEachIndexed { i, b ->
            if (i > 0) HorizontalDivider()
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Pill("🔖 ${i + 1}", Tone.ACTIVE)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(b.title ?: "Záložka v ${formatDuration(b.atMs)}", style = MaterialTheme.typography.titleMedium)
                    Text("přidaná přes: ${b.createdVia}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { player.playFrom(b.atMs - LEAD_IN_MS) }) {
                    Icon(Icons.Rounded.PlayArrow, contentDescription = "Přehrát záložku ${i + 1}")
                }
                IconButton(onClick = { scope.launch { db.bookmarkDao().delete(b.id) } }) {
                    Icon(Icons.Rounded.Delete, contentDescription = "Smazat záložku ${i + 1}")
                }
            }
            BookmarkSummary(b)
        }
    }

    // Edit / delete
    SectionCard("Upravit") {
        Collapsible("Přejmenovat") {
            var title by remember(r.id) { mutableStateOf(r.title) }
            OutlinedTextField(value = title, onValueChange = { title = it }, singleLine = true, modifier = Modifier.fillMaxWidth())
            TextButton(onClick = { scope.launch { db.recordingDao().rename(r.id, title.trim().ifEmpty { r.title }) } }) { Text("Uložit název") }
        }
        TextButton(
            onClick = {
                if (!confirmDelete) {
                    confirmDelete = true
                } else {
                    player.release()
                    val paths = chunks.map { it.path }
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            paths.forEach { File(it).delete() }
                            db.recordingDao().delete(r.id)
                        }
                        onBack()
                    }
                }
            },
            enabled = r.status != RecordingStatus.RECORDING,
        ) {
            Text(
                if (confirmDelete) "Opravdu smazat nahrávku i se zvukem? Klepni znovu." else "Smazat nahrávku",
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}
