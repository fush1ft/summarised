package io.github.fush1ft.summarised

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

/** Small settings store. Keys never leave the phone (backups are disabled). */
object AppPrefs {
    enum class Mode { EASY, ADVANCED }

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences("summarised", Context.MODE_PRIVATE)

    fun onboarded(context: Context) = prefs(context).getBoolean("onboarded", false)
    fun setOnboarded(context: Context) = prefs(context).edit { putBoolean("onboarded", true) }

    fun mode(context: Context): Mode =
        prefs(context).getString("mode", null)?.let { n -> Mode.entries.firstOrNull { it.name == n } } ?: Mode.EASY
    fun setMode(context: Context, mode: Mode) = prefs(context).edit { putString("mode", mode.name) }

    fun groqKey(context: Context): String = prefs(context).getString("groq_key", "").orEmpty()
    fun setGroqKey(context: Context, key: String) = prefs(context).edit { putString("groq_key", key.trim()) }

    /** Advanced mode: keep everything on the phone. */
    fun onDeviceOnly(context: Context) = prefs(context).getBoolean("on_device_only", false)
    fun setOnDeviceOnly(context: Context, value: Boolean) = prefs(context).edit { putBoolean("on_device_only", value) }

    fun hapticUsage(context: Context): String = prefs(context).getString("haptic_usage", "ALARM") ?: "ALARM"
    fun setHapticUsage(context: Context, usage: String) = prefs(context).edit { putString("haptic_usage", usage) }

    fun gestureConfirmed(context: Context) = prefs(context).getBoolean("gesture_done", false)
    fun setGestureConfirmed(context: Context) = prefs(context).edit { putBoolean("gesture_done", true) }
}
