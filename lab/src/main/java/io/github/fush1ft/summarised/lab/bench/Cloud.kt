package io.github.fush1ft.summarised.lab.bench

import android.content.Context
import io.github.fush1ft.summarised.lab.capture.Settings
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Optional cloud summarisers for comparison with the local models. All of them
 * speak the OpenAI chat-completions protocol, so one small client covers them.
 * Keys stay in the app's private storage on the phone.
 */
object Cloud {
    data class Provider(
        val id: String,
        val name: String,
        val chatUrl: String,
        val modelsUrl: String?,
        /** null = works without a key. */
        val keyUrl: String?,
        val prefer: List<String>,
        val note: String,
        val maxTokens: Int,
        val freeModelsOnly: Boolean = false,
    )

    val providers = listOf(
        Provider(
            "pollinations", "Pollinations",
            "https://text.pollinations.ai/openai", null, null, emptyList(),
            "Bez registrace a bez klíče. Model GPT-OSS 20B, jeden dotaz za 45 s, nejvýš 100 denně. Bez záruky dostupnosti.",
            maxTokens = 800,
        ),
        Provider(
            "groq", "Groq",
            "https://api.groq.com/openai/v1/chat/completions", "https://api.groq.com/openai/v1/models",
            "https://console.groq.com/keys", listOf("gpt-oss-120b", "llama-3.3-70b", "qwen3-32b"),
            "Velmi rychlý. Registrace zdarma bez karty, štědré denní limity.",
            maxTokens = 2048,
        ),
        Provider(
            "gemini", "Google Gemini",
            "https://generativelanguage.googleapis.com/v1beta/openai/chat/completions",
            "https://generativelanguage.googleapis.com/v1beta/openai/models",
            "https://aistudio.google.com/apikey", listOf("flash"),
            "Nejlepší čeština. Pozor: na free tieru může Google texty použít ke zlepšování svých služeb.",
            maxTokens = 2048,
        ),
        Provider(
            "cerebras", "Cerebras",
            "https://api.cerebras.ai/v1/chat/completions", "https://api.cerebras.ai/v1/models",
            "https://cloud.cerebras.ai", listOf("gpt-oss-120b", "qwen-3", "llama"),
            "Nejrychlejší. Registrace zdarma.",
            maxTokens = 2048,
        ),
        Provider(
            "openrouter", "OpenRouter",
            "https://openrouter.ai/api/v1/chat/completions", "https://openrouter.ai/api/v1/models",
            "https://openrouter.ai/keys", emptyList(),
            "Mnoho modelů na jednom místě, zdarma jen modely končící „:free“ (50 dotazů denně).",
            maxTokens = 2048, freeModelsOnly = true,
        ),
    )

    fun key(context: Context, p: Provider): String = Settings.prefs(context).getString("cloud_key_${p.id}", "").orEmpty()

    fun setKey(context: Context, p: Provider, key: String) {
        Settings.prefs(context).edit().putString("cloud_key_${p.id}", key.trim()).apply()
    }

    fun model(context: Context, p: Provider): String? = Settings.prefs(context).getString("cloud_model_${p.id}", null)

    fun setModel(context: Context, p: Provider, model: String) {
        Settings.prefs(context).edit().putString("cloud_model_${p.id}", model).apply()
    }

    /** Model ids the key can use, best guesses first. */
    fun listModels(p: Provider, key: String): List<String> {
        val url = p.modelsUrl ?: return emptyList()
        val conn = open(url, key)
        val code = conn.responseCode
        if (code !in 200..299) error("HTTP $code: ${readError(conn)}")
        val body = JSONObject(conn.inputStream.bufferedReader().readText())
        conn.disconnect()
        val data = body.optJSONArray("data") ?: body.optJSONArray("models") ?: JSONArray()
        val ids = (0 until data.length()).mapNotNull { data.optJSONObject(it)?.optString("id")?.removePrefix("models/") }
            .filter { it.isNotBlank() }
            .filter { !p.freeModelsOnly || it.endsWith(":free") }
            .filterNot { id -> listOf("whisper", "tts", "embed", "guard", "image", "audio", "vision-only").any { id.contains(it, ignoreCase = true) } }
        return ids.sortedBy { id -> p.prefer.indexOfFirst { id.contains(it, ignoreCase = true) }.let { if (it < 0) 99 else it } }
    }

    data class Answer(val text: String, val promptTokens: Int, val genTokens: Int, val totalMs: Double, val model: String)

    fun chat(p: Provider, key: String, model: String?, system: String, user: String, temperature: Float): Answer {
        val body = JSONObject()
            .put("messages", JSONArray()
                .put(JSONObject().put("role", "system").put("content", system))
                .put(JSONObject().put("role", "user").put("content", user)))
            .put("max_tokens", p.maxTokens)
            .put("temperature", temperature.toDouble())
        if (!model.isNullOrBlank()) body.put("model", model)
        val started = System.nanoTime()
        val conn = open(p.chatUrl, key)
        conn.requestMethod = "POST"
        conn.doOutput = true
        conn.setRequestProperty("Content-Type", "application/json")
        conn.outputStream.use { it.write(body.toString().toByteArray()) }
        val code = conn.responseCode
        if (code !in 200..299) {
            val retry = conn.getHeaderField("Retry-After")
            val msg = readError(conn)
            error(
                when (code) {
                    401, 403 -> "Klíč nefunguje (HTTP $code). $msg"
                    402, 429 -> "Limit vyčerpán" + (retry?.let { ", další pokus za $it s" } ?: "") + ". $msg"
                    else -> "HTTP $code: $msg"
                },
            )
        }
        val json = JSONObject(conn.inputStream.bufferedReader().readText())
        conn.disconnect()
        val totalMs = (System.nanoTime() - started) / 1e6
        val message = json.optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("message")
        val text = message?.optString("content").orEmpty()
        val usage = json.optJSONObject("usage")
        return Answer(
            text = text,
            promptTokens = usage?.optInt("prompt_tokens") ?: 0,
            genTokens = usage?.optInt("completion_tokens") ?: 0,
            totalMs = totalMs,
            model = json.optString("model").ifBlank { model ?: p.name },
        )
    }

    private fun open(url: String, key: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 20_000
            readTimeout = 120_000
            if (key.isNotBlank()) setRequestProperty("Authorization", "Bearer $key")
        }

    private fun readError(conn: HttpURLConnection): String =
        runCatching { conn.errorStream?.bufferedReader()?.readText().orEmpty() }.getOrDefault("")
            .let { raw ->
                runCatching { JSONObject(raw).optJSONObject("error")?.optString("message") }.getOrNull()?.takeIf { it.isNotBlank() } ?: raw
            }
            .take(240)
}
