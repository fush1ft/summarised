package io.github.fush1ft.summarised.transcribe

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * The speech model: Parakeet TDT 0.6B v3 (NVIDIA, CC-BY-4.0), q8_0 build for
 * whisper.cpp. Chosen in M0: 7-8x faster than real time on the Pixel 10a.
 * Downloaded once, only when the user asks, and resumable.
 */
object SpeechModel {
    const val URL = "https://huggingface.co/ggml-org/parakeet-GGUF/resolve/main/ggml-parakeet-tdt-0.6b-v3-q8_0.bin"
    const val APPROX_MB = 668
    private const val FILE = "parakeet-v3-q8.bin"

    sealed interface Status {
        data object Missing : Status
        data class Downloading(val bytes: Long, val total: Long) : Status
        data class Failed(val reason: String) : Status
        data object Ready : Status
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _status = MutableStateFlow<Status>(Status.Missing)
    val status: StateFlow<Status> = _status

    fun file(context: Context): File = File(File(context.getExternalFilesDir(null), "models").apply { mkdirs() }, FILE)

    fun isReady(context: Context): Boolean = file(context).exists()

    fun refresh(context: Context) {
        if (_status.value !is Status.Downloading) _status.value = if (isReady(context)) Status.Ready else Status.Missing
    }

    fun delete(context: Context) {
        file(context).delete()
        File(file(context).path + ".part").delete()
        refresh(context)
    }

    fun download(context: Context) {
        if (_status.value is Status.Downloading) return
        val app = context.applicationContext
        _status.value = Status.Downloading(0, APPROX_MB * 1_000_000L)
        scope.launch {
            val target = file(app)
            val part = File(target.path + ".part")
            try {
                var url = URL(URL)
                var conn: HttpURLConnection
                var redirects = 0
                while (true) {
                    conn = url.openConnection() as HttpURLConnection
                    conn.instanceFollowRedirects = false
                    conn.connectTimeout = 20_000
                    conn.readTimeout = 60_000
                    if (part.length() > 0) conn.setRequestProperty("Range", "bytes=${part.length()}-")
                    val code = conn.responseCode
                    if (code in 300..399 && redirects++ < 8) {
                        url = URL(url, conn.getHeaderField("Location"))
                        conn.disconnect()
                        continue
                    }
                    break
                }
                val code = conn.responseCode
                if (code == 416 && part.length() > 0) {
                    conn.disconnect()
                } else {
                    if (code !in listOf(200, 206)) error("HTTP $code")
                    val append = code == 206
                    val already = if (append) part.length() else 0L
                    val total = already + conn.contentLengthLong.coerceAtLeast(0)
                    conn.inputStream.use { input ->
                        FileOutputStream(part, append).use { out ->
                            val buf = ByteArray(1 shl 16)
                            var got = already
                            var last = 0L
                            while (true) {
                                val n = input.read(buf)
                                if (n < 0) break
                                out.write(buf, 0, n)
                                got += n
                                if (got - last > 2_000_000) {
                                    _status.value = Status.Downloading(got, total)
                                    last = got
                                }
                            }
                        }
                    }
                }
                if (!part.renameTo(target)) error("soubor nejde uložit")
                _status.value = Status.Ready
            } catch (e: Exception) {
                _status.value = Status.Failed(e.message ?: e.javaClass.simpleName)
            }
        }
    }
}
