package com.notifyai.data.local.db

import androidx.room.*
import com.notifyai.data.local.entity.SummaryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SummaryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: SummaryEntity): Long

    @Query("SELECT * FROM summaries ORDER BY timestamp DESC")
    fun observeAll(): Flow<List<SummaryEntity>>

    @Query("SELECT * FROM summaries ORDER BY timestamp DESC LIMIT 1")
    fun observeLatest(): Flow<SummaryEntity?>

    @Query("SELECT * FROM summaries WHERE timestamp BETWEEN :from AND :to ORDER BY timestamp DESC")
    suspend fun getSummariesInRange(from: Long, to: Long): List<SummaryEntity>

    @Query("DELETE FROM summaries WHERE timestamp < :before")
    suspend fun deleteOlderThan(before: Long)
}
