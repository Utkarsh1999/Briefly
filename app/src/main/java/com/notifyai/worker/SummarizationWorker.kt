package com.notifyai.worker

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.notifyai.ai.summarizer.NotificationSummarizer
import com.notifyai.data.local.entity.SummaryEntity
import com.notifyai.domain.model.Notification
import com.notifyai.domain.model.NotificationCategory
import com.notifyai.domain.repository.NotificationRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@HiltWorker
class SummarizationWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val repository: NotificationRepository,
    private val summarizer: NotificationSummarizer
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        Log.d("SummarizationWorker", "Starting periodic summarization...")
        
        try {
            val unprocessed = repository.getUnprocessedNotifications().ifEmpty {
                Log.d(
                    "SummarizationWorker",
                    "Database empty. Injecting mock notifications for demo."
                )
                listOf(
                    com.notifyai.data.local.entity.NotificationEntity(
                        id = -1,
                        packageName = "com.whatsapp",
                        appName = "WhatsApp",
                        title = "Rahul",
                        message = "Hey, are we still meeting tomorrow for lunch?",
                        timestamp = System.currentTimeMillis(),
                        category = "msg"
                    ),
                    com.notifyai.data.local.entity.NotificationEntity(
                        id = -2,
                        packageName = "com.google.android.gm",
                        appName = "Gmail",
                        title = "Interview Scheduled",
                        message = "Your System Design interview is scheduled for Friday 3 PM.",
                        timestamp = System.currentTimeMillis() - 10000,
                        category = "email"
                    ),
                    com.notifyai.data.local.entity.NotificationEntity(
                        id = -3,
                        packageName = "com.swiggy",
                        appName = "Swiggy",
                        title = "Hungry?",
                        message = "Flat 50% off on all pizzas today! Order now.",
                        timestamp = System.currentTimeMillis() - 20000,
                        category = "promo"
                    )
                )
            }

            // Map to domain model
            val domainNotifications = unprocessed.map { entity ->
                Notification(
                    id = entity.id,
                    packageName = entity.packageName,
                    appName = entity.appName,
                    title = entity.title,
                    message = entity.message,
                    timestamp = entity.timestamp,
                    category = NotificationCategory.from(entity.category),
                    isProcessed = entity.isProcessed,
                    threadId = entity.threadId,
                    priorityScore = entity.priorityScore,
                    conversationTitle = entity.conversationTitle
                )
            }.take(5)

            // Summarize using LLM
            val summaryResult = summarizer.summarize(domainNotifications)
            
            if (summaryResult != null) {
                // Store summary
                val summaryEntity = SummaryEntity(
                    timestamp = System.currentTimeMillis(),
                    summaryJson = Json.encodeToString(summaryResult),
                    importantCount = summaryResult.importantItems.size,
                    actionCount = summaryResult.actionItems.size,
                    promotionCount = summaryResult.promotions.size,
                    periodStart = unprocessed.minOf { it.timestamp },
                    periodEnd = unprocessed.maxOf { it.timestamp }
                )
                repository.storeSummary(summaryEntity)

                // Mark processed
                repository.markProcessed(unprocessed.map { it.id })
                Log.d("SummarizationWorker", "Summarization successful.")
            } else {
                Log.w("SummarizationWorker", "Summarization failed (null result).")
            }

            return Result.success()
        } catch (e: Exception) {
            Log.e("SummarizationWorker", "Error during summarization", e)
            return Result.retry()
        }
    }
}
