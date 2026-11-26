package io.github.fush1ft.summarised.lab.assist

import android.app.Activity
import android.os.Bundle
import io.github.fush1ft.summarised.lab.capture.CaptureController
import io.github.fush1ft.summarised.lab.capture.Haptics
import io.github.fush1ft.summarised.lab.log.EventLog

/**
 * Invisible activity launched by the assistant session (start mode
 * "trampoline"). Being a visible activity, it is allowed to start a
 * microphone foreground service even when the direct path is refused.
 */
class AssistTrampolineActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val source = intent.getStringExtra("source") ?: "trampoline"
        EventLog.add("TRAMPOLINE", "activity created, starting service")
        try {
            CaptureController.startService(this, "$source+trampoline")
        } catch (e: Exception) {
            CaptureController.fail("trampoline start failed: ${e.javaClass.simpleName}: ${e.message}")
            Haptics.play(this, Haptics.Pattern.ERROR)
        }
        finish()
    }
}
