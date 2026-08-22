package io.github.fush1ft.summarised.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import io.github.fush1ft.summarised.core.database.entity.SegmentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SegmentDao {
    @Query("SELECT * FROM segments WHERE recordingId = :recordingId ORDER BY startMs ASC")
    fun observeForRecording(recordingId: Long): Flow<List<SegmentEntity>>

    @Query("SELECT * FROM segments WHERE recordingId = :recordingId ORDER BY startMs ASC")
    suspend fun getForRecording(recordingId: Long): List<SegmentEntity>

    /** Segments still awaiting ASR, so transcription can resume mid-recording. */
    @Query(
        "SELECT * FROM segments WHERE recordingId = :recordingId AND text IS NULL " +
            "ORDER BY startMs ASC",
    )
    suspend fun getUntranscribed(recordingId: Long): List<SegmentEntity>

    @Insert
    suspend fun insertAll(segments: List<SegmentEntity>): List<Long>

    @Query("UPDATE segments SET text = :text WHERE id = :id")
    suspend fun updateText(id: Long, text: String)

    @Query("DELETE FROM segments WHERE recordingId = :recordingId")
    suspend fun deleteForRecording(recordingId: Long)
}
