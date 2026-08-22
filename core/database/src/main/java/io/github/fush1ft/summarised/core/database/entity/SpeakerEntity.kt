package io.github.fush1ft.summarised.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A speaker detected by diarization within one recording.
 *
 * Speakers are per-recording: diarization clusters voices inside a single file and
 * has no notion of identity across files, so "Speaker 1" in two recordings is not
 * claimed to be the same person.
 */
@Entity(
    tableName = "speakers",
    foreignKeys = [
        ForeignKey(
            entity = RecordingEntity::class,
            parentColumns = ["id"],
            childColumns = ["recordingId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("recordingId")],
)
data class SpeakerEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val recordingId: Long,
    /** Cluster index from diarization, stable within a recording. */
    val speakerIndex: Int,
    /** User-assigned name; when null the UI falls back to "Speaker N". */
    val displayName: String? = null,
    val colorIndex: Int,
)
