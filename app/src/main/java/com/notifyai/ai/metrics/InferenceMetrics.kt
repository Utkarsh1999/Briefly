package com.notifyai.ai.metrics

data class InferenceMetrics(
    val loadTimeMs: Long = 0,
    val promptProcessingTimeMs: Long = 0,
    val generationTimeMs: Long = 0,
    val tokensGenerated: Int = 0,
    val success: Boolean = true,
    val errorMessage: String? = null
)
