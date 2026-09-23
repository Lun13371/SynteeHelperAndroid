package ru.syntee.helper.core.database.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import ru.syntee.helper.core.database.AppDatabase
import ru.syntee.helper.core.database.dao.ChatDao
import ru.syntee.helper.core.database.dao.MessageDao
import ru.syntee.helper.core.database.dao.StreamingBufferDao
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "syntee_helper.db")
            .build()

    @Provides
    fun provideChatDao(db: AppDatabase): ChatDao = db.chatDao()

    @Provides
    fun provideMessageDao(db: AppDatabase): MessageDao = db.messageDao()

    @Provides
    fun provideStreamingBufferDao(db: AppDatabase): StreamingBufferDao = db.streamingBufferDao()
}
