package io.github.fush1ft.summarised.summarise

import android.content.Context
import android.os.SystemClock
import io.github.fush1ft.summarised.AppPrefs
import io.github.fush1ft.summarised.core.inference.Native
import io.github.fush1ft.summarised.transcribe.LocalLlm
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * The summariser chain decided after M0. Easy mode: Groq (with the user's free
 * key) -> Pollinations (no key) -> Gemma 4 E2B on the phone. Advanced mode lets
 * the user switch steps off, or keep everything on the phone.
 * Blocking; call from a worker thread.
 */
object Summariser {
    data class Answer(val json: JSONObject, val by: String)

    class Failed(message: String) : Exception(message)

    private const val POLLINATIONS_URL = "https://text.pollinations.ai/openai"
    private const val GROQ_CHAT = "https://api.groq.com/openai/v1/chat/completions"
    private const val GROQ_MODELS = "https://api.groq.com/openai/v1/models"
    /** Pollinations allows one keyless request per 45 s per IP. */
    private const val POLLINATIONS_GAP_MS = 46_000L
    /** Its keyless prompts are limited to ~16,000 characters. */
    const val POLLINATIONS_MAX_CHARS = 15_000

    private var lastPollinations = 0L
    private var groqModel: String? = null

    /** Which steps are on, in order. */
    fun steps(context: Context): List<String> {
        val advanced = AppPrefs.mode(context) == AppPrefs.Mode.ADVANCED
        if (advanced && AppPrefs.onDeviceOnly(context)) return listOf("local")
        return buildList {
            if (AppPrefs.groqKey(context).isNotBlank() && (!advanced || AppPrefs.useGroq(context))) add("groq")
            if (!advanced || AppPrefs.usePollinations(context)) add("pollinations")
            if (!advanced || AppPrefs.useLocal(context)) add("local")
        }
    }

    fun run(context: Context, user: String, maxTokens: Int = 1200): Answer {
        val errors = mutableListOf<String>()
        for (step in steps(context)) {
            try {
                val answer = when (step) {
                    "groq" -> groq(context, user, maxTokens)
                    "pollinations" -> if (user.length + Prompts.SYSTEM.length > POLLINATIONS_MAX_CHARS) null else pollinations(user)
                    "local" -> local(context, user, maxTokens)
                    else -> null
                }
                if (answer != null) return answer
            } catch (e: Exception) {
                errors += "$step: ${e.message}"
                android.util.Log.w("summarised", "summary step $step failed: ${e.message}")
            }
        }
        throw Failed(
            if (errors.isEmpty()) "Žádný způsob shrnutí není dostupný (zapni internet, zadej klíč Groq nebo stáhni lokální model)."
            else errors.joinToString("; "),
        )
    }

    // --- Groq ------------------------------------------------------------------

    private fun groq(context: Context, user: String, maxTokens: Int): Answer? {
        val key = AppPrefs.groqKey(context)
        val model = groqModel ?: pickGroqModel(key).also { groqModel = it }
        val (code, body, retry) = post(GROQ_CHAT, key, chatBody(model, user, maxTokens))
        if (code == 429 && retry != null && retry <= 30) {
            Thread.sleep(retry * 1000L)
            return groq(context, user, maxTokens)
        }
        if (code == 404 || code == 400 && body.contains("model", ignoreCase = true)) groqModel = null
        if (code !in 200..299) throw Failed("HTTP $code ${errorMessage(body)}")
        val text = content(body)
        val json = Prompts.parseJson(text) ?: throw Failed("odpověď není JSON")
        return Answer(json, "Groq · $model")
    }

    /** Prefer Qwen3 (best in M0), then GPT-OSS 120B, then any Llama. */
    private fun pickGroqModel(key: String): String {
        val conn = (URL(GROQ_MODELS).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            setRequestProperty("Authorization", "Bearer $key")
        }
        val code = conn.responseCode
        if (code !in 200..299) throw Failed("Groq: klíč nefunguje (HTTP $code)")
        val data = JSONObject(conn.inputStream.bufferedReader().readText()).optJSONArray("data") ?: JSONArray()
        val ids = (0 until data.length()).mapNotNull { data.optJSONObject(it)?.optString("id") }
            .filterNot { id -> listOf("whisper", "tts", "guard", "embed").any { id.contains(it, ignoreCase = true) } }
        return listOf("qwen3", "gpt-oss-120b", "llama-3.3-70b", "llama")
            .firstNotNullOfOrNull { p -> ids.firstOrNull { it.contains(p, ignoreCase = true) } }
            ?: ids.firstOrNull() ?: throw Failed("Groq nenabízí žádný model")
    }

