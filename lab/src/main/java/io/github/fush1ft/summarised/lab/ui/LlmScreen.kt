@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package io.github.fush1ft.summarised.lab.ui

import android.net.ConnectivityManager
import android.os.StatFs
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.SignalCellularAlt
import androidx.compose.material.icons.rounded.Speed
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import io.github.fush1ft.summarised.lab.bench.BenchRunner
import io.github.fush1ft.summarised.lab.bench.ModelKind
import io.github.fush1ft.summarised.lab.bench.Models
import io.github.fush1ft.summarised.lab.bench.Texts
import io.github.fush1ft.summarised.lab.log.Results
import org.json.JSONArray
import org.json.JSONObject

@Composable
fun LlmScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val installed by Models.installed.collectAsStateWithLifecycle()
    val progress by Models.progress.collectAsStateWithLifecycle()
    val busy by BenchRunner.busy.collectAsStateWithLifecycle()
    val stream by BenchRunner.stream.collectAsStateWithLifecycle()
    val results by Results.all.collectAsStateWithLifecycle()
    KeepScreenOn(busy != null || progress.values.any { it.active })

    val specs = Models.catalog.filter { it.kind == ModelKind.LLM }
    val recommended = specs.filter { it.recommended }
    val llmFiles = installed.filter { Models.kindOf(it) == ModelKind.LLM }
    val metered = remember { context.getSystemService(ConnectivityManager::class.java).isActiveNetworkMetered }
    val freeGb = StatFs(Models.dir(context).path).availableBytes / 1e9

    val lastAsr = results.lastOrNull { it.optString("kind") == "asr" && it.optString("passage") == "lecture" }
        ?: results.lastOrNull { it.optString("kind") == "asr" }
    val sources = buildList {
        add(BenchRunner.LlmSource("Přednáška o fotosyntéze", Texts.Template.LECTURE, Texts.lectureTranscript, Texts.LECTURE_BOOKMARK))
        Texts.passages.drop(1).forEach { add(BenchRunner.LlmSource(it.title, it.template, it.text, "0:30")) }
        if (lastAsr != null) {
            val p = Texts.passages.firstOrNull { it.id == lastAsr.optString("passage") } ?: Texts.passages.first()
            add(BenchRunner.LlmSource("Tvůj přepis (${lastAsr.optString("model")})", p.template, lastAsr.optString("text"), "0:30"))
        }
    }
    var sourceLabel by remember { mutableStateOf(sources.first().label) }
    val source = sources.firstOrNull { it.label == sourceLabel } ?: sources.first()
    var template by remember(sourceLabel) { mutableStateOf(source.template) }
    var threads by remember { mutableIntStateOf(4) }
    var maxTokens by remember { mutableIntStateOf(512) }
    var temperature by remember { mutableFloatStateOf(0f) }

    ScreenHeader(
        "Shrnutí",
        "Modely dostanou kus přednášky, jako by sis v ní udělal záložku, a mají ho česky shrnout. Ty pak ohodnotíš, které shrnutí je nejlepší.",
        onBack,
    )

    // 1. Models
    val missing = recommended.filter { spec -> installed.none { it.name == spec.localName } }
    SectionCard("Stáhni modely", "Čím víc modelů, tím lepší srovnání. Stačí ale dva.", step = 1, done = llmFiles.size >= 2) {
        if (metered && missing.isNotEmpty()) {
            Hint("Jsi na mobilních datech. Modely mají několik GB, počkej na Wi-Fi.", Icons.Rounded.SignalCellularAlt, Tone.WARN)
        }
        Text("Volné místo v telefonu: ${"%.1f".format(freeGb).replace('.', ',')} GB", style = MaterialTheme.typography.labelLarge)
        recommended.forEach { spec ->
            ModelRow(spec, installed.any { it.name == spec.localName }, progress[spec.id]) {
                Models.downloadAll(context, listOf(spec))
            }
        }
        if (missing.isNotEmpty()) {
            BigButton(
                "Stáhnout doporučené (${formatMb(missing.sumOf { it.approxMb })})",
                Icons.Rounded.Download,
                enabled = missing.none { progress[it.id]?.active == true },
            ) { Models.downloadAll(context, missing) }
        }
        Collapsible("Další modely pro srovnání") {
            specs.filter { !it.recommended }.forEach { spec ->
                ModelRow(spec, installed.any { it.name == spec.localName }, progress[spec.id]) {
                    Models.downloadAll(context, listOf(spec))
                }
            }
        }
    }

    // 2. Run
    SectionCard(
        "Porovnej",
        "Každý model shrne stejný text. Jeden model zabere zhruba minutu.",
        step = 2,
        done = results.any { it.optString("kind") == "llm" && !it.has("error") },
    ) {
        Text("Co se má shrnout:", style = MaterialTheme.typography.labelLarge)
        Choice(sources.map { it.label }, sourceLabel, { it }) { sourceLabel = it }
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                source.transcript.take(320) + if (source.transcript.length > 320) " …" else "",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(16.dp),
            )
        }
        val b = busy
        if (b == null) {
            BigButton(
                when (llmFiles.size) {
                    0 -> "Nejdřív stáhni model"
                    1 -> "Shrnout jedním modelem"
                    else -> "Porovnat ${llmFiles.size} modely"
                },
                Icons.Rounded.AutoAwesome,
                enabled = llmFiles.isNotEmpty(),
            ) {
                BenchRunner.runLlms(llmFiles, source, template, BenchRunner.LlmParams(threads, maxTokens, temperature))
            }
        } else {
            Text("${b.step} z ${b.total}: ${b.title} píše…", style = MaterialTheme.typography.titleMedium)
            LinearProgressIndicator(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(50)))
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp),
            ) {
                Text(
                    stream.takeLast(700).ifEmpty { "Načítám model do paměti…" },
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    modifier = Modifier.padding(14.dp),
                )
            }
            OutlinedButton(onClick = { BenchRunner.cancel() }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Rounded.Close, null)
                Spacer(Modifier.width(6.dp))
                Text("Zrušit")
            }
        }
        Collapsible("Pro pokročilé") {
            Text("Šablona výstupu:")
            Choice(Texts.Template.entries, template, { it.label }) { template = it }
            Text("Vlákna procesoru:")
            Choice(listOf(2, 4, 6, 8), threads, { "$it" }) { threads = it }
            Text("Max. délka odpovědi (tokeny):")
            Choice(listOf(256, 512, 1024), maxTokens, { "$it" }) { maxTokens = it }
            Text("Teplota (0 = vždy stejná odpověď):")
            Choice(listOf(0f, 0.3f, 0.7f), temperature, { if (it == 0f) "0" else "$it" }) { temperature = it }
        }
    }

    // 3. Rate
    val forSource = results.filter { it.optString("kind") == "llm" && it.optString("source") == source.label }
        .groupBy { it.optString("model") }.map { it.value.last() }
    if (forSource.isNotEmpty()) {
        val rated = forSource.count { it.optInt("rating_czech", 0) > 0 && it.optInt("rating_facts", 0) > 0 }
        SectionCard(
            "Ohodnoť",
            "Přečti si shrnutí a dej hvězdičky. Ohodnoceno $rated z ${forSource.size}.",
            step = 3,
            done = rated == forSource.size,
        ) {
            Hint(
                "Čeština: zní to přirozeně a bez chyb? Obsah: nic si nevymyslel a nic důležitého nechybí?",
                Icons.Rounded.Info,
            )
        }
        forSource.forEach { r -> LlmResultCard(r) }
    }
}

