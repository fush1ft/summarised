@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package io.github.fush1ft.summarised.lab.ui

import android.app.ActivityManager
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.fush1ft.summarised.lab.capture.Scenarios
import io.github.fush1ft.summarised.lab.log.Results

@Composable
fun HomeScreen(go: (Screen) -> Unit) {
    val context = LocalContext.current
    val results by Results.all.collectAsStateWithLifecycle()
    val verdicts = remember(results) { Scenarios.verdicts() }
    val asrRuns = results.count { it.optString("kind") == "asr" }
    val llmRuns = results.filter { it.optString("kind") == "llm" && !it.has("error") }
    val rated = llmRuns.count { it.optInt("rating_czech", 0) > 0 }

    val triggerDone = verdicts.size >= Scenarios.all.size - 1 // the 30-minute test is optional
    val asrDone = asrRuns >= 3
    val llmDone = rated >= 2
    val doneCount = listOf(triggerDone, asrDone, llmDone).count { it }
    val ramGb = remember {
        ActivityManager.MemoryInfo().also { context.getSystemService(ActivityManager::class.java).getMemoryInfo(it) }.totalMem / 1e9
    }

    Spacer(Modifier.height(8.dp))
    Text("Summarised Lab", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.SemiBold)
    Text(
        "Tři krátké testy, celkem asi hodina. Podle výsledků vyberu modely, které na tvém telefonu poběží nejlíp.",
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        shape = RoundedCornerShape(28.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (doneCount == 3) "Hotovo! Pošli výsledky." else "Hotovo $doneCount ze 3",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                Text("${Build.MODEL} · ${"%.0f".format(ramGb)} GB", style = MaterialTheme.typography.labelLarge)
            }
            LinearProgressIndicator(
                progress = { doneCount / 3f },
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(50)),
            )
        }
    }

    StepCard(
        number = 1, icon = Icons.Rounded.PowerSettingsNew,
        title = "Tlačítko zapínání",
        text = "Ověříme, že podržením vypínače spustíš nahrávání a přidáš záložku, i se zamčeným telefonem v kapse.",
        meta = "asi 15 minut",
        status = when {
            triggerDone -> "Hotovo" to Tone.DONE
            verdicts.isNotEmpty() -> "${verdicts.size} z ${Scenarios.all.size} testů" to Tone.ACTIVE
            else -> "Začni tady" to Tone.ACTIVE
        },
    ) { go(Screen.TRIGGER) }

    StepCard(
        number = 2, icon = Icons.Rounded.Mic,
        title = "Přepis řeči",
        text = "Přečteš nahlas tři krátké texty a změříme, který model tě přepíše nejrychleji a nejpřesněji.",
        meta = "asi 20 minut · stahování ~2 GB",
        status = when {
            asrDone -> "Hotovo" to Tone.DONE
            asrRuns > 0 -> "$asrRuns měření" to Tone.ACTIVE
            else -> "Nezačato" to Tone.NEUTRAL
        },
    ) { go(Screen.ASR) }

    StepCard(
        number = 3, icon = Icons.Rounded.AutoAwesome,
        title = "Shrnutí",
        text = "Modely shrnou kus přednášky a ty ohodnotíš, které shrnutí je nejlepší. Žádné znalosti nepotřebuješ.",
        meta = "asi 20 minut · stahování 3–10 GB",
        status = when {
            llmDone -> "Hotovo" to Tone.DONE
            llmRuns.isNotEmpty() -> "$rated z ${llmRuns.size} ohodnoceno" to Tone.ACTIVE
            else -> "Nezačato" to Tone.NEUTRAL
        },
    ) { go(Screen.LLM) }

    StepCard(
        number = null, icon = Icons.AutoMirrored.Rounded.Send,
        title = "Poslat výsledky",
        text = "Jedním klepnutím zkopíruješ souhrn a vložíš ho do konverzace s Claudem. Nahrávky se neposílají.",
        meta = null,
        status = if (results.isEmpty()) "Zatím prázdné" to Tone.NEUTRAL else "Připraveno" to Tone.DONE,
    ) { go(Screen.RESULTS) }

    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
        TextButton(onClick = { go(Screen.ADVANCED) }) {
            Icon(Icons.Rounded.Tune, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Pokročilé: modely, log, nastavení")
        }
    }
}

@Composable
private fun StepCard(
    number: Int?,
    icon: ImageVector,
    title: String,
    text: String,
    meta: String?,
    status: Pair<String, Tone>,
    onClick: () -> Unit,
) {
    ElevatedCard(onClick = onClick, shape = RoundedCornerShape(28.dp), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.Top) {
            IconBadge(icon, if (status.second == Tone.DONE) Tone.DONE else Tone.ACTIVE, size = 52)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (number != null) {
                    Text("KROK $number", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                }
                Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pill(status.first, status.second)
                    if (meta != null) Text(meta, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Icon(
                Icons.AutoMirrored.Rounded.KeyboardArrowRight, null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterVertically),
            )
        }
    }
}
