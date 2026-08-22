package io.github.fush1ft.summarised.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import io.github.fush1ft.summarised.core.database.entity.SummaryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SummaryDao {
    @Query("SELECT * FROM summaries WHERE recordingId = :recordingId")
    fun observeForRecording(recordingId: Long): Flow<SummaryEntity?>

    @Upsert
    suspend fun upsert(summary: SummaryEntity)

    @Query("DELETE FROM summaries WHERE recordingId = :recordingId")
    suspend fun deleteForRecording(recordingId: Long)
}
