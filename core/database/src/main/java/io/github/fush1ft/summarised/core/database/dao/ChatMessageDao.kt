package io.github.fush1ft.summarised.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import io.github.fush1ft.summarised.core.database.entity.ChatMessageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatMessageDao {
    @Query("SELECT * FROM chat_messages WHERE recordingId = :recordingId ORDER BY createdAt ASC")
    fun observeForRecording(recordingId: Long): Flow<List<ChatMessageEntity>>

    @Query("SELECT * FROM chat_messages WHERE recordingId = :recordingId ORDER BY createdAt ASC")
    suspend fun getForRecording(recordingId: Long): List<ChatMessageEntity>

    @Insert
    suspend fun insert(message: ChatMessageEntity): Long

    @Query("UPDATE chat_messages SET content = :content WHERE id = :id")
    suspend fun updateContent(id: Long, content: String)

    @Query("DELETE FROM chat_messages WHERE recordingId = :recordingId")
    suspend fun deleteForRecording(recordingId: Long)
}
