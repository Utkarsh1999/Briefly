package com.notifyai.domain.usecase

import com.notifyai.data.local.entity.NotificationEntity
import com.notifyai.domain.repository.NotificationRepository
import javax.inject.Inject

class InsertNotificationUseCase @Inject constructor(
    private val repository: NotificationRepository
) {
    suspend operator fun invoke(entity: NotificationEntity): Long =
        repository.insertNotification(entity)
}

class GetNotificationsUseCase @Inject constructor(
    private val repository: NotificationRepository
) {
    fun observeAll() = repository.observeNotifications()
    fun observeRecent(limit: Int = 100) = repository.observeRecentNotifications(limit)
    fun search(query: String) = repository.searchNotifications(query)
    fun observeFiltered(from: Long, to: Long, pkg: String? = null, category: String? = null) =
        repository.observeFilteredNotifications(from, to, pkg, category)
    suspend fun getDistinctApps() = repository.getDistinctApps()
}

class MarkNotificationsProcessedUseCase @Inject constructor(
    private val repository: NotificationRepository
) {
    suspend operator fun invoke(ids: List<Long>) = repository.markProcessed(ids)
}

class CleanupOldDataUseCase @Inject constructor(
    private val repository: NotificationRepository
) {
    /** Deletes notifications and summaries older than [daysToKeep] days */
    suspend operator fun invoke(daysToKeep: Int = 7) {
        val cutoff = System.currentTimeMillis() - (daysToKeep * 24 * 60 * 60 * 1000L)
        repository.deleteOldNotifications(cutoff)
        repository.deleteOldSummaries(cutoff)
    }
}

class GetSummariesUseCase @Inject constructor(
    private val repository: NotificationRepository
) {
    fun observeAll() = repository.observeSummaries()
    fun observeLatest() = repository.observeLatestSummary()
}
