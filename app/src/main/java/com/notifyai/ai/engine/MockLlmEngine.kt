package com.notifyai.ai.engine

import com.notifyai.ai.metrics.InferenceMetrics
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

/**
 * A stub implementation for testing when the llama.cpp GGUF model is not available.
 * Returns a realistic JSON structure simulating LLM output.
 */
//@Singleton
//class MockLlmEngine @Inject constructor() : LocalLlmEngine {
//
//    private val _isModelLoaded = MutableStateFlow(false)
//    override val isModelLoaded: StateFlow<Boolean> = _isModelLoaded.asStateFlow()
//
//    private val _lastMetrics = MutableStateFlow<InferenceMetrics?>(null)
//    override val lastMetrics: StateFlow<InferenceMetrics?> = _lastMetrics.asStateFlow()
//
//    private val _lastErrorMessage = MutableStateFlow<String?>(null)
//    override val lastErrorMessage: StateFlow<String?> = _lastErrorMessage.asStateFlow()
//
//    override suspend fun loadModel(modelPath: String) {
//        val start = System.currentTimeMillis()
//        delay(1500) // Simulating loading time
//        _isModelLoaded.value = true
//        _lastErrorMessage.value = null
//        _lastMetrics.value = InferenceMetrics(loadTimeMs = System.currentTimeMillis() - start)
//    }
//
//    override suspend fun generate(prompt: String, maxTokens: Int): String {
//        if (!_isModelLoaded.value) {
//            loadModel("dummy_path")
//        }
//
//        val start = System.currentTimeMillis()
//        delay(Random.nextLong(2000, 4000)) // Simulating inference time (2-4 seconds)
//
//        // Mock JSON string based on the prompt content.
//        // A real LLM would extract items dynamically. Here we just return dummy data.
//        val responseJson = """
//            {
//              "importantItems": [
//                "Rahul asked: Are we meeting tomorrow?",
//                "Interview scheduled for Monday"
//              ],
//              "actionItems": [
//                "Reply to Rahul about the meeting",
//                "Prepare for Monday interview"
//              ],
//              "promotions": [
//                "Swiggy: Flat 50% off on orders",
//                "Amazon: Big billion day sale starts soon"
//              ],
//              "socialUpdates": [
//                "LinkedIn: John Doe viewed your profile"
//              ],
//              "summary": "You have an important interview coming up and a pending reply to Rahul regarding tomorrow's meeting."
//            }
//        """.trimIndent()
//
//        _lastMetrics.value = _lastMetrics.value?.copy(
//            generationTimeMs = System.currentTimeMillis() - start,
//            tokensGenerated = 150
//        )
//        _lastErrorMessage.value = null
//
//        return responseJson
//    }
//
//    override fun unloadModel() {
//        _isModelLoaded.value = false
//        _lastErrorMessage.value = null
//    }
//}
