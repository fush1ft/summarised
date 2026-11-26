@file:OptIn(ExperimentalMaterial3Api::class)

package io.github.fush1ft.summarised.lab.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.fush1ft.summarised.lab.bench.BenchRecorder
import io.github.fush1ft.summarised.lab.bench.BenchRunner
import io.github.fush1ft.summarised.lab.bench.ModelKind
import io.github.fush1ft.summarised.lab.bench.Models
import io.github.fush1ft.summarised.lab.bench.Texts
import io.github.fush1ft.summarised.lab.capture.CaptureController
import io.github.fush1ft.summarised.lab.log.Results
import kotlinx.coroutines.launch

@Composable
internal fun AsrTab() {
    val context = LocalContext.current
    val recordingId by BenchRecorder.recordingId.collectAsStateWithLifecycle()
    val elapsed by BenchRecorder.elapsedMs.collectAsStateWithLifecycle()
    val installed by Models.installed.collectAsStateWithLifecycle()
    val busy by BenchRunner.busy.collectAsStateWithLifecycle()
    val results by Results.all.collectAsStateWithLifecycle()
    var selectedPassage by remember { mutableStateOf(Texts.passages.first()) }
    var selectedModels by remember { mutableStateOf(setOf<String>()) }
    var threads by remember { mutableIntStateOf(4) }
    KeepScreenOn(busy != null || recordingId != null)

    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    val asrModels = installed.filter { Models.kindOf(it) != ModelKind.LLM }

    Section("1. Nahraj vzorky (předčítání)") {
        Text(
            "Přečti každou pasáž nahlas normálním tempem, jako bys mluvil na přednášce nebo schůzce. " +
                "Stačí jednou; vzorky se použijí pro všechny modely.",
            style = MaterialTheme.typography.bodySmall,
        )
        Choice(Texts.passages, selectedPassage, { p ->
            val done = BenchRecorder.sampleFile(context, p.id).exists()
            (if (done) "✓ " else "") + p.title
        }) { selectedPassage = it }
        Text(selectedPassage.text, style = MaterialTheme.typography.bodyLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (recordingId == null) {
                Button(onClick = {
                    if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                        micPermission.launch(Manifest.permission.RECORD_AUDIO)
                    } else {
                        val id = selectedPassage.id
                        Models.scope.launch { BenchRecorder.record(context.applicationContext, id) }
                    }
                }, enabled = busy == null) { Text("● Nahrát") }
            } else {
                Button(onClick = { BenchRecorder.stop() }) { Text("■ Hotovo (${CaptureController.formatMs(elapsed)})") }
            }
        }
    }

    Section("2. Spusť přepis") {
        if (asrModels.isEmpty()) Text("Nejdřív stáhni aspoň jeden model v záložce Modely.")
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            asrModels.forEach { m ->
                FilterChip(
                    selected = m.name in selectedModels,
                    onClick = { selectedModels = if (m.name in selectedModels) selectedModels - m.name else selectedModels + m.name },
                    label = { Text(m.name.removePrefix("ggml-").removeSuffix(".bin")) },
                )
            }
        }
        Text("Vlákna:")
        Choice(listOf(2, 4, 6, 8), threads, { "$it" }) { threads = it }
        val samples = Texts.passages.count { BenchRecorder.sampleFile(context, it.id).exists() }
        val chosen = asrModels.filter { it.name in selectedModels }
        if (busy == null) {
            Button(
                onClick = { BenchRunner.runAsr(context, chosen, threads) },
                enabled = chosen.isNotEmpty() && samples > 0 && recordingId == null,
            ) { Text("Spustit (${chosen.size * samples} běhů)") }
        } else {
            Text(busy ?: "")
            LinearProgressIndicator(Modifier.fillMaxWidth())
            OutlinedButton(onClick = { BenchRunner.cancel() }) { Text("Zrušit") }
        }
    }

    Section("Výsledky") {
        val asr = results.filter { it.optString("kind") == "asr" }.reversed()
        if (asr.isEmpty()) Text("Zatím nic.")
        asr.forEach { r ->
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    "${r.optString("model")} · ${r.optString("passage")} · ${r.optInt("threads")} vl.",
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    "RTF ${"%.2f".format(r.optDouble("rtf"))} (${"%.1f".format(r.optDouble("run_ms") / 1000)} s na " +
                        "${"%.0f".format(r.optDouble("audio_ms") / 1000)} s audia) · " +
                        "WER ${"%.1f".format(r.optDouble("wer") * 100)} % · načtení ${"%.1f".format(r.optDouble("load_ms") / 1000)} s",
                )
                Text(r.optString("text").trim(), style = MaterialTheme.typography.bodySmall)
                HorizontalDivider()
            }
        }
    }
}
