package io.github.fush1ft.summarised.lab.assist

import android.app.KeyguardManager
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.service.voice.VoiceInteractionSession
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import io.github.fush1ft.summarised.lab.capture.CaptureController
import io.github.fush1ft.summarised.lab.capture.Haptics
import io.github.fush1ft.summarised.lab.log.EventLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * What a power-button hold opens. It decides start-vs-bookmark, confirms with a
 * haptic, shows a small card (also over the lock screen) and closes itself.
 */
class LabSession(context: Context) : VoiceInteractionSession(context) {

    private val main = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var watch: Job? = null
    private lateinit var title: TextView
    private lateinit var subtitle: TextView
    private val autoClose = Runnable { finish() }

    override fun onCreate() {
        super.onCreate()
        // We never need the screen contents; skip collecting them (faster, private).
        setDisabledShowContext(SHOW_WITH_ASSIST or SHOW_WITH_SCREENSHOT)
    }

    override fun onCreateContentView(): View {
        val dp = { v: Int -> TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), context.resources.displayMetrics).toInt() }
        title = TextView(context).apply {
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
        }
        subtitle = TextView(context).apply {
            setTextColor(Color.argb(200, 255, 255, 255))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
        }
        val undo = Button(context).apply {
            text = "Zpět"
            setOnClickListener {
                CaptureController.undoLastBookmark("session")
                Haptics.play(context, Haptics.Pattern.UNDO)
                finish()
            }
        }
        val stop = Button(context).apply {
            text = "■ Stop"
            setOnClickListener {
                CaptureController.stop(context, "session")
                finish()
            }
        }
        val buttons = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            addView(undo)
            addView(stop)
        }
        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(16), dp(20), dp(8))
            background = GradientDrawable().apply {
                cornerRadius = dp(24).toFloat()
                setColor(Color.argb(235, 24, 24, 32))
            }
            addView(title)
            addView(subtitle)
            addView(buttons)
            isClickable = true // taps on the card don't close it
        }
        return FrameLayout(context).apply {
            setOnClickListener { finish() }
            addView(
                card,
                FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM)
                    .apply { setMargins(dp(12), 0, dp(12), dp(32)) },
            )
        }
    }

    override fun onShow(args: Bundle?, showFlags: Int) {
        super.onShow(args, showFlags)
        val locked = context.getSystemService(KeyguardManager::class.java).isKeyguardLocked
        EventLog.add("ASSIST", "onShow flags=${describe(showFlags)} locked=$locked args=${describe(args)}")

        val outcome = CaptureController.onTrigger(context, if (locked) "power-hold(locked)" else "power-hold") { intent ->
            startAssistantActivity(intent)
            EventLog.add("ASSIST", "startAssistantActivity(trampoline) called")
        }
        when (outcome) {
            is CaptureController.Outcome.Bookmarked -> {
                Haptics.play(context, Haptics.Pattern.BOOKMARK)
                show("🔖 Záložka ${outcome.index}", "v ${CaptureController.formatMs(outcome.atMs)}")
                closeIn(2500)
            }
            CaptureController.Outcome.Starting -> {
                show("Spouštím nahrávání…", "")
                watchStart()
            }
            CaptureController.Outcome.Debounced -> {
                show("…", "příliš rychle po sobě")
                closeIn(1200)
            }
            is CaptureController.Outcome.Failed -> {
                Haptics.play(context, Haptics.Pattern.ERROR)
                show("✕ Nepodařilo se spustit", outcome.reason)
                closeIn(6000)
            }
        }
    }

    /** The service confirms (and plays the start haptic) once real audio flows. */
    private fun watchStart() {
        watch?.cancel()
        watch = scope.launch {
            CaptureController.state.collect { s ->
                when (s.phase) {
                    CaptureController.Phase.RECORDING -> {
                        show("● Nahrávám", "podrž znovu = záložka")
                        closeIn(2500)
                        watch?.cancel()
                    }
                    CaptureController.Phase.FAILED -> {
                        show("✕ Nepodařilo se spustit", s.error ?: "")
                        closeIn(6000)
                        watch?.cancel()
                    }
                    else -> Unit
                }
            }
        }
        // If nothing happens within 5 s the start was silently dropped.
        main.postDelayed({
            if (CaptureController.state.value.phase == CaptureController.Phase.STARTING) {
                CaptureController.fail("no confirmation from the service within 5 s")
            }
        }, 5000)
    }

    private fun show(t: String, s: String) {
        if (::title.isInitialized) {
            title.text = t
            subtitle.text = s
        }
    }

    private fun closeIn(ms: Long) {
        main.removeCallbacks(autoClose)
        main.postDelayed(autoClose, ms)
    }

    override fun onHide() {
        main.removeCallbacks(autoClose)
        super.onHide()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun describe(flags: Int): String {
        val names = listOf(
            1 to "WITH_ASSIST", 2 to "WITH_SCREENSHOT", 4 to "SOURCE_ASSIST_GESTURE",
            8 to "SOURCE_APPLICATION", 16 to "SOURCE_ACTIVITY", 32 to "SOURCE_PUSH_TO_TALK",
            64 to "SOURCE_NOTIFICATION", 128 to "SOURCE_AUTOMOTIVE",
        ).filter { flags and it.first != 0 }.map { it.second }
        return "$flags${if (names.isEmpty()) "" else names.joinToString("|", "(", ")")}"
    }

    @Suppress("DEPRECATION")
    private fun describe(args: Bundle?): String =
        args?.keySet()?.joinToString(",", "{", "}") { "$it=${args.get(it)}" } ?: "null"
}
