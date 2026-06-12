package com.notifyai.data.repository

import com.notifyai.data.local.db.NotificationDao
import com.notifyai.data.local.db.SummaryDao
import com.notifyai.data.local.entity.NotificationEntity
import com.notifyai.data.local.entity.SummaryEntity
import com.notifyai.domain.repository.NotificationRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationRepositoryImpl @Inject constructor(
    private val notificationDao: NotificationDao,
    private val summaryDao: SummaryDao
) : NotificationRepository {

    override suspend fun insertNotification(entity: NotificationEntity): Long =
        notificationDao.insert(entity)

    override fun observeNotifications(): Flow<List<NotificationEntity>> =
        notificationDao.observeAll()

    override fun observeRecentNotifications(limit: Int): Flow<List<NotificationEntity>> =
        notificationDao.observeRecent(limit)

    override suspend fun getNotificationsByTimeRange(from: Long, to: Long): List<NotificationEntity> =
        notificationDao.getByTimeRangeSync(from, to)

    override fun observeFilteredNotifications(
        from: Long,
        to: Long,
        packageName: String?,
        category: String?
    ): Flow<List<NotificationEntity>> =
        notificationDao.observeFiltered(from, to, packageName, category)

    override suspend fun getUnprocessedNotifications(): List<NotificationEntity> =
        notificationDao.getUnprocessed()

    override suspend fun markProcessed(ids: List<Long>) =
        notificationDao.markProcessed(ids)

    override suspend fun deleteOldNotifications(before: Long) =
        notificationDao.deleteOlderThan(before)

    override fun searchNotifications(query: String): Flow<List<NotificationEntity>> =
        notificationDao.searchNotifications(query)

    override suspend fun getDistinctApps(): List<String> =
        notificationDao.getDistinctApps()

    override suspend fun storeSummary(entity: SummaryEntity): Long =
        summaryDao.insert(entity)

    override fun observeSummaries(): Flow<List<SummaryEntity>> =
        summaryDao.observeAll()

    override fun observeLatestSummary(): Flow<SummaryEntity?> =
        summaryDao.observeLatest()

    override suspend fun deleteOldSummaries(before: Long) =
        summaryDao.deleteOlderThan(before)
}
