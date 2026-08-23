package io.github.fush1ft.summarised.core.audio.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.getSystemService
import dagger.hilt.android.AndroidEntryPoint
import io.github.fush1ft.summarised.core.audio.R
import io.github.fush1ft.summarised.core.audio.RecorderController
import io.github.fush1ft.summarised.core.audio.RecorderStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Keeps the process alive and shows the recording notification.
 *
 * Since Android 9 an app cannot hold the microphone from the background without a
 * foreground service, so this exists purely to satisfy that and to give the user a
 * persistent, non-dismissible indication that they are being recorded. The capture
 * itself lives in [RecorderController], which outlives this service.
 */
@AndroidEntryPoint
class RecordingService : Service() {

    @Inject lateinit var recorder: RecorderController

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var observeJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PAUSE -> recorder.pause()
            ACTION_RESUME -> recorder.resume()
        }

        startForegroundWithStatus(recorder.state.value.status)
        observeState()

        // The recording should be restored by the app, not silently resurrected by
        // the system with no audio session behind it.
        return START_NOT_STICKY
    }

    private fun observeState() {
        if (observeJob != null) return
        observeJob = scope.launch {
            recorder.state
                .map { it.status }
                .distinctUntilChanged()
                .collect { status ->
                    if (status == RecorderStatus.IDLE) {
                        stopSelf()
                    } else {
                        notificationManager?.notify(NOTIFICATION_ID, buildNotification(status))
                    }
                }
        }
    }

    private fun startForegroundWithStatus(status: RecorderStatus) {
        val notification = buildNotification(status)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(status: RecorderStatus): Notification {
        val paused = status == RecorderStatus.PAUSED

        val contentIntent = packageManager
            .getLaunchIntentForPackage(packageName)
            ?.let { launch ->
                PendingIntent.getActivity(
                    this,
                    0,
                    launch,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )
            }

        val toggleAction = if (paused) {
            NotificationCompat.Action(
                0,
                getString(R.string.recording_action_resume),
                servicePendingIntent(ACTION_RESUME),
            )
        } else {
            NotificationCompat.Action(
                0,
                getString(R.string.recording_action_pause),
                servicePendingIntent(ACTION_PAUSE),
            )
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(
                getString(
                    if (paused) R.string.recording_paused else R.string.recording_in_progress,
                ),
            )
            .setContentText(getString(R.string.recording_notification_text))
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(contentIntent)
            .addAction(toggleAction)
            .setOngoing(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
    }

    private fun servicePendingIntent(action: String): PendingIntent {
        val intent = Intent(this, RecordingService::class.java).setAction(action)
        return PendingIntent.getService(
            this,
            action.hashCode(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.recording_channel_name),
            // Low importance: the notification must be present and persistent, but it
            // should never make a sound in the middle of the meeting being recorded.
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.recording_channel_description)
            setShowBadge(false)
        }
        notificationManager?.createNotificationChannel(channel)
    }

    private val notificationManager: NotificationManager?
        get() = getSystemService()

    override fun onDestroy() {
        observeJob?.cancel()
        observeJob = null
        scope.coroutineContext[Job]?.cancel()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_ID = "recording"
        private const val NOTIFICATION_ID = 1

        const val ACTION_PAUSE = "io.github.fush1ft.summarised.PAUSE"
        const val ACTION_RESUME = "io.github.fush1ft.summarised.RESUME"

        fun start(context: Context) {
            val intent = Intent(context, RecordingService::class.java)
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, RecordingService::class.java))
        }
    }
}
