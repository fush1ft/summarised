package io.github.fush1ft.summarised.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import io.github.fush1ft.summarised.core.database.entity.RecordingEntity
import io.github.fush1ft.summarised.core.database.entity.RecordingStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface RecordingDao {
    @Query("SELECT * FROM recordings ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<RecordingEntity>>

    @Query("SELECT * FROM recordings WHERE id = :id")
    fun observeById(id: Long): Flow<RecordingEntity?>

    @Query("SELECT * FROM recordings WHERE id = :id")
    suspend fun getById(id: Long): RecordingEntity?

    /**
     * Recordings whose processing did not finish. Used at startup to re-enqueue work
     * that was interrupted by a process death or reboot.
     */
    @Query("SELECT * FROM recordings WHERE status NOT IN ('READY', 'FAILED', 'RECORDING')")
    suspend fun getUnfinished(): List<RecordingEntity>

    @Insert
    suspend fun insert(recording: RecordingEntity): Long

    @Update
    suspend fun update(recording: RecordingEntity)

    @Query("UPDATE recordings SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: Long, status: RecordingStatus)

    @Query("UPDATE recordings SET status = 'FAILED', errorMessage = :message WHERE id = :id")
    suspend fun markFailed(id: Long, message: String)

    @Query("UPDATE recordings SET title = :title WHERE id = :id")
    suspend fun rename(id: Long, title: String)

    @Query("DELETE FROM recordings WHERE id = :id")
    suspend fun delete(id: Long)
}
