package io.github.fush1ft.summarised.capture

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.IBinder
import android.os.SystemClock
import io.github.fush1ft.summarised.MainActivity
import io.github.fush1ft.summarised.core.database.AudioChunkEntity
import io.github.fush1ft.summarised.core.database.RecordingEntity
import io.github.fush1ft.summarised.core.database.RecordingStatus
import io.github.fush1ft.summarised.core.database.SummarisedDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Microphone foreground service. Records 16 kHz mono into ~5-minute audio
 * chunks, keeps the database current every 30 s, and watches for audio the
 * system has silenced (Android gives a blocked app zeros, not an error).
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
        private const val CHUNK_MS = 5 * 60_000L
        private const val CHECKPOINT_MS = 30_000L

        fun audioDir(context: Context): File = File(context.getExternalFilesDir(null), "recordings").apply { mkdirs() }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    @Volatile private var running = false
    private var thread: Thread? = null
    private var startedAtElapsed = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> start(intent.getStringExtra("source") ?: "app")
            ACTION_STOP -> stop()
            ACTION_BOOKMARK -> if (CaptureController.addBookmark(this, "notifikace") is CaptureController.Outcome.Bookmarked) {
                Haptics.play(this, Haptics.Pattern.BOOKMARK)
            }
            ACTION_UNDO -> {
                CaptureController.undoLastBookmark(this)
                Haptics.play(this, Haptics.Pattern.UNDO)
            }
        }
        return START_NOT_STICKY
    }

    private fun start(source: String) {
        if (running) return
        ensureChannel()
        try {
            startForeground(NOTIFICATION_ID, notification(0), ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } catch (e: Exception) {
            CaptureController.fail("startForeground: ${e.javaClass.simpleName}: ${e.message}")
            Haptics.play(this, Haptics.Pattern.ERROR)
            stopSelf()
            return
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            CaptureController.fail("Chybí oprávnění k mikrofonu")
            Haptics.play(this, Haptics.Pattern.ERROR)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }
        running = true
        thread = Thread({ captureLoop(source) }, "summarised-capture").also { it.start() }
        scope.launch {
            CaptureController.state.map { it.bookmarks.size }.distinctUntilChanged().collect { n ->
                if (running) getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification(n))
            }
        }
    }

    @SuppressLint("MissingPermission") // checked in start()
    private fun captureLoop(source: String) {
        val db = SummarisedDatabase.get(this)
        val minBuf = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val record = try {
            AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION, SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(minBuf * 4, SAMPLE_RATE),
            )
        } catch (e: Exception) {
            failFromThread("Mikrofon nejde otevřít: ${e.message}")
            return
        }
        if (record.state != AudioRecord.STATE_INITIALIZED) {
            record.release()
            failFromThread("Mikrofon nejde otevřít (používá ho jiná aplikace?)")
            return
        }
        record.startRecording()
        if (record.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
            record.release()
            failFromThread("Mikrofon nezačal nahrávat (používá ho jiná aplikace?)")
            return
        }

        val title = "Nahrávka " + SimpleDateFormat("d. M. HH:mm", Locale.forLanguageTag("cs")).format(Date())
        val recordingId = runBlocking {
            db.recordingDao().insert(RecordingEntity(title = title, createdAt = System.currentTimeMillis(), startedVia = source))
        }
        startedAtElapsed = SystemClock.elapsedRealtime()
        CaptureController.recordingStarted(recordingId, startedAtElapsed)
        scope.launch { Haptics.play(this@RecordingService, Haptics.Pattern.START) }

        val dir = audioDir(this)
        var index = 0
        var chunkStartMs = 0L
        var writer = openChunkWriter(dir, recordingId, index)
        var chunkId = runBlocking {
            db.audioChunkDao().insert(AudioChunkEntity(recordingId = recordingId, index = 0, path = writer.file.path, startMs = 0, durationMs = 0, format = writer.format))
        }
        val buf = ShortArray(SAMPLE_RATE / 10)
        var lastCheckpoint = 0L
        var windowSamples = 0
        var peak = 0
        var lastSilenced = false
        try {
            while (running) {
                val n = record.read(buf, 0, buf.size)
                if (n <= 0) {
                    if (n == AudioRecord.ERROR_DEAD_OBJECT) break
                    continue
                }
                writer.write(buf, n)
                for (i in 0 until n) peak = maxOf(peak, kotlin.math.abs(buf[i].toInt()))
                windowSamples += n
                if (windowSamples >= SAMPLE_RATE * 5) {
                    val silenced = peak == 0 || record.activeRecordingConfiguration?.isClientSilenced == true
                    if (silenced != lastSilenced) CaptureController.setSilenced(silenced)
                    lastSilenced = silenced
                    windowSamples = 0
                    peak = 0
                }
                val total = chunkStartMs + writer.durationMs()
                if (writer.durationMs() >= CHUNK_MS) {
                    writer.close()
                    val done = writer.durationMs()
                    runBlocking { db.audioChunkDao().updateDuration(chunkId, done) }
                    chunkStartMs += done
                    index++
                    writer = openChunkWriter(dir, recordingId, index)
                    val newChunk = AudioChunkEntity(recordingId = recordingId, index = index, path = writer.file.path, startMs = chunkStartMs, durationMs = 0, format = writer.format)
                    chunkId = runBlocking { db.audioChunkDao().insert(newChunk) }
                }
                if (total - lastCheckpoint >= CHECKPOINT_MS) {
                    lastCheckpoint = total
                    val chunkMs = writer.durationMs()
                    runBlocking {
                        db.recordingDao().updateDuration(recordingId, total)
                        db.audioChunkDao().updateDuration(chunkId, chunkMs)
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("summarised", "capture loop failed", e)
        } finally {
            runCatching { record.stop() }
            record.release()
            runCatching { writer.close() }
            val chunkMs = writer.durationMs()
            val total = chunkStartMs + chunkMs
            runBlocking {
                db.audioChunkDao().updateDuration(chunkId, chunkMs)
                db.recordingDao().finish(recordingId, total, RecordingStatus.READY)
            }
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

    private fun stop() {
        if (!running) {
            stopSelf()
            return
        }
        running = false
        thread?.join(4000)
        CaptureController.recordingStopped()
        Haptics.play(this, Haptics.Pattern.STOP)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        if (running) {
            running = false
            thread?.join(4000)
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
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val base = if (startedAtElapsed > 0) {
            System.currentTimeMillis() - (SystemClock.elapsedRealtime() - startedAtElapsed)
        } else {
            System.currentTimeMillis()
        }
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(io.github.fush1ft.summarised.R.drawable.ic_launcher_foreground)
            .setContentTitle("● Nahrávám")
            .setContentText("Záložky: $bookmarks · podrž vypínač pro další")
            .setWhen(base)
            .setUsesChronometer(true)
            .setShowWhen(true)
            .setOngoing(true)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setContentIntent(open)
            .setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
            .addAction(Notification.Action.Builder(null, "Záložka", action(ACTION_BOOKMARK, 1)).build())
            .addAction(Notification.Action.Builder(null, "Zpět", action(ACTION_UNDO, 2)).build())
            .addAction(Notification.Action.Builder(null, "Stop", action(ACTION_STOP, 3)).build())
            .build()
    }
}
