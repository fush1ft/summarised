package io.github.fush1ft.summarised

import android.app.Application
import io.github.fush1ft.summarised.capture.CaptureController
import io.github.fush1ft.summarised.capture.WavChunkWriter
import io.github.fush1ft.summarised.core.database.RecordingStatus
import io.github.fush1ft.summarised.core.database.SummarisedDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File

class SummarisedApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        recoverInterruptedRecordings()
        // Pick up summaries left unfinished by a previous run.
        io.github.fush1ft.summarised.summarise.SummaryManager.kick(this)
    }

    /**
     * A fresh process can't be recording, so any recording still marked as
     * running was cut off (crash, killed app, dead battery). Keep everything up
     * to the last checkpoint and make the audio playable.
     */
    private fun recoverInterruptedRecordings() {
        if (CaptureController.state.value.phase == CaptureController.Phase.RECORDING) return
        val db = SummarisedDatabase.get(this)
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            for (r in db.recordingDao().withStatus(RecordingStatus.RECORDING)) {
                var total = 0L
                for (chunk in db.audioChunkDao().forRecording(r.id)) {
                    val duration = if (chunk.format == "wav") WavChunkWriter.repairHeader(File(chunk.path)) else chunk.durationMs
                    if (duration != chunk.durationMs) db.audioChunkDao().updateDuration(chunk.id, duration)
                    total += duration
                }
                db.recordingDao().finish(r.id, maxOf(total, r.durationMs), RecordingStatus.INTERRUPTED)
            }
        }
    }
}
