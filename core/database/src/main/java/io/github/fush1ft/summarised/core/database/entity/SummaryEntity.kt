package io.github.fush1ft.summarised.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * The generated summary of a recording. One per recording; regenerating replaces it.
 *
 * Key points, decisions and action items are stored as JSON arrays rather than
 * separate tables: they are only ever read and written as a whole block alongside
 * the summary, so normalising them would buy nothing.
 */
@Entity(
    tableName = "summaries",
    foreignKeys = [
        ForeignKey(
            entity = RecordingEntity::class,
            parentColumns = ["id"],
            childColumns = ["recordingId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["recordingId"], unique = true)],
)
data class SummaryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val recordingId: Long,
    val tldr: String,
    val keyPointsJson: String,
    val decisionsJson: String,
    val actionItemsJson: String,
    val generatedAt: Long,
    /** Which engine produced this, so a cloud-generated summary is identifiable. */
    val generatedBy: String,
)
