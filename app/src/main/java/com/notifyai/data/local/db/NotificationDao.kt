package com.notifyai.data.local.db

import androidx.room.*
import com.notifyai.data.local.entity.NotificationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface NotificationDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entity: NotificationEntity): Long

    @Query("SELECT * FROM notifications WHERE timestamp BETWEEN :from AND :to ORDER BY timestamp DESC")
    fun getByTimeRange(from: Long, to: Long): Flow<List<NotificationEntity>>

    @Query("SELECT * FROM notifications WHERE timestamp BETWEEN :from AND :to ORDER BY timestamp DESC")
    suspend fun getByTimeRangeSync(from: Long, to: Long): List<NotificationEntity>

    @Query("SELECT * FROM notifications WHERE isProcessed = 0 ORDER BY timestamp DESC")
    suspend fun getUnprocessed(): List<NotificationEntity>

    @Query("SELECT * FROM notifications ORDER BY timestamp DESC LIMIT :limit")
    fun observeRecent(limit: Int = 100): Flow<List<NotificationEntity>>

    @Query("SELECT * FROM notifications ORDER BY timestamp DESC")
    fun observeAll(): Flow<List<NotificationEntity>>

    @Query("SELECT * FROM notifications WHERE packageName = :pkg ORDER BY timestamp DESC")
    fun observeByApp(pkg: String): Flow<List<NotificationEntity>>

    @Query("SELECT * FROM notifications WHERE (title LIKE '%' || :query || '%' OR message LIKE '%' || :query || '%') ORDER BY timestamp DESC")
    fun searchNotifications(query: String): Flow<List<NotificationEntity>>

    @Query("UPDATE notifications SET isProcessed = 1 WHERE id IN (:ids)")
    suspend fun markProcessed(ids: List<Long>)

    @Query("DELETE FROM notifications WHERE timestamp < :before")
    suspend fun deleteOlderThan(before: Long)

    @Query("SELECT COUNT(*) FROM notifications WHERE timestamp BETWEEN :from AND :to")
    suspend fun countInRange(from: Long, to: Long): Int

    @Query("SELECT DISTINCT packageName FROM notifications ORDER BY packageName")
    suspend fun getDistinctApps(): List<String>

    @Query("""
        SELECT * FROM notifications 
        WHERE timestamp BETWEEN :from AND :to 
        AND (:packageName IS NULL OR packageName = :packageName)
        AND (:category IS NULL OR category = :category)
        ORDER BY timestamp DESC
    """)
    fun observeFiltered(
        from: Long,
        to: Long,
        packageName: String? = null,
        category: String? = null
    ): Flow<List<NotificationEntity>>
}
