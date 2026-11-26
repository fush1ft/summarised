@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package io.github.fush1ft.summarised.lab.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.fush1ft.summarised.lab.bench.Report
import io.github.fush1ft.summarised.lab.capture.Scenarios
import io.github.fush1ft.summarised.lab.log.Results
import kotlinx.coroutines.delay

@Composable
fun ResultsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val results by Results.all.collectAsStateWithLifecycle()
    val report = remember(results) { Report.build(context) }
    var copied by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    LaunchedEffect(copied) {
        if (copied) {
            delay(2500)
            copied = false
        }
    }

    val verdicts = remember(results) { Scenarios.verdicts() }
    val asr = results.count { it.optString("kind") == "asr" }
    val llm = results.filter { it.optString("kind") == "llm" }
    val rated = llm.count { it.optInt("rating_czech", 0) > 0 }

    ScreenHeader(
        "Poslat výsledky",
        "Zkopíruj souhrn a vlož ho do konverzace s Claudem. Podle něj vyberu modely pro aplikaci.",
        onBack,
    )

    SectionCard("Co je v souhrnu") {
        CheckRow(
            verdicts.isNotEmpty(), "Tlačítko zapínání",
            if (verdicts.isEmpty()) "Zatím nevyzkoušeno" else "${verdicts.count { it.value }} z ${verdicts.size} testů funguje",
        )
        CheckRow(asr > 0, "Přepis řeči", if (asr == 0) "Zatím nevyzkoušeno" else "$asr měření")
        CheckRow(rated > 0, "Shrnutí", if (llm.isEmpty()) "Zatím nevyzkoušeno" else "${llm.size} shrnutí, $rated ohodnoceno")
        Hint(
            "Souhrn obsahuje jen čísla, hodnocení a texty, které modely napsaly. Tvoje nahrávky v něm nejsou a z telefonu neodejdou.",
            Icons.Rounded.Lock,
        )
    }

    BigButton(if (copied) "Zkopírováno ✓" else "Zkopírovat souhrn", Icons.Rounded.ContentCopy) {
        context.getSystemService(ClipboardManager::class.java)
            .setPrimaryClip(ClipData.newPlainText("Summarised M0 report", report))
        copied = true
    }
    BigButton("Sdílet…", Icons.Rounded.Share, tonal = true) {
        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, report)
        context.startActivity(Intent.createChooser(send, "Sdílet souhrn"))
    }

    SectionCard("Náhled") {
        Collapsible("Zobrazit celý text (${report.length} znaků)") {
            SelectionContainer {
                Text(report, fontFamily = FontFamily.Monospace, fontSize = 11.sp, lineHeight = 14.sp)
            }
        }
    }

    TextButton(
        onClick = {
            if (confirmClear) {
                Results.clear()
                confirmClear = false
            } else {
                confirmClear = true
            }
        },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            if (confirmClear) "Opravdu smazat všechny výsledky? Klepni znovu." else "Smazat výsledky a začít znovu",
            color = MaterialTheme.colorScheme.error,
        )
    }
}
