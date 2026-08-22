package io.github.fush1ft.summarised.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import io.github.fush1ft.summarised.core.database.entity.SpeakerEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SpeakerDao {
    @Query("SELECT * FROM speakers WHERE recordingId = :recordingId ORDER BY speakerIndex ASC")
    fun observeForRecording(recordingId: Long): Flow<List<SpeakerEntity>>

    @Insert
    suspend fun insertAll(speakers: List<SpeakerEntity>): List<Long>

    @Query(
        "UPDATE speakers SET displayName = :displayName " +
            "WHERE recordingId = :recordingId AND speakerIndex = :speakerIndex",
    )
    suspend fun rename(recordingId: Long, speakerIndex: Int, displayName: String?)

    @Query("DELETE FROM speakers WHERE recordingId = :recordingId")
    suspend fun deleteForRecording(recordingId: Long)
}
