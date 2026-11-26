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

    data class Busy(val title: String, val step: Int, val total: Int)

    private val _busy = MutableStateFlow<Busy?>(null)
    /** What is running right now, or null. */
    val busy: StateFlow<Busy?> = _busy

    private val _stream = MutableStateFlow("")
    /** Live output of the LLM that is generating right now. */
    val stream: StateFlow<String> = _stream

    private val _streamingModel = MutableStateFlow<String?>(null)
    val streamingModel: StateFlow<String?> = _streamingModel

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
                    _busy.value = Busy("${Models.displayName(model)} · ${passage.title}", i + 1, jobs.size)
                    markStart("asr", model)
                    val engine = if (Models.kindOf(model) == ModelKind.PARAKEET) 1 else 0
                    val samples = WavWriter.readFloats(wav)
                    val r = Native.json(Native.instance.transcribe(engine, model.path, samples, threads, "cs"))
                    if (!r.optBoolean("ok")) {
                        EventLog.add("ASR", "${model.name}: ${r.optString("error")}")
                        Results.add(
                            "asr_error",
                            JSONObject().put("model", Models.displayName(model)).put("error", r.optString("error")),
                        )
                        return@forEachIndexed
                    }
                    val text = r.optString("text")
                    val wer = Wer.rate(passage.text, text)
                    val rtf = r.optDouble("run_ms") / r.optDouble("audio_ms")
                    Results.add(
                        "asr",
                        JSONObject()
                            .put("model", Models.displayName(model))
                            .put("model_file", model.name)
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

    data class LlmSource(val label: String, val template: Texts.Template, val transcript: String, val at: String)

    data class LlmParams(val threads: Int = 4, val maxTokens: Int = 512, val temperature: Float = 0f, val nCtx: Int = 4096)

    /** Runs every model in [models] on the same source, one after another. */
    fun runLlms(models: List<File>, source: LlmSource, template: Texts.Template, params: LlmParams) {
        if (_busy.value != null || models.isEmpty()) return
        cancelled = false
        scope.launch {
            try {
                models.forEachIndexed { i, model ->
                    if (cancelled) return@launch
                    _busy.value = Busy(Models.displayName(model), i + 1, models.size)
                    runOne(model, source, template, params)
                }
            } finally {
                _busy.value = null
                _streamingModel.value = null
            }
        }
    }

    private fun runOne(model: File, source: LlmSource, template: Texts.Template, params: LlmParams) {
        _stream.value = ""
        _streamingModel.value = model.name
        markStart("llm", model, source.label, template.label)
        try {
            var user = Texts.bookmarkPrompt(template, source.transcript, source.at)
            // Hybrid Qwen3 models think out loud by default; the 2507 instruct ones don't.
            val name = model.name.lowercase()
            if (name.startsWith("qwen3") && !name.contains("2507")) user += " /no_think"
            val bytes = ByteArrayOutputStream()
            val r = Native.json(
                Native.instance.generate(
                    model.path,
                    Texts.SYSTEM_PROMPT.toByteArray(),
                    user.toByteArray(),
                    params.nCtx, params.threads, params.maxTokens, params.temperature,
                ) { piece ->
                    bytes.write(piece)
                    _stream.value = bytes.toString(Charsets.UTF_8.name())
                },
            )
            if (!r.optBoolean("ok")) {
                EventLog.add("LLM", "${model.name}: ${r.optString("error")}")
                Results.add(
                    "llm",
                    JSONObject()
                        .put("model", Models.displayName(model))
                        .put("model_file", model.name)
                        .put("source", source.label)
                        .put("template", template.label)
                        .put("error", r.optString("error")),
                )
                return
            }
            val text = r.optString("text")
            val parsed = Texts.extractJson(text)
            Results.add(
                "llm",
                JSONObject()
                    .put("model", Models.displayName(model))
                    .put("model_file", model.name)
                    .put("template", template.label)
                    .put("source", source.label)
                    .put("prompt_format", r.optString("prompt_format"))
                    .put("threads", params.threads)
                    .put("n_ctx", params.nCtx)
                    .put("temperature", params.temperature.toDouble())
                    .put("load_ms", r.optDouble("load_ms"))
                    .put("prompt_tokens", r.optInt("prompt_tokens"))
                    .put("prompt_ms", r.optDouble("prompt_ms"))
                    .put("gen_tokens", r.optInt("gen_tokens"))
                    .put("gen_ms", r.optDouble("gen_ms"))
                    .put("cancelled", r.optBoolean("cancelled"))
                    .put("json_valid", parsed != null)
                    .put("text", text),
            )
            EventLog.add(
                "LLM",
                "${model.name}: prefill ${r.optInt("prompt_tokens")} tok in ${"%.1f".format(r.optDouble("prompt_ms") / 1000)} s, " +
                    "gen ${r.optInt("gen_tokens")} tok in ${"%.1f".format(r.optDouble("gen_ms") / 1000)} s, JSON ${parsed != null}",
            )
        } catch (e: Throwable) {
            EventLog.add("LLM", "crashed: ${e.javaClass.simpleName}: ${e.message}")
            Results.add(
                "llm",
                JSONObject().put("model", Models.displayName(model)).put("model_file", model.name)
                    .put("source", source.label).put("template", template.label).put("error", e.message ?: "crash"),
            )
        }
    }

    private fun markStart(type: String, model: File, source: String = "", template: String = "") {
        Results.add(
            "run_start",
            JSONObject().put("type", type).put("model", Models.displayName(model)).put("model_file", model.name)
                .put("source", source).put("template", template),
        )
    }

    /**
     * A model too big for RAM gets the whole process killed by Android, so no
     * result is ever written. On the next launch, a run that started but never
     * finished is recorded as such: that is a result too.
     */
    fun recoverCrashedRun() {
        val all = Results.all.value
        val start = all.lastOrNull { it.optString("kind") == "run_start" } ?: return
        val startAt = start.optLong("at")
        val finished = all.any {
            it.optLong("at") >= startAt && it.optString("kind") in setOf("asr", "asr_error", "llm") &&
                it.optString("model") == start.optString("model")
        }
        if (finished || all.any { it.optString("kind") == "crash_noted" && it.optLong("start_at") == startAt }) return
        val reason = "Aplikace spadla během běhu modelu (nejspíš došla paměť)."
        val type = start.optString("type")
        Results.add(
            if (type == "llm") "llm" else "asr_error",
            JSONObject().put("model", start.optString("model")).put("model_file", start.optString("model_file"))
                .put("source", start.optString("source")).put("template", start.optString("template"))
                .put("error", reason),
        )
        Results.add("crash_noted", JSONObject().put("start_at", startAt))
        EventLog.add("CRASH", "${start.optString("model")}: $reason")
    }

    fun rate(uid: String, key: String, value: Int) {
        Results.update({ it.optString("uid") == uid }) { it.put(key, value) }
    }
}
