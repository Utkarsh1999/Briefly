package com.notifyai.domain.model

/**
 * A cluster groups related notifications together (same app + conversation/thread)
 * before they are sent to the LLM for summarization.
 */
data class NotificationCluster(
    val appName: String,
    val packageName: String,
    val threadId: String?,
    val conversationTitle: String?,
    val notifications: List<Notification>,
    val category: NotificationCategory
) {
    val count: Int get() = notifications.size
    val latestTimestamp: Long get() = notifications.maxOf { it.timestamp }

    /** Condensed text representation sent to the LLM */
    fun toPromptText(): String {
        val header = conversationTitle?.let { "$appName – $it" } ?: appName
        val lines = notifications.takeLast(10).joinToString("\n") { n ->
            val title = if (n.title.isNotBlank() && n.title != appName) "[${n.title}]" else ""
            "- $title ${n.message}".trim()
        }
        return "$header:\n$lines"
    }
}
