package io.github.fush1ft.summarised.lab.assist

import android.service.voice.VoiceInteractionService
import io.github.fush1ft.summarised.lab.log.EventLog

/** Present only so the app can hold the digital-assistant role. */
class LabVoiceInteractionService : VoiceInteractionService() {
    override fun onReady() {
        super.onReady()
        EventLog.add("ASSIST", "VoiceInteractionService ready — the lab is the active assistant")
    }

    override fun onShutdown() {
        EventLog.add("ASSIST", "VoiceInteractionService shut down (another assistant selected?)")
        super.onShutdown()
    }

    override fun onLaunchVoiceAssistFromKeyguard() {
        EventLog.add("ASSIST", "onLaunchVoiceAssistFromKeyguard()")
        super.onLaunchVoiceAssistFromKeyguard()
    }
}
