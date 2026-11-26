package io.github.fush1ft.summarised.capture

import android.content.Context
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.VibratorManager
import io.github.fush1ft.summarised.AppPrefs

/** The app's haptic vocabulary, felt through a pocket (verified in the M0 lab). */
object Haptics {
    enum class Pattern { START, BOOKMARK, UNDO, STOP, ERROR }

    enum class Usage(val label: String, val value: Int) {
        ALARM("Silná (jako budík)", VibrationAttributes.USAGE_ALARM),
        NOTIFICATION("Jako upozornění", VibrationAttributes.USAGE_NOTIFICATION),
        TOUCH("Jemná (jako klávesnice)", VibrationAttributes.USAGE_TOUCH),
    }

    fun usage(context: Context): Usage =
        Usage.entries.firstOrNull { it.name == AppPrefs.hapticUsage(context) } ?: Usage.ALARM

    fun play(context: Context, pattern: Pattern, usage: Usage = usage(context)) {
        val vibrator = context.getSystemService(VibratorManager::class.java).defaultVibrator
        val rich = vibrator.areAllPrimitivesSupported(
            VibrationEffect.Composition.PRIMITIVE_CLICK,
            VibrationEffect.Composition.PRIMITIVE_TICK,
            VibrationEffect.Composition.PRIMITIVE_THUD,
        )
        vibrator.vibrate(if (rich) composed(pattern) else waveform(pattern), VibrationAttributes.createForUsage(usage.value))
    }

    private fun composed(p: Pattern): VibrationEffect {
        val c = VibrationEffect.startComposition()
        when (p) {
            Pattern.START -> c
                .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 0.5f)
                .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 1.0f, 120)
            Pattern.BOOKMARK -> c.addPrimitive(VibrationEffect.Composition.PRIMITIVE_THUD, 1.0f)
            Pattern.UNDO -> c.addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, 0.6f)
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
