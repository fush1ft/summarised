@file:OptIn(ExperimentalMaterial3Api::class)

package io.github.fush1ft.summarised.lab.ui

import android.os.StatFs
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.fush1ft.summarised.lab.bench.Models
import kotlinx.coroutines.launch

@Composable
internal fun ModelsTab() {
    val context = LocalContext.current
    val scope = Models.scope
    val progress by Models.progress.collectAsStateWithLifecycle()
    val installed by Models.installed.collectAsStateWithLifecycle()
    var customUrl by remember { mutableStateOf("") }
    val anyActive = progress.values.any { !it.done && it.error == null }
    KeepScreenOn(anyActive)

    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch { Models.import(context.applicationContext, uri) }
    }

    val free = StatFs(Models.dir(context).path).availableBytes
    Section("Úložiště") {
        Text("Volné místo: ${"%.1f".format(free / 1e9)} GB")
        Text(
            "Nic se nestahuje samo. Modely jsou z Hugging Face; velikost a licence jsou u každého. " +
                "Stahování pokračuje tam, kde skončilo, když ho přerušíš.",
            style = MaterialTheme.typography.bodySmall,
        )
    }

    Section("Katalog") {
        Models.catalog.forEach { spec ->
            val p = progress[spec.id]
            val have = installed.any { it.name == spec.fileName }
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(spec.label, style = MaterialTheme.typography.titleSmall)
                Text("${spec.kind.name} · ~${spec.sizeMb} MB · ${spec.license}", style = MaterialTheme.typography.bodySmall)
                when {
                    have -> Text("✓ staženo", color = MaterialTheme.colorScheme.primary)
                    p != null && p.error == null && !p.done -> {
                        val f = if (p.totalBytes > 0) p.downloadedBytes.toFloat() / p.totalBytes else 0f
                        LinearProgressIndicator(progress = { f }, modifier = Modifier.fillMaxWidth())
                        Text("${p.downloadedBytes / 1_000_000} / ${p.totalBytes / 1_000_000} MB")
                    }
                    else -> {
                        if (p?.error != null) Text("Chyba: ${p.error}", color = MaterialTheme.colorScheme.error)
                        Button(onClick = { scope.launch { Models.download(context.applicationContext, spec.id, spec.url) } }) {
                            Text(if (p?.error != null) "Pokračovat" else "Stáhnout ${spec.sizeMb} MB")
                        }
                    }
                }
            }
        }
    }

    Section("Vlastní model") {
        OutlinedTextField(
            value = customUrl,
            onValueChange = { customUrl = it.trim() },
            label = { Text("URL na .bin / .gguf") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { scope.launch { Models.download(context.applicationContext, "custom:$customUrl", customUrl) } },
                enabled = customUrl.startsWith("https://"),
            ) { Text("Stáhnout") }
            OutlinedButton(onClick = { importer.launch(arrayOf("*/*")) }) { Text("Importovat soubor") }
        }
        progress.filterKeys { it.startsWith("custom:") || it.startsWith("import:") }.forEach { (id, p) ->
            Text(
                "${id.substringAfterLast('/')}: " + when {
                    p.error != null -> "chyba ${p.error}"
                    p.done -> "hotovo"
                    else -> "${p.downloadedBytes / 1_000_000} MB…"
                },
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }

    Section("Nainstalované (${installed.size})") {
        installed.forEach { f ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("${f.name}\n${Models.kindOf(f).name} · ${f.length() / 1_000_000} MB", modifier = Modifier.weight(1f))
                TextButton(onClick = { Models.delete(context, f) }) { Text("Smazat") }
            }
        }
    }
}
