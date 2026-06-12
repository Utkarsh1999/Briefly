package com.notifyai.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.notifyai.data.local.entity.NotificationEntity
import com.notifyai.data.local.entity.SummaryEntity

@Database(
    entities = [NotificationEntity::class, SummaryEntity::class],
    version = 1,
    exportSchema = true
)
abstract class NotifyAIDatabase : RoomDatabase() {
    abstract fun notificationDao(): NotificationDao
    abstract fun summaryDao(): SummaryDao

    companion object {
        const val DATABASE_NAME = "notifyai_db"
    }
}
