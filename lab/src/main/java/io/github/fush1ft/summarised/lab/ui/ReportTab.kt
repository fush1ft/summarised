@file:OptIn(ExperimentalMaterial3Api::class)

package io.github.fush1ft.summarised.lab.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.fush1ft.summarised.lab.bench.Report
import io.github.fush1ft.summarised.lab.log.Results

@Composable
internal fun ReportTab() {
    val context = LocalContext.current
    val results by Results.all.collectAsStateWithLifecycle()
    val report = remember(results) { Report.build(context) }
    var confirmClear by remember { mutableStateOf(false) }

    Section("Report") {
        Text(
            "Až projdeš testy, zkopíruj report a vlož ho do konverzace s Claudem. " +
                "Obsahuje jen čísla, log a výstupy modelů, žádné nahrávky.",
            style = MaterialTheme.typography.bodySmall,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                context.getSystemService(ClipboardManager::class.java)
                    .setPrimaryClip(ClipData.newPlainText("Summarised M0 report", report))
            }) { Text("Kopírovat") }
            OutlinedButton(onClick = {
                val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, report)
                context.startActivity(Intent.createChooser(send, "Sdílet report"))
            }) { Text("Sdílet") }
        }
        TextButton(onClick = {
            if (confirmClear) {
                Results.clear()
                confirmClear = false
            } else {
                confirmClear = true
            }
        }) { Text(if (confirmClear) "Opravdu smazat všechny výsledky?" else "Smazat výsledky") }
    }
    Section("Náhled") {
        SelectionContainer {
            Text(report, fontFamily = FontFamily.Monospace, fontSize = 11.sp, lineHeight = 14.sp)
        }
    }
}
