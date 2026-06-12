package com.notifyai.ai.engine

import com.notifyai.ai.metrics.InferenceMetrics
import kotlinx.coroutines.flow.StateFlow

interface LocalLlmEngine {
    val isModelLoaded: StateFlow<Boolean>
    val isGenerating: StateFlow<Boolean>
    val lastMetrics: StateFlow<InferenceMetrics?>
    val lastErrorMessage: StateFlow<String?>

    /**
     * Whether this device is fast enough to run on-device inference without
     * tripping ART's thread-suspension watchdog (which aborts the process on
     * runaway native compute). Null until the one-time benchmark has run.
     */
    val isDeviceCapable: StateFlow<Boolean?>

    /**
     * Live state of the one-time model download. The model is no longer bundled
     * in assets — it's fetched on first use. UI surfaces this so the user sees
     * "Downloading model… 47%" instead of a confusing empty/placeholder view.
     */
    val downloadState: StateFlow<ModelDownloader.State>

    // `modelPath` is now ignored (kept for ABI compatibility with existing callers);
    // the model is downloaded once into filesDir and reused thereafter.
    suspend fun loadModel(modelPath: String = "")
    
    // The summary is a compact JSON object (a handful of short list items plus
    // a one-line summary) — it never needs anywhere near 1024 tokens. Capping it
    // keeps wall-clock time bounded on slow CPU-only devices (~2s/token here),
    // where the old default could otherwise run for the better part of an hour.
    suspend fun generate(prompt: String, maxTokens: Int = 256): String
    
    fun unloadModel()
}
