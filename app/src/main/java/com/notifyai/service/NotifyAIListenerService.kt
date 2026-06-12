package com.notifyai.service

import android.app.Notification
import android.content.pm.PackageManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.notifyai.data.local.entity.NotificationEntity
import com.notifyai.domain.model.NotificationCategory
import com.notifyai.domain.usecase.InsertNotificationUseCase
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class NotifyAIListenerService : NotificationListenerService() {

    @Inject
    lateinit var insertNotificationUseCase: InsertNotificationUseCase

    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)
    
    // Quick deduplication cache (title + message hash -> timestamp)
    private val recentNotifications = mutableMapOf<Int, Long>()

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null) return

        val notification = sbn.notification
        val extras = notification.extras
        
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim() ?: ""
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim() ?: ""
        
        // Ignore empty notifications or progress bars
        if (title.isEmpty() && text.isEmpty()) return
        if (notification.flags and Notification.FLAG_FOREGROUND_SERVICE != 0) return
        if (notification.flags and Notification.FLAG_ONGOING_EVENT != 0) return

        val packageName = sbn.packageName
        
        // Simple deduplication within 5 seconds window
        val hash = title.hashCode() + text.hashCode() + packageName.hashCode()
        val now = System.currentTimeMillis()
        if (recentNotifications.containsKey(hash)) {
            val lastSeen = recentNotifications[hash] ?: 0L
            if (now - lastSeen < 5000L) return
        }
        recentNotifications[hash] = now
        // Clean up old hashes occasionally
        if (recentNotifications.size > 100) recentNotifications.clear()

        val appName = getAppName(packageName)
        val category = determineCategory(packageName, notification.category)
        val conversationTitle = extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)?.toString()?.trim()
        val threadId = conversationTitle ?: title.takeIf { it.isNotEmpty() }

        val entity = NotificationEntity(
            packageName = packageName,
            appName = appName,
            title = title,
            message = text,
            timestamp = sbn.postTime.takeIf { it > 0 } ?: now,
            category = category.name,
            threadId = threadId,
            conversationTitle = conversationTitle
        )

        scope.launch {
            try {
                insertNotificationUseCase(entity)
                Log.d("NotifyAI", "Stored notification from $appName: $title")
            } catch (e: Exception) {
                Log.e("NotifyAI", "Error storing notification", e)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        job.cancel()
    }

    private fun getAppName(packageName: String): String {
        return try {
            val pm = packageManager
            val applicationInfo = pm.getApplicationInfo(packageName, PackageManager.GET_META_DATA)
            pm.getApplicationLabel(applicationInfo).toString()
        } catch (e: PackageManager.NameNotFoundException) {
            packageName
        }
    }

    private fun determineCategory(packageName: String, systemCategory: String?): NotificationCategory {
        return when {
            packageName.contains("whatsapp") || packageName.contains("telegram") || packageName.contains("slack") -> NotificationCategory.MESSAGE
            packageName.contains("gmail") || packageName.contains("email") -> NotificationCategory.EMAIL
            packageName.contains("linkedin") || packageName.contains("instagram") || packageName.contains("facebook") || packageName.contains("twitter") -> NotificationCategory.SOCIAL
            systemCategory == Notification.CATEGORY_PROMO -> NotificationCategory.PROMOTION
            systemCategory == Notification.CATEGORY_ALARM || systemCategory == Notification.CATEGORY_ERROR -> NotificationCategory.ALERT
            else -> NotificationCategory.OTHER
        }
    }
}
