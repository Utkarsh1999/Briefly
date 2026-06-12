package com.notifyai.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "notifications",
    indices = [
        Index(value = ["packageName"]),
        Index(value = ["timestamp"]),
        Index(value = ["isProcessed"]),
        Index(value = ["threadId"])
    ]
)
data class NotificationEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val packageName: String,
    val appName: String,
    val title: String,
    val message: String,
    val timestamp: Long,
    val category: String,
    val isProcessed: Boolean = false,
    val threadId: String? = null,
    val priorityScore: Int = 0,
    val conversationTitle: String? = null
)
