package com.notifyai.ai.summarizer

import com.notifyai.ai.clustering.NotificationClusterer
import com.notifyai.ai.engine.LocalLlmEngine
import com.notifyai.domain.model.Notification
import com.notifyai.domain.model.SummaryResult
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationSummarizer @Inject constructor(
    private val clusterer: NotificationClusterer,
    private val llmEngine: LocalLlmEngine
) {
    private val json = Json { 
        ignoreUnknownKeys = true
        isLenient = true 
    }

    suspend fun summarize(notifications: List<Notification>): SummaryResult? {
        if (notifications.isEmpty()) return null

        return withContext(Dispatchers.Default) {
            val clusters = clusterer.cluster(notifications)
            val prompt = PromptBuilder.buildPrompt(clusters)

            if (llmEngine.isDeviceCapable.value == false) {
                Log.w("Summarizer", "Device marked incapable of on-device inference; skipping LLM call.")
                return@withContext SummaryResult(
                    importantItems = listOf("On-device summary unavailable on this device"),
                    actionItems = emptyList(),
                    promotions = emptyList(),
                    socialUpdates = emptyList(),
                    summary = "You have ${notifications.size} unread notifications."
                )
            }

            Log.d("Summarizer", "Prompt sent to LLM:\n$prompt")

            val rawJson = llmEngine.generate(prompt)
            Log.d("Summarizer", "Raw LLM output:\n$rawJson")

            val extracted = extractJson(rawJson)
            Log.d("Summarizer", "Extracted JSON:\n$extracted")

            try {
                val result = json.decodeFromString<SummaryResult>(extracted)
                Log.d("Summarizer", "Parse SUCCESS: summary='${result.summary}'")
                result
            } catch (e: Exception) {
                Log.e("Summarizer", "JSON parse FAILED: ${e.message}")
                // Fallback for malformed JSON from LLM
                SummaryResult(
                    importantItems = listOf("Failed to parse AI summary"),
                    actionItems = emptyList(),
                    promotions = emptyList(),
                    socialUpdates = emptyList(),
                    summary = "You have ${notifications.size} unread notifications. (Raw: ${extracted.take(200)})"
                )
            }
        }
    }

    /**
     * Extracts just the JSON part from an LLM response just in case the LLM ignored strict formatting
     */
    private fun extractJson(raw: String): String {
        val start = raw.indexOf("{")
        val end = raw.lastIndexOf("}")
        return if (start != -1 && end != -1 && end >= start) {
            raw.substring(start, end + 1)
        } else {
            raw
        }
    }
}
