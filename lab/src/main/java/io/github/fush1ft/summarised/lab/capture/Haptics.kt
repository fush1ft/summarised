package io.github.fush1ft.summarised.lab.capture

import android.content.Context
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.VibratorManager
import io.github.fush1ft.summarised.lab.log.EventLog

/**
 * The haptic vocabulary of the app. The lab lets you pick the vibration "usage",
 * because Android scales or mutes vibrations per usage (touch feedback off,
 * Do Not Disturb, …) and we need the one that is reliably felt in a pocket.
 */
object Haptics {
    enum class Pattern(val label: String) {
        START("Start"), BOOKMARK("Záložka"), UNDO("Zpět"), STOP("Stop"), ERROR("Chyba")
    }

    enum class Usage(val label: String, val value: Int) {
        TOUCH("Touch", VibrationAttributes.USAGE_TOUCH),
        NOTIFICATION("Notification", VibrationAttributes.USAGE_NOTIFICATION),
        ALARM("Alarm", VibrationAttributes.USAGE_ALARM),
        ACCESSIBILITY("Accessibility", VibrationAttributes.USAGE_ACCESSIBILITY),
        MEDIA("Media", VibrationAttributes.USAGE_MEDIA),
    }

    fun usage(context: Context): Usage =
        Settings.prefs(context).getString("haptic_usage", null)
            ?.let { name -> Usage.entries.firstOrNull { it.name == name } }
            ?: Usage.ALARM

    fun setUsage(context: Context, usage: Usage) {
        Settings.prefs(context).edit().putString("haptic_usage", usage.name).apply()
    }

    fun play(context: Context, pattern: Pattern) {
        val vibrator = context.getSystemService(VibratorManager::class.java).defaultVibrator
        val usage = usage(context)
        val primitives = intArrayOf(
            VibrationEffect.Composition.PRIMITIVE_CLICK,
            VibrationEffect.Composition.PRIMITIVE_TICK,
            VibrationEffect.Composition.PRIMITIVE_THUD,
        )
        val rich = vibrator.areAllPrimitivesSupported(*primitives)
        val effect = if (rich) composed(pattern) else waveform(pattern)
        vibrator.vibrate(effect, VibrationAttributes.createForUsage(usage.value))
        EventLog.add("HAPTIC", "${pattern.label} via ${usage.label} (${if (rich) "primitives" else "waveform"})")
    }

    private fun composed(p: Pattern): VibrationEffect {
        val c = VibrationEffect.startComposition()
        when (p) {
            Pattern.START -> c
                .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 0.5f)
                .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 1.0f, 120)
            Pattern.BOOKMARK -> c
                .addPrimitive(VibrationEffect.Composition.PRIMITIVE_THUD, 1.0f)
            Pattern.UNDO -> c
                .addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, 0.6f)
            Pattern.STOP -> c
                .addPrimitive(VibrationEffect.Composition.PRIMITIVE_THUD, 1.0f)
                .addPrimitive(VibrationEffect.Composition.PRIMITIVE_THUD, 0.6f, 150)
                .addPrimitive(VibrationEffect.Composition.PRIMITIVE_THUD, 0.3f, 150)
            Pattern.ERROR -> c
                .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 1.0f)
                .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 1.0f, 90)
                .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 1.0f, 90)
        }
        return c.compose()
    }

    private fun waveform(p: Pattern): VibrationEffect = when (p) {
        Pattern.START -> VibrationEffect.createWaveform(longArrayOf(0, 40, 100, 60), intArrayOf(0, 120, 0, 255), -1)
        Pattern.BOOKMARK -> VibrationEffect.createOneShot(80, 255)
        Pattern.UNDO -> VibrationEffect.createOneShot(25, 120)
        Pattern.STOP -> VibrationEffect.createOneShot(350, 255)
        Pattern.ERROR -> VibrationEffect.createWaveform(longArrayOf(0, 60, 80, 60, 80, 60), intArrayOf(0, 255, 0, 255, 0, 255), -1)
    }
}
