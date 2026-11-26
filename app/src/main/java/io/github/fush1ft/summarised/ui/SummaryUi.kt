@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package io.github.fush1ft.summarised.ui

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.fush1ft.summarised.capture.formatDuration
import io.github.fush1ft.summarised.core.database.BookmarkEntity
import io.github.fush1ft.summarised.core.database.RecordingEntity
import io.github.fush1ft.summarised.core.database.RecordingStatus
import io.github.fush1ft.summarised.core.database.SegmentEntity
import io.github.fush1ft.summarised.core.designsystem.component.Choice
import io.github.fush1ft.summarised.core.designsystem.component.Hint
import io.github.fush1ft.summarised.core.designsystem.component.Pill
import io.github.fush1ft.summarised.core.designsystem.component.SectionCard
import io.github.fush1ft.summarised.core.designsystem.component.Tone
import io.github.fush1ft.summarised.summarise.Prompts
import io.github.fush1ft.summarised.summarise.SummaryManager
import org.json.JSONArray
import org.json.JSONObject

private val sectionLabels = linkedMapOf(
    "body" to "Klíčové body",
    "pojmy" to "Pojmy",
    "otazky_ke_zkousce" to "Možné otázky ke zkoušce",
    "rozhodnuti" to "Rozhodnutí",
    "ukoly" to "Úkoly",
    "otevrene_otazky" to "Otevřené otázky",
    "cisla" to "Čísla a měření",
    "zjisteni" to "Zjištění",
)

private fun bullets(arr: JSONArray): List<String> = (0 until arr.length()).map { i ->
    when (val v = arr.get(i)) {
        is JSONObject -> v.keys().asSequence().map { v.optString(it) }.filter { it.isNotBlank() }.joinToString(" — ")
        else -> v.toString()
    }
}

