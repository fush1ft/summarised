package io.github.fush1ft.summarised.core.database.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.fush1ft.summarised.core.database.SummarisedDatabase
import io.github.fush1ft.summarised.core.database.dao.ChatMessageDao
import io.github.fush1ft.summarised.core.database.dao.RecordingDao
import io.github.fush1ft.summarised.core.database.dao.SegmentDao
import io.github.fush1ft.summarised.core.database.dao.SpeakerDao
import io.github.fush1ft.summarised.core.database.dao.SummaryDao
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context,
    ): SummarisedDatabase = Room.databaseBuilder(
        context,
        SummarisedDatabase::class.java,
        SummarisedDatabase.NAME,
    ).build()

    @Provides
    fun provideRecordingDao(db: SummarisedDatabase): RecordingDao = db.recordingDao()

    @Provides
    fun provideSegmentDao(db: SummarisedDatabase): SegmentDao = db.segmentDao()

    @Provides
    fun provideSpeakerDao(db: SummarisedDatabase): SpeakerDao = db.speakerDao()

    @Provides
    fun provideSummaryDao(db: SummarisedDatabase): SummaryDao = db.summaryDao()

    @Provides
    fun provideChatMessageDao(db: SummarisedDatabase): ChatMessageDao = db.chatMessageDao()
}
