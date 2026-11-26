package io.github.fush1ft.summarised.core.inference

import org.json.JSONObject

/**
 * JNI bridge. Results come back as UTF-8 JSON bytes rather than jstring, because
 * model output is arbitrary UTF-8 that NewStringUTF can't take.
 */
class Native private constructor() {
    fun interface TokenListener {
        fun onToken(piece: ByteArray)
    }

    external fun systemInfo(): ByteArray
    external fun cancel()

    // One-shot transcription (engine 0 = Whisper, 1 = Parakeet).
    external fun transcribe(engine: Int, modelPath: String, samples: FloatArray, threads: Int, language: String): ByteArray

    // Persistent Parakeet context: open once, transcribe many stretches, close.
    external fun parakeetOpen(modelPath: String): Long
    external fun parakeetTranscribe(handle: Long, samples: FloatArray, threads: Int): ByteArray
    external fun parakeetClose(handle: Long)

    external fun generate(
        modelPath: String, system: ByteArray, user: ByteArray,
        nCtx: Int, threads: Int, maxTokens: Int, temperature: Float, listener: TokenListener,
    ): ByteArray

    companion object {
        /** Loaded on first use, so screens that never need inference never load it. */
        val instance: Native by lazy {
            System.loadLibrary("summarised_inference")
            Native()
        }

        fun json(bytes: ByteArray): JSONObject = JSONObject(String(bytes, Charsets.UTF_8))
    }
}

/** A loaded Parakeet model. Not thread-safe: use from one worker thread. */
class ParakeetEngine private constructor(private var handle: Long) : AutoCloseable {

    data class Segment(val startMs: Long, val endMs: Long, val text: String)

    /** Transcribes 16 kHz mono audio; times are relative to the start of [samples]. */
    fun transcribe(samples: FloatArray, threads: Int = 4): List<Segment> {
        check(handle != 0L) { "engine closed" }
        val r = Native.json(Native.instance.parakeetTranscribe(handle, samples, threads))
        if (!r.optBoolean("ok")) error(r.optString("error"))
        val arr = r.optJSONArray("segments") ?: return emptyList()
        return (0 until arr.length()).map { arr.getJSONObject(it) }
            .map { Segment(it.optLong("t0"), it.optLong("t1"), it.optString("text").trim()) }
            .filter { it.text.isNotBlank() }
    }

    override fun close() {
        if (handle != 0L) {
            Native.instance.parakeetClose(handle)
            handle = 0L
        }
    }

    companion object {
        /** Returns null when the file can't be loaded as a Parakeet model. */
        fun open(modelPath: String): ParakeetEngine? {
            val h = Native.instance.parakeetOpen(modelPath)
            return if (h == 0L) null else ParakeetEngine(h)
        }
    }
}
