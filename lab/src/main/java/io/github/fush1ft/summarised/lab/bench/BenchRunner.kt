package io.github.fush1ft.summarised.lab.bench

import android.content.Context
import io.github.fush1ft.summarised.lab.capture.WavWriter
import io.github.fush1ft.summarised.lab.log.EventLog
import io.github.fush1ft.summarised.lab.log.Results
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File

/** Runs benchmarks outside any screen's lifetime and exposes progress as flows. */
object BenchRunner {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _busy = MutableStateFlow<String?>(null)
    /** What is running right now, or null. */
    val busy: StateFlow<String?> = _busy

    private val _stream = MutableStateFlow("")
    /** Live LLM output of the current/last run. */
    val stream: StateFlow<String> = _stream

    private val _lastLlm = MutableStateFlow<JSONObject?>(null)
    val lastLlm: StateFlow<JSONObject?> = _lastLlm

    @Volatile private var cancelled = false

    fun cancel() {
        cancelled = true
        runCatching { Native.instance.cancel() }
    }

    fun runAsr(context: Context, models: List<File>, threads: Int) {
        if (_busy.value != null) return
        val app = context.applicationContext
        cancelled = false
        scope.launch {
            try {
                val jobs = models.flatMap { m ->
                    Texts.passages.mapNotNull { p ->
                        BenchRecorder.sampleFile(app, p.id).takeIf { it.exists() }?.let { Triple(m, p, it) }
                    }
                }
                jobs.forEachIndexed { i, (model, passage, wav) ->
                    if (cancelled) return@launch
                    _busy.value = "Přepis ${i + 1}/${jobs.size}: ${model.name} · ${passage.title}"
                    val engine = if (Models.kindOf(model) == ModelKind.PARAKEET) 1 else 0
                    val samples = WavWriter.readFloats(wav)
                    val r = Native.json(Native.instance.transcribe(engine, model.path, samples, threads, "cs"))
                    if (!r.optBoolean("ok")) {
                        EventLog.add("ASR", "${model.name}: ${r.optString("error")}")
                        return@forEachIndexed
                    }
                    val text = r.optString("text")
                    val wer = Wer.rate(passage.text, text)
                    val rtf = r.optDouble("run_ms") / r.optDouble("audio_ms")
                    Results.add(
                        "asr",
                        JSONObject()
                            .put("model", model.name)
                            .put("passage", passage.id)
                            .put("threads", threads)
                            .put("audio_ms", r.optDouble("audio_ms"))
                            .put("run_ms", r.optDouble("run_ms"))
                            .put("load_ms", r.optDouble("load_ms"))
                            .put("encode_ms", r.optDouble("encode_ms"))
                            .put("decode_ms", r.optDouble("decode_ms"))
                            .put("rtf", rtf)
                            .put("wer", wer)
                            .put("text", text),
                    )
                    EventLog.add("ASR", "${model.name} / ${passage.id}: RTF ${"%.2f".format(rtf)}, WER ${"%.1f".format(wer * 100)} %")
                }
            } catch (e: Throwable) {
                EventLog.add("ASR", "crashed: ${e.javaClass.simpleName}: ${e.message}")
            } finally {
                _busy.value = null
            }
        }
    }

    data class LlmJob(
        val model: File,
        val template: Texts.Template,
        val sourceLabel: String,
        val transcript: String,
        val atLabel: String,
        val threads: Int,
        val maxTokens: Int,
        val temperature: Float,
        val nCtx: Int = 4096,
    )

    fun runLlm(job: LlmJob) {
        if (_busy.value != null) return
        cancelled = false
        _stream.value = ""
        _lastLlm.value = null
        scope.launch {
            _busy.value = "LLM: ${job.model.name}"
            try {
                var user = Texts.bookmarkPrompt(job.template, job.transcript, job.atLabel)
                // Hybrid Qwen3 models think out loud by default; the 2507 instruct ones don't.
                val name = job.model.name.lowercase()
                if (name.startsWith("qwen3") && !name.contains("2507")) user += " /no_think"
                val bytes = ByteArrayOutputStream()
                val r = Native.json(
                    Native.instance.generate(
                        job.model.path,
                        Texts.SYSTEM_PROMPT.toByteArray(),
                        user.toByteArray(),
                        job.nCtx, job.threads, job.maxTokens, job.temperature,
                    ) { piece ->
                        bytes.write(piece)
                        _stream.value = bytes.toString(Charsets.UTF_8.name())
                    },
                )
                if (!r.optBoolean("ok")) {
                    EventLog.add("LLM", "${job.model.name}: ${r.optString("error")}")
                    _stream.value = "Chyba: ${r.optString("error")}"
                    return@launch
                }
                val text = r.optString("text")
                val parsed = Texts.extractJson(text)
                val result = JSONObject()
                    .put("model", job.model.name)
                    .put("template", job.template.label)
                    .put("source", job.sourceLabel)
                    .put("threads", job.threads)
                    .put("n_ctx", job.nCtx)
                    .put("temperature", job.temperature.toDouble())
                    .put("load_ms", r.optDouble("load_ms"))
                    .put("prompt_tokens", r.optInt("prompt_tokens"))
                    .put("prompt_ms", r.optDouble("prompt_ms"))
                    .put("gen_tokens", r.optInt("gen_tokens"))
                    .put("gen_ms", r.optDouble("gen_ms"))
                    .put("cancelled", r.optBoolean("cancelled"))
                    .put("json_valid", parsed != null)
                    .put("text", text)
                Results.add("llm", result)
                _lastLlm.value = result
                EventLog.add(
                    "LLM",
                    "${job.model.name}: prefill ${r.optInt("prompt_tokens")} tok in ${"%.1f".format(r.optDouble("prompt_ms") / 1000)} s, " +
                        "gen ${r.optInt("gen_tokens")} tok in ${"%.1f".format(r.optDouble("gen_ms") / 1000)} s, JSON ${parsed != null}",
                )
            } catch (e: Throwable) {
                EventLog.add("LLM", "crashed: ${e.javaClass.simpleName}: ${e.message}")
                _stream.value = "Chyba: ${e.message}"
            } finally {
                _busy.value = null
            }
        }
    }

    fun rateLast(key: String, value: Int) {
        Results.updateLast("llm") { it.put(key, value) }
        _lastLlm.value = Results.all.value.lastOrNull { it.optString("kind") == "llm" }?.let { JSONObject(it.toString()) }
    }
}
