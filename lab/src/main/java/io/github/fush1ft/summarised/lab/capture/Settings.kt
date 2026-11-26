package io.github.fush1ft.summarised.lab.capture

import android.content.Context
import android.content.SharedPreferences

object Settings {
    fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences("lab", Context.MODE_PRIVATE)

    /** How the assistant session starts the microphone service. */
    enum class StartMode(val label: String) {
        DIRECT("Přímo ze session"),
        TRAMPOLINE("Přes neviditelnou aktivitu"),
    }

    fun startMode(context: Context): StartMode =
        prefs(context).getString("start_mode", null)
            ?.let { name -> StartMode.entries.firstOrNull { it.name == name } }
            ?: StartMode.DIRECT

    fun setStartMode(context: Context, mode: StartMode) {
        prefs(context).edit().putString("start_mode", mode.name).apply()
    }
}
