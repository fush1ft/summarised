package io.github.fush1ft.summarised.lab.capture

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.IBinder
import android.os.SystemClock
import io.github.fush1ft.summarised.lab.log.EventLog
import io.github.fush1ft.summarised.lab.log.Results
import io.github.fush1ft.summarised.lab.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Microphone foreground service. Writes 16 kHz mono WAV and reports, every few
 * seconds, whether the audio is real or silenced by the system — Android hands a
 * background app all-zero audio instead of an error, so "it started" is not
 * enough; we have to check that sound actually arrives.
 */
class RecordingService : Service() {

    companion object {
        const val ACTION_START = "start"
        const val ACTION_STOP = "stop"
        const val ACTION_BOOKMARK = "bookmark"
        const val ACTION_UNDO = "undo"
        private const val CHANNEL = "recording"
        private const val NOTIFICATION_ID = 1
        private const val SAMPLE_RATE = 16_000
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    @Volatile private var running = false
    private var thread: Thread? = null
    private var file: File? = null
    private var startedAtElapsed = 0L
    private var startSource = "?"

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> start(intent.getStringExtra("source") ?: "?")
            ACTION_STOP -> stop("notification/app")
            ACTION_BOOKMARK -> {
                if (CaptureController.addBookmark("notification") is CaptureController.Outcome.Bookmarked) {
                    Haptics.play(this, Haptics.Pattern.BOOKMARK)
                }
            }
            ACTION_UNDO -> {
                CaptureController.undoLastBookmark("notification")
                Haptics.play(this, Haptics.Pattern.UNDO)
            }
        }
        return START_NOT_STICKY
    }

    private fun start(source: String) {
        if (running) return
        startSource = source
        ensureChannel()
        try {
            startForeground(NOTIFICATION_ID, notification(0), ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
            EventLog.add("FGS", "startForeground(microphone) OK")
        } catch (e: Exception) {
            CaptureController.fail("startForeground failed: ${e.javaClass.simpleName}: ${e.message}")
            Haptics.play(this, Haptics.Pattern.ERROR)
            stopSelf()
            return
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            CaptureController.fail("RECORD_AUDIO not granted")
            Haptics.play(this, Haptics.Pattern.ERROR)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }
        val dir = File(getExternalFilesDir(null), "recordings").apply { mkdirs() }
        val out = File(dir, "rec-${System.currentTimeMillis()}.wav")
        file = out
        running = true
        thread = Thread({ captureLoop(out) }, "lab-capture").also { it.start() }

        // Keep the notification's bookmark count current.
        scope.launch {
            CaptureController.state.map { it.bookmarksMs.size }.distinctUntilChanged().collect { n ->
                if (running) getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification(n))
            }
        }
    }

    @SuppressLint("MissingPermission") // checked in start()
    private fun captureLoop(out: File) {
        val minBuf = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val record = try {
            AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION, SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(minBuf * 4, SAMPLE_RATE),
            )
        } catch (e: Exception) {
            failFromThread("AudioRecord(): ${e.javaClass.simpleName}: ${e.message}")
            return
        }
        if (record.state != AudioRecord.STATE_INITIALIZED) {
            record.release()
            failFromThread("AudioRecord not initialized (mic busy?)")
            return
        }
        record.startRecording()
        if (record.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
            record.release()
            failFromThread("startRecording() did not start (mic in use by another app?)")
            return
        }
        startedAtElapsed = SystemClock.elapsedRealtime()
        CaptureController.recordingStarted(startedAtElapsed)
        EventLog.add("AUDIO", "recording → ${out.name}")
        scope.launch { Haptics.play(this@RecordingService, Haptics.Pattern.START) }

        val writer = WavWriter(out, SAMPLE_RATE)
        val buf = ShortArray(SAMPLE_RATE / 10)
        var windowSamples = 0
        var sumSq = 0.0
        var peak = 0
        var windows = 0
        var lastSilenced: Boolean? = null
        var silentWindows = 0
        var totalWindows = 0
        try {
            while (running) {
                val n = record.read(buf, 0, buf.size)
                if (n <= 0) {
                    EventLog.add("AUDIO", "read() returned $n")
                    if (n == AudioRecord.ERROR_DEAD_OBJECT) break
                    continue
                }
                writer.write(buf, n)
                for (i in 0 until n) {
                    val v = buf[i].toInt()
                    sumSq += v.toDouble() * v
                    peak = maxOf(peak, abs(v))
                }
                windowSamples += n
                if (windowSamples >= SAMPLE_RATE * 5) {
                    val rms = sqrt(sumSq / windowSamples)
                    val clientSilenced = record.activeRecordingConfiguration?.isClientSilenced ?: false
                    val silenced = clientSilenced || peak == 0
                    totalWindows++
                    if (silenced) silentWindows++
                    // Log the first few windows, then only changes.
                    if (windows < 3 || silenced != lastSilenced) {
                        EventLog.add(
                            "AUDIO",
                            "t=${CaptureController.formatMs(writer.durationMs())} rms=${"%.0f".format(rms)} peak=$peak" +
                                (if (clientSilenced) " SILENCED-BY-SYSTEM" else "") +
                                (if (peak == 0) " ALL-ZERO" else ""),
                        )
                    }
                    if (silenced != lastSilenced) CaptureController.setSilenced(silenced)
                    lastSilenced = silenced
                    windows++
                    windowSamples = 0; sumSq = 0.0; peak = 0
                }
            }
        } finally {
            runCatching { record.stop() }
            record.release()
            writer.close()
            val state = CaptureController.state.value
            Results.add(
                "capture",
                JSONObject()
                    .put("source", startSource)
                    .put("file", out.name)
                    .put("duration_ms", writer.durationMs())
                    .put("bookmarks_ms", JSONArray(state.bookmarksMs))
                    .put("silent_windows", silentWindows)
                    .put("windows", totalWindows),
            )
            EventLog.add(
                "AUDIO",
                "stopped: ${CaptureController.formatMs(writer.durationMs())}, " +
                    "${state.bookmarksMs.size} bookmarks, silent windows $silentWindows/$totalWindows",
            )
        }
    }

    private fun failFromThread(reason: String) {
        running = false
        CaptureController.fail(reason)
        scope.launch {
            Haptics.play(this@RecordingService, Haptics.Pattern.ERROR)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun stop(source: String) {
        if (!running) {
            stopSelf()
            return
        }
        EventLog.add("TRIGGER", "stop via $source")
        running = false
        thread?.join(2000)
        CaptureController.recordingStopped()
        Haptics.play(this, Haptics.Pattern.STOP)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        if (running) {
            EventLog.add("FGS", "onDestroy while recording")
            running = false
            thread?.join(2000)
            CaptureController.recordingStopped()
        }
        scope.cancel()
        super.onDestroy()
    }

    private fun ensureChannel() {
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "Nahrávání", NotificationManager.IMPORTANCE_LOW).apply {
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                },
            )
        }
    }

    private fun action(action: String, request: Int): PendingIntent =
        PendingIntent.getService(
            this, request, Intent(this, RecordingService::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    private fun notification(bookmarks: Int): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        val base = if (startedAtElapsed > 0L) {
            System.currentTimeMillis() - (SystemClock.elapsedRealtime() - startedAtElapsed)
        } else {
            System.currentTimeMillis()
        }
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("● Nahrávám")
            .setContentText("Záložky: $bookmarks · podrž tlačítko zapínání pro další")
            .setWhen(base)
            .setUsesChronometer(true)
            .setShowWhen(true)
            .setOngoing(true)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setContentIntent(open)
            .setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
            .addAction(Notification.Action.Builder(null, "🔖 Záložka", action(ACTION_BOOKMARK, 1)).build())
            .addAction(Notification.Action.Builder(null, "Zpět", action(ACTION_UNDO, 2)).build())
            .addAction(Notification.Action.Builder(null, "■ Stop", action(ACTION_STOP, 3)).build())
            .build()
    }
}
