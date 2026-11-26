package io.github.fush1ft.summarised.lab.bench

import android.app.ActivityManager
import android.app.role.RoleManager
import android.content.Context
import android.os.Build
import io.github.fush1ft.summarised.lab.capture.CaptureController
import io.github.fush1ft.summarised.lab.capture.Haptics
import io.github.fush1ft.summarised.lab.capture.Scenarios
import io.github.fush1ft.summarised.lab.capture.Settings
import io.github.fush1ft.summarised.lab.log.EventLog
import io.github.fush1ft.summarised.lab.log.Results

/** Builds the Markdown report the user pastes back into the conversation. */
object Report {
    fun build(context: Context): String {
        val all = Results.all.value
        val sb = StringBuilder()
        val mem = ActivityManager.MemoryInfo().also { context.getSystemService(ActivityManager::class.java).getMemoryInfo(it) }
        val isAssistant = context.getSystemService(RoleManager::class.java).isRoleHeld(RoleManager.ROLE_ASSISTANT)
        sb.appendLine("# Summarised M0 lab: report")
        sb.appendLine()
        sb.appendLine("- Zařízení: ${Build.MANUFACTURER} ${Build.MODEL} (SoC ${Build.SOC_MANUFACTURER} ${Build.SOC_MODEL})")
        sb.appendLine("- Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}), RAM ${"%.1f".format(mem.totalMem / 1e9)} GB")
        sb.appendLine("- Lab je výchozí asistent: ${if (isAssistant) "ano" else "NE"}")
        sb.appendLine("- Způsob spuštění: ${Settings.startMode(context).name}, haptika: ${Haptics.usage(context).name}")
        val native = runCatching { Native.json(Native.instance.systemInfo()).optString("llama") }.getOrElse { "nenačteno: ${it.message}" }
        sb.appendLine("- ggml: `$native`")
        sb.appendLine()

        val verdicts = all.filter { it.optString("kind") == "scenario" }
        sb.appendLine("## Spouštěč: scénáře")
        if (verdicts.isEmpty()) sb.appendLine("(nevyzkoušeno)")
        Scenarios.all.forEach { s ->
            val v = verdicts.lastOrNull { it.optString("id") == s.id }
            if (v != null) {
                sb.appendLine("- ${s.id} ${s.title}: ${if (v.optBoolean("ok")) "✓ funguje" else "✗ nefunguje"} (režim ${v.optString("start_mode")})")
            }
        }
        val captures = all.filter { it.optString("kind") == "capture" }
        if (captures.isNotEmpty()) {
            sb.appendLine()
            sb.appendLine("| spuštěno | délka | záložky | tichá okna |")
            sb.appendLine("|---|---|---|---|")
            captures.forEach { c ->
                val bms = c.optJSONArray("bookmarks_ms")
                val list = (0 until (bms?.length() ?: 0)).joinToString(" ") { CaptureController.formatMs(bms!!.getLong(it)) }
                sb.appendLine("| ${c.optString("source")} | ${CaptureController.formatMs(c.optLong("duration_ms"))} | ${bms?.length() ?: 0}: $list | ${c.optInt("silent_windows")}/${c.optInt("windows")} |")
            }
        }
        sb.appendLine()
        sb.appendLine("<details><summary>Log událostí (posledních 150)</summary>")
        sb.appendLine()
        sb.appendLine("```")
        EventLog.lines.value.takeLast(150).forEach { sb.appendLine(it) }
        sb.appendLine("```")
        sb.appendLine("</details>")
        sb.appendLine()

        val asr = all.filter { it.optString("kind") == "asr" }
        sb.appendLine("## Přepis (${asr.size} běhů)")
        if (asr.isNotEmpty()) {
            sb.appendLine("| model | pasáž | vlákna | audio | běh | RTF | WER | WER bez čísel | teplota |")
            sb.appendLine("|---|---|---|---|---|---|---|---|---|")
            asr.forEach { r ->
                sb.appendLine(
                    "| ${r.optString("model")} | ${r.optString("passage")} | ${r.optInt("threads")} | " +
                        "${s(r.optDouble("audio_ms"))} | ${s(r.optDouble("run_ms"))} | ${"%.2f".format(r.optDouble("rtf"))} | " +
                        "${"%.1f".format(100 * r.optDouble("wer"))} % | " +
                        (if (r.has("wer_nonum")) "${"%.1f".format(100 * r.optDouble("wer_nonum"))} %" else "–") +
                        " | ${if (r.has("thermal")) r.optInt("thermal") else "–"} |",
                )
            }
            sb.appendLine()
            sb.appendLine("<details><summary>Přepisy</summary>")
            sb.appendLine()
            asr.forEach { r -> sb.appendLine("- **${r.optString("model")} / ${r.optString("passage")}**: ${r.optString("text").trim()}") }
            sb.appendLine("</details>")
        }
        all.filter { it.optString("kind") == "asr_error" }.forEach { sb.appendLine("- ✗ ${it.optString("model")}: ${it.optString("error")}") }
        sb.appendLine()

        val llm = all.filter { it.optString("kind") == "llm" }
        sb.appendLine("## Shrnutí (${llm.size} běhů)")
        val ok = llm.filter { !it.has("error") }
        if (ok.isNotEmpty()) {
            sb.appendLine("| model | zdroj | prompt tok | prefill tok/s | gen tok/s | načtení | JSON | čeština | správnost | formát | teplota před→po | volná RAM |")
            sb.appendLine("|---|---|---|---|---|---|---|---|---|---|---|---|")
            ok.forEach { r ->
                sb.appendLine(
                    "| ${r.optString("model")} | ${r.optString("source")} | ${r.optInt("prompt_tokens")} | " +
                        "${tps(r.optInt("prompt_tokens"), r.optDouble("prompt_ms"))} | ${tps(r.optInt("gen_tokens"), r.optDouble("gen_ms"))} | " +
                        "${s(r.optDouble("load_ms"))} | ${if (r.optBoolean("json_valid")) "✓" else "✗"} | " +
                        "${r.optInt("rating_czech", 0).takeIf { it > 0 } ?: "–"} | ${r.optInt("rating_facts", 0).takeIf { it > 0 } ?: "–"} | " +
                        "${if (r.has("cloud")) "cloud" else r.optString("prompt_format")} | " +
                        (if (r.has("thermal_before")) "${r.optInt("thermal_before")}→${r.optInt("thermal_after")}" else "–") + " | " +
                        (if (r.has("free_mem_mb")) "${r.optLong("free_mem_mb")} MB" else "–") + " |",
                )
            }
        }
        llm.filter { it.has("error") }.forEach { sb.appendLine("- ✗ ${it.optString("model")}: ${it.optString("error")}") }

        val speed = all.filter { it.optString("kind") == "llm_speed" }
        if (speed.isNotEmpty()) {
            sb.appendLine()
            sb.appendLine("### Rychlost podle vláken")
            sb.appendLine("| model | vlákna | gen tok/s | prefill tok/s | teplota před→po | volná RAM | chyba |")
            sb.appendLine("|---|---|---|---|---|---|---|")
            speed.forEach { r ->
                sb.appendLine(
                    "| ${r.optString("model")} | ${r.optInt("threads")} | ${"%.1f".format(r.optDouble("gen_tps"))} | " +
                        "${"%.0f".format(r.optDouble("prefill_tps"))} | ${r.optInt("thermal_before")}→${r.optInt("thermal_after")} | " +
                        "${r.optLong("free_mem_mb")} MB | ${r.optString("error")} |",
                )
            }
        }
        sb.appendLine()
        ok.forEach { r ->
            sb.appendLine("<details><summary>${r.optString("model")} / ${r.optString("source")}</summary>")
            sb.appendLine()
            sb.appendLine("```")
            sb.appendLine(r.optString("text").take(2500))
            sb.appendLine("```")
            sb.appendLine("</details>")
        }
        return sb.toString()
    }

    private fun s(ms: Double) = "%.1f s".format(ms / 1000.0)
    private fun tps(tokens: Int, ms: Double) = if (ms > 0) "%.1f".format(tokens * 1000.0 / ms) else "–"
}
