package com.notifyai.domain.model

data class Notification(
    val id: Long,
    val packageName: String,
    val appName: String,
    val title: String,
    val message: String,
    val timestamp: Long,
    val category: NotificationCategory,
    val isProcessed: Boolean,
    val threadId: String?,
    val priorityScore: Int,
    val conversationTitle: String?
)

enum class NotificationCategory(val label: String) {
    MESSAGE("Messages"),
    EMAIL("Email"),
    SOCIAL("Social"),
    PROMOTION("Promotions"),
    ALERT("Alerts"),
    OTHER("Other");

    companion object {
        fun from(value: String?): NotificationCategory =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: OTHER
    }
}
