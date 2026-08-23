package io.github.fush1ft.summarised.core.data.model

import io.github.fush1ft.summarised.core.database.entity.RecordingEntity
import io.github.fush1ft.summarised.core.database.entity.RecordingStatus

/**
 * A recording as the UI cares about it.
 *
 * Kept separate from [RecordingEntity] so the storage schema can change — and it
 * will, as the transcription and summary stages land — without every screen
 * needing to follow.
 */
data class Recording(
    val id: Long,
    val title: String,
    val filePath: String,
    val durationMs: Long,
    val createdAt: Long,
    val status: RecordingStatus,
    val errorMessage: String? = null,
)

internal fun RecordingEntity.toDomain(): Recording = Recording(
    id = id,
    title = title,
    filePath = filePath,
    durationMs = durationMs,
    createdAt = createdAt,
    status = status,
    errorMessage = errorMessage,
)
