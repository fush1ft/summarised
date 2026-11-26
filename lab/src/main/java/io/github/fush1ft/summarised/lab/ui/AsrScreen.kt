@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package io.github.fush1ft.summarised.lab.ui

import android.Manifest
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SignalCellularAlt
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.fush1ft.summarised.lab.bench.BenchRecorder
import io.github.fush1ft.summarised.lab.bench.BenchRunner
import io.github.fush1ft.summarised.lab.bench.ModelKind
import io.github.fush1ft.summarised.lab.bench.Models
import io.github.fush1ft.summarised.lab.bench.Texts
import io.github.fush1ft.summarised.lab.capture.CaptureController
import io.github.fush1ft.summarised.lab.log.Results
import kotlinx.coroutines.launch
import org.json.JSONObject

@Composable
fun AsrScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val installed by Models.installed.collectAsStateWithLifecycle()
    val progress by Models.progress.collectAsStateWithLifecycle()
    val recordingId by BenchRecorder.recordingId.collectAsStateWithLifecycle()
    val elapsed by BenchRecorder.elapsedMs.collectAsStateWithLifecycle()
    val level by BenchRecorder.level.collectAsStateWithLifecycle()
    val busy by BenchRunner.busy.collectAsStateWithLifecycle()
    val results by Results.all.collectAsStateWithLifecycle()
    // Open on the text being recorded, if any, so a recording left running
    // in the background is never hidden behind a different text.
    var passage by remember { mutableStateOf(Texts.passages.firstOrNull { it.id == BenchRecorder.recordingId.value } ?: Texts.passages.first()) }
    // Reading a text only makes sense on this screen: leaving it ends the take.
    DisposableEffect(Unit) { onDispose { BenchRecorder.stop() } }
    var threads by remember { mutableIntStateOf(4) }
    val downloading = progress.values.any { it.active }
    KeepScreenOn(busy != null || recordingId != null || downloading)

    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    val specs = Models.catalog.filter { it.kind != ModelKind.LLM }
    val recommended = specs.filter { it.recommended }
    val asrFiles = installed.filter { Models.kindOf(it) != ModelKind.LLM }
    val recorded = Texts.passages.filter { recordingId != it.id && BenchRecorder.sampleFile(context, it.id).exists() }
    val metered = remember { context.getSystemService(ConnectivityManager::class.java).isActiveNetworkMetered }

    ScreenHeader(
        "Přepis řeči",
        "Tři kroky: stáhnout modely, nahrát se a spustit test. Pak uvidíš, který model tě přepsal nejlíp.",
        onBack,
    )

    // 1. Models
    val missing = recommended.filter { spec -> installed.none { it.name == spec.localName } }
    SectionCard(
        "Stáhni modely",
        "Stáhnou se jednou a zůstanou v telefonu.",
        step = 1,
        done = missing.isEmpty(),
    ) {
        if (metered && missing.isNotEmpty()) {
            Hint("Jsi na mobilních datech. Doporučuju počkat na Wi-Fi.", Icons.Rounded.SignalCellularAlt, Tone.WARN)
        }
        recommended.forEach { spec ->
            ModelRow(spec, installed.any { it.name == spec.localName }, progress[spec.id]) {
                Models.downloadAll(context, listOf(spec))
            }
        }
        if (missing.isNotEmpty()) {
            BigButton(
                "Stáhnout vše (${formatMb(missing.sumOf { it.approxMb })})",
                Icons.Rounded.Download,
                enabled = missing.none { progress[it.id]?.active == true },
            ) { Models.downloadAll(context, missing) }
        }
        Collapsible("Další modely") {
            specs.filter { !it.recommended }.forEach { spec ->
                ModelRow(spec, installed.any { it.name == spec.localName }, progress[spec.id]) {
                    Models.downloadAll(context, listOf(spec))
                }
            }
        }
    }

    // 2. Recordings
    SectionCard(
        "Nahraj se",
        "Přečti nahlas každý text. Mluv normálně, přeřeknutí nevadí.",
        step = 2,
        done = recorded.size == Texts.passages.size,
    ) {
        Choice(Texts.passages, passage, { p ->
            (if (p in recorded) "✓ " else "") + p.title.substringBefore(':')
        }) { if (recordingId == null) passage = it }
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                passage.text,
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 19.sp, lineHeight = 29.sp),
                modifier = Modifier.padding(18.dp),
            )
        }
        if (recordingId == passage.id) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PulsingDot(14)
                Spacer(Modifier.width(10.dp))
                Text(
                    "Nahrávám ${CaptureController.formatMs(elapsed).removePrefix("0:")}",
                    style = MaterialTheme.typography.titleMedium,
                    fontFamily = FontFamily.Monospace,
                )
            }
            LevelMeter(level)
            BigButton("Hotovo", Icons.Rounded.Stop) { BenchRecorder.stop() }
        } else {
            BigButton(
                if (passage in recorded) "Nahrát znovu" else "Začít číst",
                Icons.Rounded.Mic,
                enabled = recordingId == null && busy == null,
                tonal = passage in recorded,
            ) {
                if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                    micPermission.launch(Manifest.permission.RECORD_AUDIO)
                } else {
                    val id = passage.id
                    Models.scope.launch { BenchRecorder.record(context.applicationContext, id) }
                }
            }
            // Never disable silently: say why, and offer the way out.
            val b = busy
            val otherRecording = recordingId
            if (b != null) {
                Hint(
                    "Nahrávat teď nejde, na pozadí běží test modelů: ${b.title} (${b.step} z ${b.total}). " +
                        "Počkej, až doběhne, nebo ho zruš.",
                    Icons.Rounded.Info,
                    Tone.WARN,
                )
                OutlinedButton(onClick = { BenchRunner.cancel() }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.Close, null)
                    Spacer(Modifier.width(6.dp))
                    Text("Zrušit běžící test")
                }
            } else if (otherRecording != null) {
                val title = Texts.passages.firstOrNull { it.id == otherRecording }?.title ?: otherRecording
                Hint("Právě se nahrává jiný text: $title.", Icons.Rounded.Info, Tone.WARN)
                OutlinedButton(onClick = { BenchRecorder.stop() }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.Stop, null)
                    Spacer(Modifier.width(6.dp))
                    Text("Ukončit tu nahrávku")
                }
            }
            if (passage in recorded) {
                val next = Texts.passages.firstOrNull { it !in recorded }
                if (next != null) {
                    OutlinedButton(onClick = { passage = next }, modifier = Modifier.fillMaxWidth()) { Text("Další text: ${next.title}") }
                }
            }
        }
    }

    // 3. Run
    val runs = recorded.size * asrFiles.size
    SectionCard(
        "Spusť test",
        "Každý model přepíše každou nahrávku. Může to pár minut trvat.",
        step = 3,
        done = results.any { it.optString("kind") == "asr" },
    ) {
        val b = busy
        if (b == null) {
            BigButton(
                if (runs == 0) "Nejdřív stáhni model a nahraj text" else "Spustit test ($runs přepisů)",
                Icons.Rounded.PlayArrow,
                enabled = runs > 0 && recordingId == null,
            ) { BenchRunner.runAsr(context, asrFiles, threads) }
        } else {
            Text("${b.step} z ${b.total}: ${b.title}", style = MaterialTheme.typography.titleMedium)
            LinearProgressIndicator(
                progress = { (b.step - 1f) / b.total },
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(50)),
            )
            OutlinedButton(onClick = { BenchRunner.cancel() }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Rounded.Close, null)
                Spacer(Modifier.width(6.dp))
                Text("Zrušit")
            }
        }
        Collapsible("Pro pokročilé") {
            Text("Počet vláken procesoru:")
            Choice(listOf(2, 4, 6, 8), threads, { "$it" }) { threads = it }
        }
    }

    // Results
    val asr = results.filter { it.optString("kind") == "asr" }
    if (asr.isNotEmpty()) AsrResults(asr)
    results.filter { it.optString("kind") == "asr_error" }.takeLast(3).forEach {
        Hint("${it.optString("model")}: ${it.optString("error")}", Icons.Rounded.Info, Tone.ERROR)
    }
}