    // --- Pollinations ------------------------------------------------------------

    @Synchronized
    private fun pollinations(user: String): Answer? {
        val wait = lastPollinations + POLLINATIONS_GAP_MS - SystemClock.elapsedRealtime()
        if (wait > 0) Thread.sleep(wait)
        lastPollinations = SystemClock.elapsedRealtime()
        val (code, body, retry) = post(POLLINATIONS_URL, "", chatBody(null, user, 800))
        if ((code == 402 || code == 429) && retry != null && retry <= 60) {
            Thread.sleep(retry * 1000L)
            lastPollinations = SystemClock.elapsedRealtime()
            val (c2, b2, _) = post(POLLINATIONS_URL, "", chatBody(null, user, 800))
            if (c2 !in 200..299) throw Failed("HTTP $c2 ${errorMessage(b2)}")
            return Answer(Prompts.parseJson(content(b2)) ?: throw Failed("odpověď není JSON"), "Pollinations · GPT-OSS 20B")
        }
        if (code !in 200..299) throw Failed("HTTP $code ${errorMessage(body)}")
        val json = Prompts.parseJson(content(body)) ?: throw Failed("odpověď není JSON")
        return Answer(json, "Pollinations · GPT-OSS 20B")
    }

    // --- On the phone ------------------------------------------------------------

    private fun local(context: Context, user: String, maxTokens: Int): Answer? {
        val model = LocalLlm.file(context)
        if (!model.exists()) return null
        // Gemma (3 GB) next to Parakeet while recording risks running out of memory
        // on an 8 GB phone; the caller retries once the recording ends.
        if (io.github.fush1ft.summarised.capture.CaptureController.state.value.phase ==
            io.github.fush1ft.summarised.capture.CaptureController.Phase.RECORDING
        ) {
            throw Failed("lokální model počká na konec nahrávání")
        }
        val r = Native.json(
            Native.instance.generate(model.path, Prompts.SYSTEM.toByteArray(), user.toByteArray(), 8192, 4, minOf(maxTokens, 900), 0f) { },
        )
        if (!r.optBoolean("ok")) throw Failed(r.optString("error"))
        val json = Prompts.parseJson(r.optString("text")) ?: throw Failed("odpověď není JSON")
        return Answer(json, "Telefon · Gemma 4 E2B")
    }

    // --- HTTP --------------------------------------------------------------------

    private fun chatBody(model: String?, user: String, maxTokens: Int): String =
        JSONObject()
            .put("messages", JSONArray()
                .put(JSONObject().put("role", "system").put("content", Prompts.SYSTEM))
                .put(JSONObject().put("role", "user").put("content", user)))
            .put("max_tokens", maxTokens)
            .put("temperature", 0.2)
            .apply { if (model != null) put("model", model) }
            .toString()

    private data class Response(val code: Int, val body: String, val retryAfterS: Int?)

    private fun post(url: String, key: String, body: String): Response {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 20_000
            readTimeout = 120_000
            requestMethod = "POST"
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            if (key.isNotBlank()) setRequestProperty("Authorization", "Bearer $key")
        }
        conn.outputStream.use { it.write(body.toByteArray()) }
        val code = conn.responseCode
        val text = runCatching {
            (if (code in 200..299) conn.inputStream else conn.errorStream)?.bufferedReader()?.readText().orEmpty()
        }.getOrDefault("")
        val retry = conn.getHeaderField("Retry-After")?.toIntOrNull()
        conn.disconnect()
        return Response(code, text, retry)
    }

    private fun content(body: String): String =
        JSONObject(body).optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("message")?.optString("content").orEmpty()

    private fun errorMessage(body: String): String =
        (runCatching { JSONObject(body).optJSONObject("error")?.optString("message") }.getOrNull() ?: body).take(200)
}
