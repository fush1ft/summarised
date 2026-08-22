package io.github.fush1ft.summarised.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One speaker turn with its transcribed text.
 *
 * Segments are created by diarization (with [text] still null) and filled in by
 * ASR, so a partially transcribed recording is a valid, displayable state.
 */
@Entity(
    tableName = "segments",
    foreignKeys = [
        ForeignKey(
            entity = RecordingEntity::class,
            parentColumns = ["id"],
            childColumns = ["recordingId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("recordingId"), Index("recordingId", "startMs")],
)
data class SegmentEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val recordingId: Long,
    val startMs: Long,
    val endMs: Long,
    val speakerIndex: Int,
    val text: String? = null,
)