/** A summary JSON rendered as a readable note. */
@Composable
fun SummaryView(j: JSONObject, showTitle: Boolean = true) {
    if (showTitle) {
        j.optString("nadpis").takeIf { it.isNotBlank() }?.let {
            Text(it, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        }
    }
    j.optString("shrnuti").takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodyLarge) }
    val keys = sectionLabels.keys + j.keys().asSequence().filter { it !in sectionLabels && it != "nadpis" && it != "shrnuti" }
    keys.forEach { key ->
        val arr = j.optJSONArray(key) ?: return@forEach
        if (arr.length() == 0) return@forEach
        Text(sectionLabels[key] ?: key, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 6.dp))
        bullets(arr).forEach { line ->
            Row {
                Text("•", modifier = Modifier.width(16.dp), style = MaterialTheme.typography.bodyMedium)
                Text(line, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

/** Whole-recording summary with template switch, retry and sharing. */
@Composable
fun RecordingSummarySection(r: RecordingEntity, segments: List<SegmentEntity>, bookmarks: List<BookmarkEntity>) {
    val context = LocalContext.current
    val state by SummaryManager.state.collectAsStateWithLifecycle()
    val error = state.errors[SummaryManager.recordingKey(r.id)]
    val json = r.summaryJson?.let { runCatching { JSONObject(it) }.getOrNull() }

    SectionCard("Shrnutí", r.summarisedBy?.let { "Napsal: $it" }) {
        when {
            json != null -> SummaryView(json)
            error != null -> {
                Hint("Shrnutí se nepovedlo: $error", Icons.Rounded.ErrorOutline, Tone.ERROR)
                OutlinedButton(onClick = { SummaryManager.retry(context, r.id) }) {
                    Icon(Icons.Rounded.Refresh, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Zkusit znovu")
                }
            }
            r.status == RecordingStatus.RECORDING -> Text("Shrnutí se napíše po skončení nahrávání.", style = MaterialTheme.typography.bodyMedium)
            segments.isEmpty() -> Text("Shrnutí se napíše, až bude hotový přepis.", style = MaterialTheme.typography.bodyMedium)
            else -> Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(10.dp))
                Text(state.working ?: "Čeká ve frontě…", style = MaterialTheme.typography.bodyMedium)
            }
        }
        if (r.status != RecordingStatus.RECORDING && segments.isNotEmpty()) {
            Text("Typ nahrávky:", style = MaterialTheme.typography.labelLarge)
            Choice(Prompts.Template.entries, Prompts.Template.of(r.template), { it.label }) { t ->
                if (t.name != r.template || json == null) SummaryManager.resummarise(context, r.id, t.name)
            }
            Row {
                if (json != null) {
                    TextButton(onClick = { SummaryManager.resummarise(context, r.id, r.template) }) {
                        Icon(Icons.Rounded.AutoAwesome, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Shrnout znovu")
                    }
                }
                TextButton(onClick = { share(context, r, segments, bookmarks) }) {
                    Icon(Icons.Rounded.Share, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Sdílet")
                }
            }
        }
    }
}

/** One bookmark's summary inside the bookmark list. */
@Composable
fun BookmarkSummary(b: BookmarkEntity) {
    val state by SummaryManager.state.collectAsStateWithLifecycle()
    val json = b.summaryJson?.let { runCatching { JSONObject(it) }.getOrNull() }
    val error = state.errors[SummaryManager.bookmarkKey(b.id)]
    when {
        json != null -> {
            SummaryView(json, showTitle = false)
            b.summarisedBy?.let { Pill(it, Tone.NEUTRAL) }
        }
        error != null -> Text("Shrnutí selhalo: $error", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        else -> Text("Shrnutí přijde, až bude přepsáno okolí záložky.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Shares title, summary, bookmarks and transcript as Markdown text. */
fun share(context: Context, r: RecordingEntity, segments: List<SegmentEntity>, bookmarks: List<BookmarkEntity>) {
    val sb = StringBuilder()
    sb.appendLine("# ${r.title}").appendLine()
    sb.appendLine("Délka ${formatDuration(r.durationMs)} · ${bookmarks.size} záložek").appendLine()
    r.summaryJson?.let { runCatching { JSONObject(it) }.getOrNull() }?.let { j ->
        sb.appendLine("## Shrnutí").appendLine()
        appendJson(sb, j)
    }
    if (bookmarks.isNotEmpty()) {
        sb.appendLine("## Záložky").appendLine()
        bookmarks.forEachIndexed { i, b ->
            sb.appendLine("### ${i + 1}. ${formatDuration(b.atMs)}" + (b.title?.let { " — $it" } ?: "")).appendLine()
            b.summaryJson?.let { runCatching { JSONObject(it) }.getOrNull() }?.let { appendJson(sb, it, withTitle = false) }
        }
    }
    if (segments.isNotEmpty()) {
        sb.appendLine("## Přepis").appendLine()
        segments.forEach { sb.appendLine("[${formatDuration(it.startMs)}] ${it.text}") }
    }
    val text = sb.toString().take(400_000)
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_SUBJECT, r.title).putExtra(Intent.EXTRA_TEXT, text)
    context.startActivity(Intent.createChooser(send, "Sdílet nahrávku").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

private fun appendJson(sb: StringBuilder, j: JSONObject, withTitle: Boolean = true) {
    if (withTitle) j.optString("nadpis").takeIf { it.isNotBlank() }?.let { sb.appendLine("**$it**").appendLine() }
    j.optString("shrnuti").takeIf { it.isNotBlank() }?.let { sb.appendLine(it).appendLine() }
    val keys = sectionLabels.keys + j.keys().asSequence().filter { it !in sectionLabels && it != "nadpis" && it != "shrnuti" }
    keys.forEach { key ->
        val arr = j.optJSONArray(key) ?: return@forEach
        if (arr.length() == 0) return@forEach
        sb.appendLine("*${sectionLabels[key] ?: key}*")
        bullets(arr).forEach { sb.appendLine("- $it") }
        sb.appendLine()
    }
}
