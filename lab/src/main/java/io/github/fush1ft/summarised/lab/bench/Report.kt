package io.github.fush1ft.summarised.lab.bench

import android.app.ActivityManager
import android.app.role.RoleManager
import android.content.Context
import android.os.Build
import io.github.fush1ft.summarised.lab.capture.CaptureController
import io.github.fush1ft.summarised.lab.capture.Haptics
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
        sb.appendLine("# Summarised M0 lab — report")
        sb.appendLine()
        sb.appendLine("- Zařízení: ${Build.MANUFACTURER} ${Build.MODEL} (SoC ${Build.SOC_MANUFACTURER} ${Build.SOC_MODEL})")
        sb.appendLine("- Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}), RAM ${"%.1f".format(mem.totalMem / 1e9)} GB")
        sb.appendLine("- Lab je výchozí asistent: ${if (isAssistant) "ano" else "NE"}")
        sb.appendLine("- Start mode: ${Settings.startMode(context).name}, haptika: ${Haptics.usage(context).name}")
        val native = runCatching { Native.json(Native.instance.systemInfo()).optString("llama") }.getOrElse { "nenačteno: ${it.message}" }
        sb.appendLine("- ggml: `$native`")
        sb.appendLine()

        val captures = all.filter { it.optString("kind") == "capture" }
        sb.appendLine("## Spouštěč: nahrávky (${captures.size})")
        if (captures.isNotEmpty()) {
            sb.appendLine("| zdroj | délka | záložky | tichá okna |")
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
            sb.appendLine("| model | pasáž | vlákna | audio | běh | RTF | WER | načtení |")
            sb.appendLine("|---|---|---|---|---|---|---|---|")
            asr.forEach { r ->
                sb.appendLine(
                    "| ${r.optString("model")} | ${r.optString("passage")} | ${r.optInt("threads")} | " +
                        "${s(r.optDouble("audio_ms"))} | ${s(r.optDouble("run_ms"))} | ${"%.2f".format(r.optDouble("rtf"))} | " +
                        "${"%.1f".format(100 * r.optDouble("wer"))} % | ${s(r.optDouble("load_ms"))} |",
                )
            }
            sb.appendLine()
            sb.appendLine("<details><summary>Přepisy</summary>")
            sb.appendLine()
            asr.forEach { r -> sb.appendLine("- **${r.optString("model")} / ${r.optString("passage")}**: ${r.optString("text").trim()}") }
            sb.appendLine("</details>")
        }
        sb.appendLine()

        val llm = all.filter { it.optString("kind") == "llm" }
        sb.appendLine("## LLM (${llm.size} běhů)")
        if (llm.isNotEmpty()) {
            sb.appendLine("| model | šablona | zdroj | prompt tok | prefill tok/s | gen tok/s | načtení | JSON | čeština | správnost |")
            sb.appendLine("|---|---|---|---|---|---|---|---|---|---|")
            llm.forEach { r ->
                sb.appendLine(
                    "| ${r.optString("model")} | ${r.optString("template")} | ${r.optString("source")} | ${r.optInt("prompt_tokens")} | " +
                        "${tps(r.optInt("prompt_tokens"), r.optDouble("prompt_ms"))} | ${tps(r.optInt("gen_tokens"), r.optDouble("gen_ms"))} | " +
                        "${s(r.optDouble("load_ms"))} | ${if (r.optBoolean("json_valid")) "✓" else "✗"} | " +
                        "${r.optInt("rating_czech", 0).takeIf { it > 0 } ?: "–"} | ${r.optInt("rating_facts", 0).takeIf { it > 0 } ?: "–"} |",
                )
            }
            sb.appendLine()
            llm.forEach { r ->
                sb.appendLine("<details><summary>${r.optString("model")} / ${r.optString("source")}</summary>")
                sb.appendLine()
                sb.appendLine("```")
                sb.appendLine(r.optString("text").take(2500))
                sb.appendLine("```")
                sb.appendLine("</details>")
            }
        }
        return sb.toString()
    }

    private fun s(ms: Double) = "%.1f s".format(ms / 1000.0)
    private fun tps(tokens: Int, ms: Double) = if (ms > 0) "%.1f".format(tokens * 1000.0 / ms) else "–"

}
