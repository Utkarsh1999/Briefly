package com.notifyai.di

import android.content.Context
import androidx.room.Room
import com.notifyai.data.local.db.NotificationDao
import com.notifyai.data.local.db.NotifyAIDatabase
import com.notifyai.data.local.db.SummaryDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): NotifyAIDatabase {
        return Room.databaseBuilder(
            context,
            NotifyAIDatabase::class.java,
            NotifyAIDatabase.DATABASE_NAME
        )
        .fallbackToDestructiveMigration()
        .build()
    }

    @Provides
    fun provideNotificationDao(database: NotifyAIDatabase): NotificationDao {
        return database.notificationDao()
    }

    @Provides
    fun provideSummaryDao(database: NotifyAIDatabase): SummaryDao {
        return database.summaryDao()
    }
}
