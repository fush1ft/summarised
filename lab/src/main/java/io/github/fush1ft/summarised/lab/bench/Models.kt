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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

enum class ModelKind { WHISPER, PARAKEET, LLM }

/**
 * A model is named by its Hugging Face repo and a list of preferred file-name
 * fragments. The exact file is looked up through the Hub API when you tap
 * Download, so a renamed quant never leaves a dead link in the app.
 */
data class ModelSpec(
    val id: String,
    val kind: ModelKind,
    val name: String,
    val blurb: String,
    val repo: String,
    val prefer: List<String>,
    val extension: String,
    val approxMb: Int,
    val license: String,
    val recommended: Boolean,
    val warning: String? = null,
) {
    /** Stored under our own id, so the app knows what a file is regardless of upstream naming. */
    val localName: String get() = "$id.$extension"
}

object Models {
    /** Outlives any screen, so leaving a screen doesn't cancel a 3 GB download. */
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val catalog = listOf(
        // Speech to text
        ModelSpec(
            "parakeet-v3", ModelKind.PARAKEET, "Parakeet v3",
            "Nový model od NVIDIA, umí 25 evropských jazyků včetně češtiny. Bývá nejrychlejší.",
            "ggml-org/parakeet-GGUF", listOf("parakeet-tdt-0.6b-v3-q8_0", "parakeet-tdt-0.6b-v3-f16", "parakeet-tdt-0.6b-v3"),
            "bin", 1250, "CC-BY-4.0", recommended = true,
        ),
        ModelSpec(
            "whisper-small", ModelKind.WHISPER, "Whisper small",
            "Malý a rychlý Whisper od OpenAI. Česky dělá víc chyb.",
            "ggerganov/whisper.cpp", listOf("ggml-small-q8_0.bin"),
            "bin", 264, "MIT", recommended = true,
        ),
        ModelSpec(
            "whisper-turbo", ModelKind.WHISPER, "Whisper turbo",
            "Velký Whisper v kompaktní verzi. Nejlepší čeština, ale pomalejší.",
            "ggerganov/whisper.cpp", listOf("ggml-large-v3-turbo-q5_0.bin"),
            "bin", 574, "MIT", recommended = true,
        ),
        ModelSpec(
            "whisper-turbo-q8", ModelKind.WHISPER, "Whisper turbo (q8)",
            "Přesnější varianta turba, o 300 MB větší.",
            "ggerganov/whisper.cpp", listOf("ggml-large-v3-turbo-q8_0.bin"),
            "bin", 874, "MIT", recommended = false,
        ),
        // Summaries
        ModelSpec(
            "gemma4-e2b", ModelKind.LLM, "Gemma 4 E2B",
            "Nejnovější malá Gemma od Googlu (2026). Stavěná pro telefony, umí 140+ jazyků.",
            "unsloth/gemma-4-E2B-it-GGUF", listOf("Q4_K_M", "Q4_K_XL", "Q4_0"),
            "gguf", 3100, "Apache-2.0", recommended = true,
        ),
        ModelSpec(
            "gemma4-e4b", ModelKind.LLM, "Gemma 4 E4B",
            "Větší Gemma 4. Nejspíš nejlepší čeština, ale náročná na paměť.",
            "unsloth/gemma-4-E4B-it-GGUF", listOf("Q4_K_M", "Q4_K_XL", "Q4_0"),
            "gguf", 5000, "Apache-2.0", recommended = true,
            warning = "Na 8 GB RAM je to na hraně: aplikace může spadnout. I to je užitečný výsledek.",
        ),
        ModelSpec(
            "qwen3-4b-2507", ModelKind.LLM, "Qwen3 4B",
            "Model od Alibaby. Rychlý a rozumí dobře, česká gramatika občas drhne.",
            "unsloth/Qwen3-4B-Instruct-2507-GGUF", listOf("Q4_K_M", "Q4_K_XL"),
            "gguf", 2500, "Apache-2.0", recommended = true,
        ),
        ModelSpec(
            "gemma3n-e4b", ModelKind.LLM, "Gemma 3n E4B",
            "Předchozí generace Gemmy pro telefony, pro srovnání s Gemmou 4.",
            "unsloth/gemma-3n-E4B-it-GGUF", listOf("Q4_K_M", "Q4_K_XL", "Q4_0"),
            "gguf", 4900, "Gemma", recommended = false,
            warning = "Na 8 GB RAM je to na hraně.",
        ),
        ModelSpec(
            "gemma3n-e2b", ModelKind.LLM, "Gemma 3n E2B",
            "Menší předchozí Gemma.",
            "unsloth/gemma-3n-E2B-it-GGUF", listOf("Q4_K_M", "Q4_K_XL", "Q4_0"),
            "gguf", 3000, "Gemma", recommended = false,
        ),
        ModelSpec(
            "qwen3-1.7b", ModelKind.LLM, "Qwen3 1.7B",
            "Nejmenší a nejrychlejší, na češtinu spíš slabý.",
            "unsloth/Qwen3-1.7B-GGUF", listOf("Q4_K_M", "Q4_K_XL"),
            "gguf", 1100, "Apache-2.0", recommended = false,
        ),
    )

    fun spec(file: File): ModelSpec? = catalog.firstOrNull { it.localName == file.name }

    fun displayName(file: File): String = spec(file)?.name ?: file.nameWithoutExtension

    data class Progress(
        val downloadedBytes: Long,
        val totalBytes: Long,
        val error: String? = null,
        val done: Boolean = false,
        val resolving: Boolean = false,
    ) {
        val fraction: Float get() = if (totalBytes > 0) (downloadedBytes.toFloat() / totalBytes).coerceIn(0f, 1f) else 0f
        val active: Boolean get() = !done && error == null
    }

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

