package io.github.fush1ft.summarised.lab.bench

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import io.github.fush1ft.summarised.lab.log.EventLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

enum class ModelKind { WHISPER, PARAKEET, LLM }

data class ModelSpec(
    val id: String,
    val kind: ModelKind,
    val label: String,
    val url: String,
    val sizeMb: Int,
    val license: String,
) {
    val fileName: String get() = url.substringAfterLast('/').substringBefore('?')
}

object Models {
    /** Outlives any screen, so leaving the tab doesn't cancel a 2 GB download. */
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Candidates for M0. URLs point at Hugging Face; nothing is downloaded until
     * you tap. If a URL is wrong, use "own URL" or import a file you already have.
     */
    val catalog = listOf(
        ModelSpec("whisper-small-q8", ModelKind.WHISPER, "Whisper small (q8_0)",
            "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-small-q8_0.bin", 264, "MIT"),
        ModelSpec("whisper-turbo-q5", ModelKind.WHISPER, "Whisper large-v3-turbo (q5_0)",
            "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-large-v3-turbo-q5_0.bin", 574, "MIT"),
        ModelSpec("whisper-turbo-q8", ModelKind.WHISPER, "Whisper large-v3-turbo (q8_0)",
            "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-large-v3-turbo-q8_0.bin", 874, "MIT"),
        ModelSpec("parakeet-v3-f16", ModelKind.PARAKEET, "Parakeet TDT 0.6B v3 (f16, 25 jazyků vč. češtiny)",
            "https://huggingface.co/ggml-org/parakeet-GGUF/resolve/main/ggml-parakeet-tdt-0.6b-v3-f16.bin", 1250, "CC-BY-4.0"),
        ModelSpec("qwen3-4b-2507", ModelKind.LLM, "Qwen3 4B Instruct 2507 (Q4_K_M)",
            "https://huggingface.co/unsloth/Qwen3-4B-Instruct-2507-GGUF/resolve/main/Qwen3-4B-Instruct-2507-Q4_K_M.gguf", 2500, "Apache-2.0"),
        ModelSpec("gemma3n-e2b", ModelKind.LLM, "Gemma 3n E2B (Q4_K_M)",
            "https://huggingface.co/unsloth/gemma-3n-E2B-it-GGUF/resolve/main/gemma-3n-E2B-it-Q4_K_M.gguf", 3000, "Gemma"),
        ModelSpec("qwen3-1.7b", ModelKind.LLM, "Qwen3 1.7B (Q4_K_M)",
            "https://huggingface.co/unsloth/Qwen3-1.7B-GGUF/resolve/main/Qwen3-1.7B-Q4_K_M.gguf", 1100, "Apache-2.0"),
    )

    data class Progress(val downloadedBytes: Long, val totalBytes: Long, val error: String? = null, val done: Boolean = false)

    private val _progress = MutableStateFlow<Map<String, Progress>>(emptyMap())
    val progress: StateFlow<Map<String, Progress>> = _progress
    private val _installed = MutableStateFlow<List<File>>(emptyList())
    val installed: StateFlow<List<File>> = _installed

    fun dir(context: Context): File = File(context.getExternalFilesDir(null), "models").apply { mkdirs() }

    fun refresh(context: Context) {
        _installed.value = dir(context).listFiles()
            ?.filter { it.isFile && !it.name.endsWith(".part") }
            ?.sortedBy { it.name }
            .orEmpty()
    }

    /** Guesses the engine from the file name; Parakeet and Whisper share the .bin suffix. */
    fun kindOf(file: File): ModelKind = when {
        file.name.endsWith(".gguf") && !file.name.contains("parakeet") -> ModelKind.LLM
        file.name.contains("parakeet") -> ModelKind.PARAKEET
        else -> ModelKind.WHISPER
    }

    fun delete(context: Context, file: File) {
        file.delete()
        File(file.path + ".part").delete()
        refresh(context)
    }

    /** Resumable download: keeps a .part file and continues with an HTTP Range request. */
    suspend fun download(context: Context, id: String, url: String) = withContext(Dispatchers.IO) {
        val name = url.substringAfterLast('/').substringBefore('?')
        val target = File(dir(context), name)
        val part = File(dir(context), "$name.part")
        fun update(p: Progress) { _progress.value = _progress.value + (id to p) }
        try {
            var redirects = 0
            var current = URL(url)
            var conn: HttpURLConnection
            while (true) {
                conn = current.openConnection() as HttpURLConnection
                conn.instanceFollowRedirects = false
                conn.connectTimeout = 20_000
                conn.readTimeout = 60_000
                if (part.exists()) conn.setRequestProperty("Range", "bytes=${part.length()}-")
                val code = conn.responseCode
                if (code in 300..399 && redirects < 8) {
                    current = URL(current, conn.getHeaderField("Location"))
                    conn.disconnect()
                    redirects++
                    continue
                }
                break
            }
            val code = conn.responseCode
            if (code == 416 && part.exists() && part.length() > 0) {
                // The .part file is already complete.
                conn.disconnect()
                if (!part.renameTo(target)) error("rename failed")
                update(Progress(target.length(), target.length(), done = true))
                refresh(context)
                return@withContext
            }
            if (code !in listOf(200, 206)) error("HTTP $code")
            val append = code == 206
            val already = if (append) part.length() else 0L
            val total = already + conn.contentLengthLong.coerceAtLeast(0L)
            EventLog.add("MODEL", "download $name: HTTP $code, ${total / 1_000_000} MB")
            conn.inputStream.use { input ->
                java.io.FileOutputStream(part, append).use { out ->
                    val buf = ByteArray(1 shl 16)
                    var got = already
                    var lastReport = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        got += n
                        if (got - lastReport > 2_000_000) {
                            update(Progress(got, total))
                            lastReport = got
                        }
                    }
                    update(Progress(got, total))
                }
            }
            if (!part.renameTo(target)) error("rename failed")
            update(Progress(target.length(), target.length(), done = true))
            EventLog.add("MODEL", "downloaded $name (${target.length() / 1_000_000} MB)")
        } catch (e: Exception) {
            update(Progress(part.length(), 0, error = "${e.javaClass.simpleName}: ${e.message}"))
            EventLog.add("MODEL", "download $name failed: ${e.message} (tap again to resume)")
        }
        refresh(context)
    }

    /** Copies a model the user already has on the phone (Files app) into the lab. */
    suspend fun import(context: Context, uri: Uri) = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val name = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        } ?: "imported-${System.currentTimeMillis()}.bin"
        val id = "import:$name"
        val target = File(dir(context), name)
        try {
            resolver.openInputStream(uri)!!.use { input ->
                target.outputStream().use { out ->
                    val buf = ByteArray(1 shl 16)
                    var got = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        got += n
                        if (got % 50_000_000 < buf.size) _progress.value = _progress.value + (id to Progress(got, 0))
                    }
                }
            }
            _progress.value = _progress.value + (id to Progress(target.length(), target.length(), done = true))
            EventLog.add("MODEL", "imported $name (${target.length() / 1_000_000} MB)")
        } catch (e: Exception) {
            target.delete()
            _progress.value = _progress.value + (id to Progress(0, 0, error = e.message))
        }
        refresh(context)
    }
}
