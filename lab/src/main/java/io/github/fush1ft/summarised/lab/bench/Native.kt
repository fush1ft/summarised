package io.github.fush1ft.summarised.lab.bench

import org.json.JSONObject

/**
 * JNI bridge to whisper.cpp (Whisper + Parakeet) and llama.cpp. Every call
 * returns UTF-8 JSON bytes; see lab_jni.cpp for why not jstring.
 */
class Native {
    fun interface TokenListener {
        fun onToken(piece: ByteArray)
    }

    external fun systemInfo(): ByteArray
    external fun cancel()
    external fun transcribe(engine: Int, modelPath: String, samples: FloatArray, threads: Int, language: String): ByteArray
    external fun generate(
        modelPath: String, system: ByteArray, user: ByteArray,
        nCtx: Int, threads: Int, maxTokens: Int, temperature: Float, listener: TokenListener,
    ): ByteArray

    companion object {
        /** Loaded lazily, so the trigger tests work even if the native code would crash. */
        val instance: Native by lazy {
            System.loadLibrary("summarised_lab")
            Native()
        }

        fun json(bytes: ByteArray): JSONObject = JSONObject(String(bytes, Charsets.UTF_8))
    }
}
