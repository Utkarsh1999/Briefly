package com.notifyai.domain.repository

import com.notifyai.data.local.entity.NotificationEntity
import com.notifyai.data.local.entity.SummaryEntity
import kotlinx.coroutines.flow.Flow

interface NotificationRepository {

    // --- Notifications ---
    suspend fun insertNotification(entity: NotificationEntity): Long

    fun observeNotifications(): Flow<List<NotificationEntity>>

    fun observeRecentNotifications(limit: Int = 100): Flow<List<NotificationEntity>>

    suspend fun getNotificationsByTimeRange(from: Long, to: Long): List<NotificationEntity>

    fun observeFilteredNotifications(
        from: Long,
        to: Long,
        packageName: String? = null,
        category: String? = null
    ): Flow<List<NotificationEntity>>

    suspend fun getUnprocessedNotifications(): List<NotificationEntity>

    suspend fun markProcessed(ids: List<Long>)

    suspend fun deleteOldNotifications(before: Long)

    fun searchNotifications(query: String): Flow<List<NotificationEntity>>

    suspend fun getDistinctApps(): List<String>

    // --- Summaries ---
    suspend fun storeSummary(entity: SummaryEntity): Long

    fun observeSummaries(): Flow<List<SummaryEntity>>

    fun observeLatestSummary(): Flow<SummaryEntity?>

    suspend fun deleteOldSummaries(before: Long)
}
