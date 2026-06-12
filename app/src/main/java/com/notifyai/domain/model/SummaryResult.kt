package com.notifyai.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class SummaryResult(
    val importantItems: List<String>,
    val actionItems: List<String>,
    val promotions: List<String>,
    val summary: String,
    val socialUpdates: List<String> = emptyList()
)
