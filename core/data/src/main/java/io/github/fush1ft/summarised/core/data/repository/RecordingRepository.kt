package io.github.fush1ft.summarised.core.data.repository

import io.github.fush1ft.summarised.core.data.model.Recording
import io.github.fush1ft.summarised.core.data.model.toDomain
import io.github.fush1ft.summarised.core.data.di.IoDispatcher
import io.github.fush1ft.summarised.core.database.dao.RecordingDao
import io.github.fush1ft.summarised.core.database.entity.RecordingEntity
import io.github.fush1ft.summarised.core.database.entity.RecordingStatus
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RecordingRepository @Inject constructor(
    private val recordingDao: RecordingDao,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {
    fun observeRecordings(): Flow<List<Recording>> =
        recordingDao.observeAll().map { entities -> entities.map { it.toDomain() } }

    fun observeRecording(id: Long): Flow<Recording?> =
        recordingDao.observeById(id).map { it?.toDomain() }

    /**
     * Stores a finished recording and returns its id.
     *
     * The status is QUEUED rather than READY: the audio exists, but the transcript
     * and summary stages have not run. Once those land this row is what they pick up.
     */
    suspend fun saveRecording(file: File, durationMs: Long): Long = withContext(ioDispatcher) {
        recordingDao.insert(
            RecordingEntity(
                title = defaultTitle(),
                filePath = file.absolutePath,
                durationMs = durationMs,
                createdAt = System.currentTimeMillis(),
                status = RecordingStatus.QUEUED,
            ),
        )
    }

    suspend fun rename(id: Long, title: String) = withContext(ioDispatcher) {
        recordingDao.rename(id, title.trim().ifEmpty { defaultTitle() })
    }

    /**
     * Deletes a recording and its audio file. Segments, speakers, summaries and chat
     * go with it through the schema's cascading foreign keys.
     */
    suspend fun delete(id: Long) = withContext(ioDispatcher) {
        val recording = recordingDao.getById(id)
        recordingDao.delete(id)
        recording?.let { File(it.filePath).delete() }
    }

    private fun defaultTitle(): String =
        "Recording ${SimpleDateFormat("d MMM, HH:mm", Locale.getDefault()).format(Date())}"
}
