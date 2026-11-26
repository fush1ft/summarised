package io.github.fush1ft.summarised.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface RecordingDao {
    @Insert
    suspend fun insert(recording: RecordingEntity): Long

    @Query("SELECT * FROM recordings ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<RecordingEntity>>

    @Query("SELECT * FROM recordings WHERE id = :id")
    fun observe(id: Long): Flow<RecordingEntity?>

    @Query("SELECT * FROM recordings WHERE status = :status")
    suspend fun withStatus(status: RecordingStatus): List<RecordingEntity>

    @Query("UPDATE recordings SET durationMs = :durationMs, status = :status WHERE id = :id")
    suspend fun finish(id: Long, durationMs: Long, status: RecordingStatus)

    @Query("UPDATE recordings SET durationMs = :durationMs WHERE id = :id")
    suspend fun updateDuration(id: Long, durationMs: Long)

    @Query("UPDATE recordings SET transcribedUntilMs = :untilMs WHERE id = :id")
    suspend fun setTranscribedUntil(id: Long, untilMs: Long)

    @Query("SELECT * FROM recordings WHERE id = :id")
    suspend fun get(id: Long): RecordingEntity?

    @Query("UPDATE recordings SET title = :title WHERE id = :id")
    suspend fun rename(id: Long, title: String)

    @Query("DELETE FROM recordings WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface AudioChunkDao {
    @Insert
    suspend fun insert(chunk: AudioChunkEntity): Long

    @Query("UPDATE audio_chunks SET durationMs = :durationMs WHERE id = :id")
    suspend fun updateDuration(id: Long, durationMs: Long)

    @Query("SELECT * FROM audio_chunks WHERE recordingId = :recordingId ORDER BY `index`")
    suspend fun forRecording(recordingId: Long): List<AudioChunkEntity>

    @Query("SELECT * FROM audio_chunks WHERE recordingId = :recordingId ORDER BY `index`")
    fun observe(recordingId: Long): Flow<List<AudioChunkEntity>>
}

@Dao
interface BookmarkDao {
    @Insert
    suspend fun insert(bookmark: BookmarkEntity): Long

    @Query("DELETE FROM bookmarks WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM bookmarks WHERE recordingId = :recordingId ORDER BY atMs")
    fun observe(recordingId: Long): Flow<List<BookmarkEntity>>

    @Query("SELECT recordingId, COUNT(*) AS count FROM bookmarks GROUP BY recordingId")
    fun observeCounts(): Flow<List<BookmarkCount>>
}

data class BookmarkCount(val recordingId: Long, val count: Int)

@Dao
interface SegmentDao {
    @Insert
    suspend fun insert(segment: SegmentEntity): Long

    @Insert
    suspend fun insertAll(segments: List<SegmentEntity>)

    @Query("SELECT * FROM segments WHERE recordingId = :recordingId ORDER BY startMs")
    fun observe(recordingId: Long): Flow<List<SegmentEntity>>

    @Query("SELECT * FROM segments WHERE recordingId = :recordingId ORDER BY startMs DESC LIMIT 1")
    fun observeLast(recordingId: Long): Flow<SegmentEntity?>

    @Query("DELETE FROM segments WHERE recordingId = :recordingId")
    suspend fun deleteFor(recordingId: Long)
}
