package io.github.fush1ft.summarised.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import io.github.fush1ft.summarised.core.database.dao.ChatMessageDao
import io.github.fush1ft.summarised.core.database.dao.RecordingDao
import io.github.fush1ft.summarised.core.database.dao.SegmentDao
import io.github.fush1ft.summarised.core.database.dao.SpeakerDao
import io.github.fush1ft.summarised.core.database.dao.SummaryDao
import io.github.fush1ft.summarised.core.database.entity.ChatMessageEntity
import io.github.fush1ft.summarised.core.database.entity.RecordingEntity
import io.github.fush1ft.summarised.core.database.entity.SegmentEntity
import io.github.fush1ft.summarised.core.database.entity.SpeakerEntity
import io.github.fush1ft.summarised.core.database.entity.SummaryEntity

@Database(
    entities = [
        RecordingEntity::class,
        SegmentEntity::class,
        SpeakerEntity::class,
        SummaryEntity::class,
        ChatMessageEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class SummarisedDatabase : RoomDatabase() {
    abstract fun recordingDao(): RecordingDao
    abstract fun segmentDao(): SegmentDao
    abstract fun speakerDao(): SpeakerDao
    abstract fun summaryDao(): SummaryDao
    abstract fun chatMessageDao(): ChatMessageDao

    companion object {
        const val NAME = "summarised.db"
    }
}
