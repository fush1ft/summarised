@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package io.github.fush1ft.summarised.lab.ui

import android.os.StatFs
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.fush1ft.summarised.lab.bench.Models
import io.github.fush1ft.summarised.lab.bench.Native
import io.github.fush1ft.summarised.lab.log.EventLog
import kotlinx.coroutines.launch

@Composable
fun AdvancedScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val installed by Models.installed.collectAsStateWithLifecycle()
    val progress by Models.progress.collectAsStateWithLifecycle()
    val log by EventLog.lines.collectAsStateWithLifecycle()
    var url by remember { mutableStateOf("") }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) Models.scope.launch { Models.import(context.applicationContext, uri) }
    }

    ScreenHeader("Pokročilé", "Správa modelů a technické detaily. Pro testy to nepotřebuješ.", onBack)

    val free = StatFs(Models.dir(context).path).availableBytes / 1e9
    val used = installed.sumOf { it.length() } / 1e9
    SectionCard("Stažené modely", "Zabírají ${"%.1f".format(used)} GB · volno ${"%.1f".format(free)} GB") {
        if (installed.isEmpty()) Text("Zatím nic.")
        installed.forEach { f ->
            InstalledRow(Models.displayName(f), "${f.name} · ${f.length() / 1_000_000} MB") { Models.delete(context, f) }
        }
    }

    SectionCard("Vlastní model", "Odkaz na .gguf (LLM) nebo .bin (Whisper), případně soubor z telefonu.") {
        OutlinedTextField(
            value = url,
            onValueChange = { url = it.trim() },
            label = { Text("https://…") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        BigButton("Stáhnout z odkazu", Icons.Rounded.Download, enabled = url.startsWith("https://"), tonal = true) {
            val u = url
            Models.scope.launch { Models.downloadUrl(context.applicationContext, u) }
        }
        BigButton("Vybrat soubor v telefonu", Icons.Rounded.FileOpen, tonal = true) { importer.launch(arrayOf("*/*")) }
        progress.filterKeys { it.startsWith("custom:") || it.startsWith("import:") }.forEach { (id, p) ->
            Text(
                "${id.substringAfter(':')}: " + when {
                    p.error != null -> "chyba: ${p.error}"
                    p.done -> "hotovo"
                    else -> "${p.downloadedBytes / 1_000_000} MB…"
                },
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }

    SectionCard("Technické informace") {
        val info = remember {
            runCatching { Native.json(Native.instance.systemInfo()).optString("llama") }.getOrElse { "nativní knihovna se nenačetla: ${it.message}" }
        }
        Text(info, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
        Collapsible("Záznam událostí (${log.size})") {
            TextButton(onClick = { EventLog.clear() }) { Text("Vymazat") }
            SelectionContainer {
                Text(log.takeLast(120).reversed().joinToString("\n"), fontFamily = FontFamily.Monospace, fontSize = 11.sp, lineHeight = 14.sp)
            }
        }
    }
}