@Composable
private fun LlmResultCard(r: JSONObject) {
    Card(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(r.optString("model"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                if (r.has("error")) Pill("Selhalo", Tone.ERROR)
                else if (!r.optBoolean("json_valid")) Pill("Špatný formát", Tone.WARN)
            }
            if (r.has("error")) {
                Row(verticalAlignment = Alignment.Top) {
                    Icon(Icons.Rounded.ErrorOutline, null, tint = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.width(8.dp))
                    Text(friendlyError(r.optString("error")), style = MaterialTheme.typography.bodyMedium)
                }
                return@Column
            }
            val genTps = r.optInt("gen_tokens") * 1000.0 / r.optDouble("gen_ms").coerceAtLeast(1.0)
            val totalS = (r.optDouble("load_ms") + r.optDouble("prompt_ms") + r.optDouble("gen_ms")) / 1000
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Speed, null, modifier = Modifier.padding(end = 4.dp))
                    Text("hotovo za ${"%.0f".format(totalS)} s", style = MaterialTheme.typography.labelLarge)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Memory, null, modifier = Modifier.padding(end = 4.dp))
                    Text("${"%.1f".format(genTps)} tokenů/s", style = MaterialTheme.typography.labelLarge)
                }
            }
            HorizontalDivider()
            val json = Texts.extractJson(r.optString("text"))
            if (json != null) SummaryView(json) else {
                SelectionContainer { Text(r.optString("text"), style = MaterialTheme.typography.bodyMedium) }
            }
            HorizontalDivider()
            val uid = r.optString("uid")
            StarRating("Čeština", r.optInt("rating_czech", 0)) { BenchRunner.rate(uid, "rating_czech", it) }
            StarRating("Správnost obsahu", r.optInt("rating_facts", 0)) { BenchRunner.rate(uid, "rating_facts", it) }
        }
    }
}

/** Renders the model's JSON as a readable note instead of code. */
@Composable
private fun SummaryView(j: JSONObject) {
    j.optString("nadpis").takeIf { it.isNotBlank() }?.let {
        Text(it, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    }
    j.optString("shrnuti").takeIf { it.isNotBlank() }?.let {
        Text(it, style = MaterialTheme.typography.bodyLarge)
    }
    val labels = linkedMapOf(
        "body" to "Klíčové body",
        "pojmy" to "Pojmy",
        "otazky_ke_zkousce" to "Možné otázky ke zkoušce",
        "rozhodnuti" to "Rozhodnutí",
        "ukoly" to "Úkoly",
        "otevrene_otazky" to "Otevřené otázky",
        "cisla" to "Čísla a měření",
        "zjisteni" to "Zjištění",
    )
    val keys = labels.keys + j.keys().asSequence().filter { it !in labels && it != "nadpis" && it != "shrnuti" }
    keys.forEach { key ->
        val arr = j.optJSONArray(key) ?: return@forEach
        if (arr.length() == 0) return@forEach
        Text(
            labels[key] ?: key,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 6.dp),
        )
        bullets(arr).forEach { line ->
            Row {
                Text("•", modifier = Modifier.width(16.dp), style = MaterialTheme.typography.bodyMedium)
                Text(line, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

private fun bullets(arr: JSONArray): List<String> = (0 until arr.length()).map { i ->
    when (val v = arr.get(i)) {
        is JSONObject -> v.keys().asSequence().map { v.optString(it) }.filter { it.isNotBlank() }.joinToString(" — ")
        else -> v.toString()
    }
}

private fun friendlyError(e: String): String = when {
    e.contains("failed to load", ignoreCase = true) -> "Model se nepodařilo načíst. Buď je soubor poškozený, nebo ho tahle verze aplikace neumí."
    e.contains("context", ignoreCase = true) || e.contains("memory", ignoreCase = true) -> "Došla paměť. Na tenhle telefon je model moc velký. (${e})"
    else -> e
}
