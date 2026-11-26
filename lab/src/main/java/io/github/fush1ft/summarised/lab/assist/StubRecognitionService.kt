package io.github.fush1ft.summarised.lab.assist

import android.content.Intent
import android.speech.RecognitionService
import android.speech.SpeechRecognizer

/** Declared only because the assistant picker requires a recognizer. */
class StubRecognitionService : RecognitionService() {
    override fun onStartListening(recognizerIntent: Intent?, listener: Callback?) {
        runCatching { listener?.error(SpeechRecognizer.ERROR_CLIENT) }
    }

    override fun onCancel(listener: Callback?) = Unit

    override fun onStopListening(listener: Callback?) = Unit
}
