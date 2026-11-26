@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package io.github.fush1ft.summarised.ui

import android.net.ConnectivityManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.SignalCellularAlt
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.fush1ft.summarised.AppPrefs
import io.github.fush1ft.summarised.capture.formatDuration
import io.github.fush1ft.summarised.core.database.BookmarkEntity
import io.github.fush1ft.summarised.core.database.SegmentEntity
import io.github.fush1ft.summarised.core.designsystem.component.BigButton
import io.github.fush1ft.summarised.core.designsystem.component.Hint
import io.github.fush1ft.summarised.core.designsystem.component.Pill
import io.github.fush1ft.summarised.core.designsystem.component.SectionCard
import io.github.fush1ft.summarised.core.designsystem.component.Tone
import io.github.fush1ft.summarised.transcribe.DownloadableModel
import io.github.fush1ft.summarised.transcribe.SpeechModel
import io.github.fush1ft.summarised.transcribe.TranscriptionManager

/** Speech-model download and the live-transcription switch (settings and first run). */
@Composable
fun TranscriptionSection(step: Int?) {
    val context = LocalContext.current
    val status by SpeechModel.status.collectAsStateWithLifecycle()
    var live by remember { mutableStateOf(AppPrefs.liveTranscription(context)) }
    LaunchedEffect(Unit) { SpeechModel.refresh(context) }
    val metered = remember { context.getSystemService(ConnectivityManager::class.java).isActiveNetworkMetered }

    SectionCard(
        "Přepis řeči",
        "Parakeet přepisuje češtinu přímo v telefonu, 7–8× rychleji než mluvíš. Zvuk nikam neodchází.",
        step = step,
        done = status is DownloadableModel.Status.Ready,
    ) {
        when (val s = status) {
            DownloadableModel.Status.Ready -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Pill("Model stažený", Tone.DONE)
                    Spacer(Modifier.width(8.dp))
                    Text("${SpeechModel.APPROX_MB} MB", style = MaterialTheme.typography.bodySmall)
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Přepisovat během nahrávání", style = MaterialTheme.typography.titleMedium)
                        Text("Po skončení je přepis hned hotový. Vypni, když šetříš baterii.", style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(checked = live, onCheckedChange = {
                        live = it
                        AppPrefs.setLiveTranscription(context, it)
                    })
                }
                if (step == null) {
                    TextButton(onClick = { SpeechModel.delete(context) }) {
                        Text("Smazat model", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
            is DownloadableModel.Status.Downloading -> {
                val f = if (s.total > 0) s.bytes.toFloat() / s.total else 0f
                LinearProgressIndicator(progress = { f }, modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(50)))
                Text("Stahuji ${s.bytes / 1_000_000} z ${s.total / 1_000_000} MB", style = MaterialTheme.typography.bodySmall)
            }
            is DownloadableModel.Status.Failed, DownloadableModel.Status.Missing -> {
                if (s is DownloadableModel.Status.Failed) {
                    Hint("Stahování se přerušilo: ${s.reason}. Pokračuje od místa, kde skončilo.", Icons.Rounded.Info, Tone.ERROR)
                }
                if (metered) Hint("Jsi na mobilních datech. Doporučuju počkat na Wi-Fi.", Icons.Rounded.SignalCellularAlt, Tone.WARN)
                BigButton(
                    if (s is DownloadableModel.Status.Failed) "Pokračovat ve stahování" else "Stáhnout model (${SpeechModel.APPROX_MB} MB)",
                    Icons.Rounded.Download,
                    tonal = step == null,
                ) { SpeechModel.download(context) }
                Text("NVIDIA Parakeet TDT 0.6B v3 · licence CC-BY-4.0 · z Hugging Face", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** The transcript of a recording, with bookmarks shown where they fall. Tap a line to play it. */
@Composable
fun TranscriptSection(
    recordingId: Long,
    durationMs: Long,
    segments: List<SegmentEntity>,
    bookmarks: List<BookmarkEntity>,
    recordingNow: Boolean,
    onPlay: (Long) -> Unit,
) {
    val context = LocalContext.current
    val progress by TranscriptionManager.progress.collectAsStateWithLifecycle()
    val modelStatus by SpeechModel.status.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { SpeechModel.refresh(context) }
    val job = progress[recordingId]

    SectionCard(
        "Přepis",
        when {
            job?.error != null -> null
            job != null && job.live -> "Přepisuje se během nahrávání…"
            job != null -> "Přepisuji… ${if (job.totalMs > 0) "${job.doneMs * 100 / job.totalMs} %" else ""}"
            segments.isEmpty() -> "Zatím bez přepisu."
            else -> "Klepni na větu a přehraje se."
        },
    ) {
        if (job != null && job.error == null && !job.live) {
            val f = if (job.totalMs > 0) job.doneMs.toFloat() / job.totalMs else 0f
            LinearProgressIndicator(progress = { f }, modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(50)))
        }
        if (job?.error != null) {
            Hint("Přepis selhal: ${job.error}", Icons.Rounded.Info, Tone.ERROR)
            TextButton(onClick = { TranscriptionManager.dismissError(recordingId) }) { Text("Zavřít") }
        }

        // Transcript lines with bookmarks interleaved by time.
        var b = 0
        segments.forEach { seg ->
            while (b < bookmarks.size && bookmarks[b].atMs <= seg.startMs) {
                BookmarkMarker(b + 1, bookmarks[b].atMs) { onPlay(bookmarks[it - 1].atMs - 10_000) }
                b++
            }
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onPlay(seg.startMs) }.padding(vertical = 6.dp, horizontal = 4.dp),
            ) {
                Text(
                    formatDuration(seg.startMs),
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.width(56.dp).padding(top = 2.dp),
                )
                Text(seg.text, style = MaterialTheme.typography.bodyLarge)
            }
        }
        if (segments.isNotEmpty()) {
            while (b < bookmarks.size) {
                BookmarkMarker(b + 1, bookmarks[b].atMs) { onPlay(bookmarks[it - 1].atMs - 10_000) }
                b++
            }
        }

        if (job == null && !recordingNow) {
            when {
                modelStatus !is DownloadableModel.Status.Ready -> Hint(
                    "Pro přepis je potřeba stáhnout model (${SpeechModel.APPROX_MB} MB) v Nastavení → Přepis řeči.",
                    Icons.Rounded.Download,
                    Tone.WARN,
                )
                segments.isEmpty() -> BigButton("Přepsat nahrávku", Icons.Rounded.GraphicEq) {
                    TranscriptionManager.transcribeRecording(context, recordingId)
                }
                else -> TextButton(onClick = { TranscriptionManager.transcribeRecording(context, recordingId) }) {
                    Text("Přepsat znovu (${formatDuration(durationMs)} zvuku)")
                }
            }
        }
    }
}

@Composable
private fun BookmarkMarker(number: Int, atMs: Long, onPlay: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onPlay(number) }.padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Pill("🔖 Záložka $number", Tone.ACTIVE)
        Text(formatDuration(atMs), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium)
    }
}
