@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package io.github.fush1ft.summarised.lab.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Thermostat
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.fush1ft.summarised.lab.bench.BenchRunner
import io.github.fush1ft.summarised.lab.bench.Cloud
import io.github.fush1ft.summarised.lab.bench.Models
import io.github.fush1ft.summarised.lab.bench.Texts
import io.github.fush1ft.summarised.lab.log.Results
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Optional comparison against cloud models, with the same prompt as the local ones. */
@Composable
fun CloudSection(source: BenchRunner.LlmSource, template: Texts.Template, temperature: Float) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val busy by BenchRunner.busy.collectAsStateWithLifecycle()
    var provider by remember { mutableStateOf(Cloud.providers.first()) }
    var key by remember(provider) { mutableStateOf(Cloud.key(context, provider)) }
    var models by remember(provider) { mutableStateOf<List<String>>(emptyList()) }
    var model by remember(provider) { mutableStateOf(Cloud.model(context, provider)) }
    var status by remember(provider) { mutableStateOf<String?>(null) }

    SectionCard("Cloudové modely", "Volitelné: o kolik líp shrnuje velký model na internetu?") {
        Hint(
            "Vybraný text se odešle poskytovateli přes internet. Lokální modely nic neodesílají.",
            Icons.Rounded.Cloud,
            Tone.WARN,
        )
        Choice(Cloud.providers, provider, { it.name }) { provider = it }
        Text(provider.note, style = MaterialTheme.typography.bodyMedium)

        if (provider.keyUrl != null) {
            OutlinedTextField(
                value = key,
                onValueChange = {
                    key = it.trim()
                    Cloud.setKey(context, provider, key)
                },
                label = { Text("API klíč ${provider.name}") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(provider.keyUrl))) }
                }) { Text("Získat klíč zdarma") }
                OutlinedButton(
                    enabled = key.isNotBlank(),
                    onClick = {
                        status = "Načítám modely…"
                        val p = provider
                        val k = key
                        scope.launch {
                            val result = withContext(Dispatchers.IO) { runCatching { Cloud.listModels(p, k) } }
                            result.onSuccess { list ->
                                models = list
                                if (model == null || model !in list) {
                                    model = list.firstOrNull()
                                    model?.let { Cloud.setModel(context, p, it) }
                                }
                                status = if (list.isEmpty()) "Žádný vhodný model." else null
                            }.onFailure { status = it.message }
                        }
                    },
                ) { Text("Načíst modely") }
            }
            Text(
                "🔒 Klíč zůstává jen v tomhle telefonu, do výsledků se neukládá.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (models.isNotEmpty()) {
                Text("Model:", style = MaterialTheme.typography.labelLarge)
                Choice(models.take(15), model, { it }) {
                    model = it
                    Cloud.setModel(context, provider, it)
                }
            } else if (model != null) {
                Text("Model: $model", style = MaterialTheme.typography.bodySmall)
            }
        }
        status?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }

        val ready = provider.keyUrl == null || (key.isNotBlank() && model != null)
        BigButton(
            if (ready) "Shrnout v cloudu: ${source.label}" else "Nejdřív vlož klíč a načti modely",
            Icons.Rounded.Key.takeIf { !ready } ?: Icons.Rounded.Cloud,
            enabled = ready && busy == null,
            tonal = true,
        ) {
            BenchRunner.runCloud(context, provider, if (provider.keyUrl == null) null else model, source, template, temperature)
        }
    }
}

/** Thread-count sweep, to tell slow cores from a hot phone. */
@Composable
fun SpeedSection(llmFiles: List<File>) {
    val context = LocalContext.current
    val busy by BenchRunner.busy.collectAsStateWithLifecycle()
    val results by Results.all.collectAsStateWithLifecycle()
    var modelName by remember { mutableStateOf(llmFiles.firstOrNull()?.name) }
    if (llmFiles.isEmpty()) return

    SectionCard("Proč je to pomalé?", "Krátký test se 2, 4, 6 a 8 vlákny procesoru. Zabere pár minut.") {
        Hint(
            "Nech telefon pár minut vychladnout a nenabíjej ho. Teplý telefon zpomaluje a test by lhal.",
            Icons.Rounded.Thermostat,
        )
        Choice(llmFiles.map { it.name }, modelName, { Models.displayName(File(it)) }) { modelName = it }
        val file = llmFiles.firstOrNull { it.name == modelName }
        BigButton("Změřit rychlost", Icons.Rounded.Speed, enabled = file != null && busy == null, tonal = true) {
            BenchRunner.runSpeedTest(context, file!!)
        }
        val display = file?.let { Models.displayName(it) }
        val rows = results.filter { it.optString("kind") == "llm_speed" && it.optString("model") == display }
            .groupBy { it.optInt("threads") }.map { it.value.last() }.sortedBy { it.optInt("threads") }
        if (rows.isNotEmpty()) {
            val best = rows.filter { it.optBoolean("ok") }.maxOfOrNull { it.optDouble("gen_tps") } ?: 0.0
            rows.forEach { r ->
                if (!r.optBoolean("ok")) {
                    Text("${r.optInt("threads")} vlákna: selhalo (${r.optString("error")})", style = MaterialTheme.typography.bodySmall)
                } else {
                    val tps = r.optDouble("gen_tps")
                    MetricBar(
                        "${r.optInt("threads")} vlákna · ${thermalLabel(r.optInt("thermal_after"))}",
                        "${"%.1f".format(tps)} tokenů/s",
                        if (best > 0) (tps / best).toFloat() else 0f,
                        highlight = tps == best,
                    )
                }
            }
            Text(
                "Pro představu: shrnutí záložky má asi 300 tokenů. Při 10 tokenech/s je hotové za půl minuty.",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Normal,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

fun thermalLabel(status: Int): String = when (status) {
    0 -> "telefon v pohodě"
    1 -> "lehce teplý"
    2 -> "teplý"
    3 -> "horký, zpomaluje"
    else -> "přehřátý"
}
