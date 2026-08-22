package io.github.fush1ft.summarised.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Stages a recording moves through after capture ends. The pipeline is sequential
 * and each stage writes its results before the next begins, so a process death
 * resumes from the last completed stage rather than restarting from the audio file.
 */
enum class RecordingStatus {
    RECORDING,
    QUEUED,
    DIARIZING,
    TRANSCRIBING,
    SUMMARISING,
    READY,
    FAILED,
}

@Entity(tableName = "recordings")
data class RecordingEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    /** Absolute path to the WAV file in app-private storage. */
    val filePath: String,
    val durationMs: Long,
    val createdAt: Long,
    val status: RecordingStatus,
    /** BCP-47 tag detected by the ASR model, null until transcription runs. */
    val language: String? = null,
    /** Which ASR/LLM models produced the current results, for reproducibility. */
    val asrModel: String? = null,
    val llmModel: String? = null,
    /** Set when status is FAILED, so the UI can explain what went wrong. */
    val errorMessage: String? = null,
)
