package com.notifyai.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "summaries",
    indices = [Index(value = ["timestamp"])]
)
data class SummaryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val timestamp: Long,
    val summaryJson: String,       // JSON-serialized SummaryResult
    val importantCount: Int,
    val actionCount: Int,
    val promotionCount: Int,
    val periodStart: Long,        // notifications covered from
    val periodEnd: Long           // notifications covered to
)
