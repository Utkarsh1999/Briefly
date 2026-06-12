package com.notifyai.worker

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WorkManagerScheduler @Inject constructor(
    private val context: Context
) {
    fun schedulePeriodicSummarization(batteryAware: Boolean = true) {
        val constraintsBuilder = Constraints.Builder()
            .setRequiresBatteryNotLow(true)

        if (batteryAware) {
            constraintsBuilder.setRequiresCharging(true)
        }

        val request = PeriodicWorkRequestBuilder<SummarizationWorker>(6, TimeUnit.HOURS)
            .setConstraints(constraintsBuilder.build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, MIN_BACKOFF_SECONDS, TimeUnit.SECONDS)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME_SUMMARIZATION,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    fun triggerImmediateSummarization() {
        val request = androidx.work.OneTimeWorkRequestBuilder<SummarizationWorker>()
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, MIN_BACKOFF_SECONDS, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            WORK_NAME_IMMEDIATE_SUMMARIZATION,
            ExistingWorkPolicy.KEEP,
            request
        )
    }

    companion object {
        private const val WORK_NAME_SUMMARIZATION = "periodic_notification_summarization"
        private const val WORK_NAME_IMMEDIATE_SUMMARIZATION = "immediate_notification_summarization"
        private const val MIN_BACKOFF_SECONDS = 30L
    }
}
