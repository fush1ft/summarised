@file:OptIn(ExperimentalMaterial3Api::class)

package io.github.fush1ft.summarised.lab.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.fush1ft.summarised.lab.bench.BenchRunner
import io.github.fush1ft.summarised.lab.bench.ModelKind
import io.github.fush1ft.summarised.lab.bench.Models
import io.github.fush1ft.summarised.lab.bench.Texts
import io.github.fush1ft.summarised.lab.log.Results

private data class Source(val label: String, val template: Texts.Template, val transcript: String, val at: String)

@Composable
internal fun LlmTab() {
    val installed by Models.installed.collectAsStateWithLifecycle()
    val busy by BenchRunner.busy.collectAsStateWithLifecycle()
    val stream by BenchRunner.stream.collectAsStateWithLifecycle()
    val last by BenchRunner.lastLlm.collectAsStateWithLifecycle()
    val results by Results.all.collectAsStateWithLifecycle()
    KeepScreenOn(busy != null)

    val lastAsr = results.lastOrNull { it.optString("kind") == "asr" }
    val sources = buildList {
        add(Source("Přednáška (4 min přepis)", Texts.Template.LECTURE, Texts.lectureTranscript, Texts.LECTURE_BOOKMARK))
        Texts.passages.drop(1).forEach { add(Source(it.title, it.template, it.text, "0:30")) }
        if (lastAsr != null) {
            val p = Texts.passages.firstOrNull { it.id == lastAsr.optString("passage") } ?: Texts.passages.first()
            add(Source("Můj poslední přepis (${lastAsr.optString("model")})", p.template, lastAsr.optString("text"), "0:30"))
        }
    }
    var source by remember { mutableStateOf(sources.first()) }
    var template by remember { mutableStateOf(source.template) }
    val llms = installed.filter { Models.kindOf(it) == ModelKind.LLM }
    var modelName by remember { mutableStateOf(llms.firstOrNull()?.name) }
    var threads by remember { mutableIntStateOf(4) }
    var maxTokens by remember { mutableIntStateOf(512) }
    var temperature by remember { mutableFloatStateOf(0f) }

    Section("Vstup") {
        Choice(sources, source, { it.label }) {
            source = it
            template = it.template
        }
        Text("Šablona:")
        Choice(Texts.Template.entries, template, { it.label }) { template = it }
        Text(
            source.transcript.take(400) + if (source.transcript.length > 400) "…" else "",
            style = MaterialTheme.typography.bodySmall,
        )
    }

    Section("Model a parametry") {
        if (llms.isEmpty()) Text("Nejdřív stáhni LLM v záložce Modely.")
        Choice(llms.map { it.name }, modelName, { it.removeSuffix(".gguf") }) { modelName = it }
        Text("Vlákna:")
        Choice(listOf(2, 4, 6, 8), threads, { "$it" }) { threads = it }
        Text("Max. tokenů:")
        Choice(listOf(256, 512, 1024), maxTokens, { "$it" }) { maxTokens = it }
        Text("Teplota:")
        Choice(listOf(0f, 0.3f, 0.7f), temperature, { if (it == 0f) "0 (greedy)" else "$it" }) { temperature = it }
        val model = llms.firstOrNull { it.name == modelName }
        if (busy == null) {
            Button(
                onClick = {
                    BenchRunner.runLlm(
                        BenchRunner.LlmJob(model!!, template, source.label, source.transcript, source.at, threads, maxTokens, temperature),
                    )
                },
                enabled = model != null,
            ) { Text("Spustit shrnutí záložky") }
        } else {
            Text(busy ?: "")
            LinearProgressIndicator(Modifier.fillMaxWidth())
            OutlinedButton(onClick = { BenchRunner.cancel() }) { Text("Zrušit") }
        }
    }

    Section("Výstup") {
        last?.let { r ->
            val prefill = r.optInt("prompt_tokens") * 1000.0 / r.optDouble("prompt_ms").coerceAtLeast(1.0)
            val gen = r.optInt("gen_tokens") * 1000.0 / r.optDouble("gen_ms").coerceAtLeast(1.0)
            Text(
                "Načtení ${"%.1f".format(r.optDouble("load_ms") / 1000)} s · " +
                    "prefill ${r.optInt("prompt_tokens")} tok (${"%.0f".format(prefill)} tok/s) · " +
                    "generování ${"%.1f".format(gen)} tok/s · JSON ${if (r.optBoolean("json_valid")) "✓" else "✗"}",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        SelectionContainer {
            Text(stream.ifEmpty { "—" }, fontFamily = FontFamily.Monospace, fontSize = 12.sp, lineHeight = 16.sp)
        }
        last?.let { r ->
            Text("Ohodnoť (1 = špatné, 5 = výborné):", style = MaterialTheme.typography.titleSmall)
            Text("Čeština (gramatika, přirozenost):")
            Rating(r.optInt("rating_czech", 0)) { BenchRunner.rateLast("rating_czech", it) }
            Text("Věcná správnost (nic nevymyslel, nic důležitého nechybí):")
            Rating(r.optInt("rating_facts", 0)) { BenchRunner.rateLast("rating_facts", it) }
        }
    }
}

@Composable
private fun Rating(value: Int, onRate: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Choice((1..5).toList(), value.takeIf { it > 0 }, { "$it" }, onRate)
    }
}