    fun isInstalled(spec: ModelSpec): Boolean = _installed.value.any { it.name == spec.localName }

    fun kindOf(file: File): ModelKind = spec(file)?.kind ?: when {
        file.name.contains("parakeet") -> ModelKind.PARAKEET
        file.name.endsWith(".gguf") -> ModelKind.LLM
        else -> ModelKind.WHISPER
    }

    fun delete(context: Context, file: File) {
        file.delete()
        File(file.path + ".part").delete()
        refresh(context)
    }

    private fun update(id: String, p: Progress) {
        _progress.value = _progress.value + (id to p)
    }

    /** Downloads [specs] one after another (parallel downloads only fight for bandwidth). */
    fun downloadAll(context: Context, specs: List<ModelSpec>) {
        val app = context.applicationContext
        specs.filter { !isInstalled(it) && _progress.value[it.id]?.active != true }.forEach { update(it.id, Progress(0, 0, resolving = true)) }
        scope.launch {
            for (s in specs) {
                if (isInstalled(s)) continue
                download(app, s)
            }
        }
    }

    /** Asks the Hub which file in the repo matches our preference list. */
    private fun resolve(spec: ModelSpec): Pair<String, Long> {
        val conn = URL("https://huggingface.co/api/models/${spec.repo}/tree/main").openConnection() as HttpURLConnection
        conn.connectTimeout = 20_000
        conn.readTimeout = 30_000
        val code = conn.responseCode
        if (code != 200) error("Hugging Face API: HTTP $code pro ${spec.repo}")
        val files = JSONArray(conn.inputStream.bufferedReader().readText())
        conn.disconnect()
        val candidates = (0 until files.length()).map { files.getJSONObject(it) }
            .filter { it.optString("type") == "file" }
            .map { it.optString("path") to (it.optJSONObject("lfs")?.optLong("size") ?: it.optLong("size")) }
            .filter { (path, _) -> path.endsWith(".${spec.extension}") && !path.contains("mmproj", ignoreCase = true) && !path.contains('/') }
        for (fragment in spec.prefer) {
            candidates.firstOrNull { (path, _) -> path.contains(fragment, ignoreCase = true) }?.let { return it }
        }
        error("v ${spec.repo} není soubor ${spec.prefer.first()} (je tam: ${candidates.joinToString { it.first }.take(300)})")
    }

    /** Resumable download: keeps a .part file and continues with an HTTP Range request. */
    suspend fun download(context: Context, spec: ModelSpec) = withContext(Dispatchers.IO) {
        val target = File(dir(context), spec.localName)
        val part = File(dir(context), spec.localName + ".part")
        try {
            update(spec.id, Progress(part.length(), 0, resolving = true))
            val (path, size) = resolve(spec)
            EventLog.add("MODEL", "${spec.name}: ${spec.repo}/$path (${size / 1_000_000} MB)")
            fetch(spec.id, "https://huggingface.co/${spec.repo}/resolve/main/$path", part, target, size)
        } catch (e: Exception) {
            update(spec.id, Progress(part.length(), 0, error = e.message ?: e.javaClass.simpleName))
            EventLog.add("MODEL", "${spec.name}: ${e.message}")
        }
        refresh(context)
    }

    /** Download from an arbitrary URL (advanced screen). */
    suspend fun downloadUrl(context: Context, url: String) = withContext(Dispatchers.IO) {
        val name = url.substringAfterLast('/').substringBefore('?')
        val id = "custom:$name"
        try {
            fetch(id, url, File(dir(context), "$name.part"), File(dir(context), name), 0)
        } catch (e: Exception) {
            update(id, Progress(0, 0, error = e.message ?: e.javaClass.simpleName))
        }
        refresh(context)
    }

    private fun fetch(id: String, url: String, part: File, target: File, expected: Long) {
        var current = URL(url)
        var conn: HttpURLConnection
        var redirects = 0
        while (true) {
            conn = current.openConnection() as HttpURLConnection
            conn.instanceFollowRedirects = false
            conn.connectTimeout = 20_000
            conn.readTimeout = 60_000
            if (part.exists() && part.length() > 0) conn.setRequestProperty("Range", "bytes=${part.length()}-")
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
        if (code == 416 && part.length() > 0) {
            conn.disconnect()
            if (!part.renameTo(target)) error("nepodařilo se přejmenovat soubor")
            update(id, Progress(target.length(), target.length(), done = true))
            return
        }
        if (code !in listOf(200, 206)) error("HTTP $code")
        val append = code == 206
        val already = if (append) part.length() else 0L
        val total = (already + conn.contentLengthLong.coerceAtLeast(0L)).takeIf { it > 0 } ?: expected
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
                    if (got - lastReport > 1_000_000) {
                        update(id, Progress(got, total))
                        lastReport = got
                    }
                }
            }
        }
        if (!part.renameTo(target)) error("nepodařilo se přejmenovat soubor")
        update(id, Progress(target.length(), target.length(), done = true))
        EventLog.add("MODEL", "staženo ${target.name} (${target.length() / 1_000_000} MB)")
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
                    var last = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        got += n
                        if (got - last > 20_000_000) {
                            update(id, Progress(got, 0))
                            last = got
                        }
                    }
                }
            }
            update(id, Progress(target.length(), target.length(), done = true))
            EventLog.add("MODEL", "importováno $name (${target.length() / 1_000_000} MB)")
        } catch (e: Exception) {
            target.delete()
            update(id, Progress(0, 0, error = e.message))
        }
        refresh(context)
    }
}
