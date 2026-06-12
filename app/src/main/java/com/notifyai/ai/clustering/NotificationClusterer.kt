package com.notifyai.ai.clustering

import com.notifyai.domain.model.Notification
import com.notifyai.domain.model.NotificationCluster
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationClusterer @Inject constructor() {

    fun cluster(notifications: List<Notification>): List<NotificationCluster> {
        // Group by AppName + Conversation/ThreadId
        val groups = notifications.groupBy { 
            val keyPart2 = it.threadId ?: it.conversationTitle ?: it.title
            "${it.appName}_$keyPart2" 
        }

        return groups.map { (_, groupNotes) ->
            val first = groupNotes.first()
            // Sort by time ascending so LLM reads chronologically
            val chronological = groupNotes.sortedBy { it.timestamp }
            
            NotificationCluster(
                appName = first.appName,
                packageName = first.packageName,
                threadId = first.threadId,
                conversationTitle = first.conversationTitle ?: first.title,
                notifications = chronological,
                category = first.category
            )
        }
    }
}
