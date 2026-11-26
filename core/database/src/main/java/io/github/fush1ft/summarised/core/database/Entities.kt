package io.github.fush1ft.summarised.core.database

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

enum class RecordingStatus { RECORDING, READY, INTERRUPTED }

/** One recording session, from the first power-button hold to Stop. */
@Entity(tableName = "recordings")
data class RecordingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val createdAt: Long,
    val durationMs: Long = 0,
    val status: RecordingStatus = RecordingStatus.RECORDING,
    /** How it was started: power-hold, app, tile, notification. */
    val startedVia: String = "app",
    /** Audio up to here has been transcribed (live or afterwards). */
    val transcribedUntilMs: Long = 0,
    /** Summary template: LECTURE, MEETING or FIELD. */
    val template: String = "LECTURE",
    /** Whole-recording summary as JSON, and which model wrote it. */
    val summaryJson: String? = null,
    val summarisedBy: String? = null,
)

/** Audio is written in ~5-minute files, so a crash loses at most one chunk. */
@Entity(
    tableName = "audio_chunks",
    foreignKeys = [ForeignKey(RecordingEntity::class, ["id"], ["recordingId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("recordingId")],
)
data class AudioChunkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val recordingId: Long,
    val index: Int,
    val path: String,
    /** Offset of this chunk from the start of the recording. */
    val startMs: Long,
    val durationMs: Long,
    /** "opus" or "wav". */
    val format: String,
)

@Entity(
    tableName = "bookmarks",
    foreignKeys = [ForeignKey(RecordingEntity::class, ["id"], ["recordingId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("recordingId")],
)
data class BookmarkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val recordingId: Long,
    /** Position in the recording, already moved back by the hold's reaction time. */
    val atMs: Long,
    val createdVia: String,
    val createdAt: Long,
    /** Filled in by the summariser (M3). */
    val title: String? = null,
    val summaryJson: String? = null,
    val summarisedBy: String? = null,
)

/** Transcript pieces (M2). */
@Entity(
    tableName = "segments",
    foreignKeys = [ForeignKey(RecordingEntity::class, ["id"], ["recordingId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("recordingId")],
)
data class SegmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val recordingId: Long,
    val startMs: Long,
    val endMs: Long,
    val text: String,
)