private data class ModelScore(val model: String, val rtf: Double, val wer: Double, val runs: List<JSONObject>)

@Composable
private fun AsrResults(asr: List<JSONObject>) {
    // Latest run per (model, passage), averaged per model.
    val scores = asr.groupBy { it.optString("model") }.map { (model, runs) ->
        val latest = runs.groupBy { it.optString("passage") }.map { it.value.last() }
        // Prefer the number-insensitive WER (digits vs. spoken numbers are not real errors).
        ModelScore(
            model,
            latest.map { it.optDouble("rtf") }.average(),
            latest.map { if (it.has("wer_nonum")) it.optDouble("wer_nonum") else it.optDouble("wer") }.average(),
            latest,
        )
    }.sortedBy { it.wer }
    val mostAccurate = scores.minByOrNull { it.wer }?.model
    val fastest = scores.minByOrNull { it.rtf }?.model

    Text("Výsledky", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))
    scores.forEach { s ->
        Card(
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(s.model, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    if (s.model == mostAccurate) Pill("🏆 nejpřesnější", Tone.DONE)
                    if (s.model == fastest) Pill("⚡ nejrychlejší", Tone.ACTIVE)
                }
                MetricBar(
                    "Přesnost",
                    "${((1 - s.wer) * 100).toInt().coerceAtLeast(0)} % slov správně (bez čísel)",
                    (1 - s.wer).toFloat(),
                    highlight = s.model == mostAccurate,
                )
                MetricBar(
                    "Rychlost",
                    "minuta řeči za ${"%.0f".format(s.rtf * 60)} s",
                    (1 - s.rtf).toFloat(),
                    highlight = s.model == fastest,
                )
                if (s.rtf > 0.6) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Bolt, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.padding(end = 6.dp))
                        Text("Na přepis během nahrávání je pomalý.", style = MaterialTheme.typography.bodySmall)
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.CheckCircle, null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.padding(end = 6.dp))
                        Text("Stihne přepisovat už během nahrávání.", style = MaterialTheme.typography.bodySmall)
                    }
                }
                Collapsible("Co přesně napsal") {
                    s.runs.forEachIndexed { i, r ->
                        if (i > 0) HorizontalDivider()
                        val title = Texts.passages.firstOrNull { it.id == r.optString("passage") }?.title ?: r.optString("passage")
                        Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        Text(r.optString("text").trim(), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
    if (scores.size > 1) {
        Hint(
            "Ideální je model, který je přesný a zároveň stihne přepisovat během nahrávání.",
            Icons.Rounded.EmojiEvents,
        )
    }
}
