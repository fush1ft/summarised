package io.github.fush1ft.summarised.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [RecordingEntity::class, AudioChunkEntity::class, BookmarkEntity::class, SegmentEntity::class],
    version = 4,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class SummarisedDatabase : RoomDatabase() {
    abstract fun recordingDao(): RecordingDao
    abstract fun audioChunkDao(): AudioChunkDao
    abstract fun bookmarkDao(): BookmarkDao
    abstract fun segmentDao(): SegmentDao

    companion object {
        private const val NAME = "summarised.db"

        @Volatile private var instance: SummarisedDatabase? = null

        fun get(context: Context): SummarisedDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(context.applicationContext, SummarisedDatabase::class.java, NAME)
                    // Version 1 only ever shipped in pre-alpha builds of the old plan.
                    .fallbackToDestructiveMigrationFrom(true, 1)
                    .addMigrations(MIGRATION_2_3, MIGRATION_3_4)
                    .build()
                    .also { instance = it }
            }
    }
}

/** M2: transcription progress per recording. */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE recordings ADD COLUMN transcribedUntilMs INTEGER NOT NULL DEFAULT 0")
    }
}

/** M3/M4: summary template and the whole-recording summary. */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE recordings ADD COLUMN template TEXT NOT NULL DEFAULT 'LECTURE'")
        db.execSQL("ALTER TABLE recordings ADD COLUMN summaryJson TEXT")
        db.execSQL("ALTER TABLE recordings ADD COLUMN summarisedBy TEXT")
    }
}

/** Enums are stored by name, so reordering them never reinterprets old rows. */
class Converters {
    @TypeConverter
    fun toStatus(value: String): RecordingStatus = RecordingStatus.valueOf(value)

    @TypeConverter
    fun fromStatus(status: RecordingStatus): String = status.name
}
