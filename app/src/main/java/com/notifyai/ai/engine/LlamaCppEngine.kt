package com.notifyai.ai.engine

import android.content.Context
import android.util.Log
import com.arm.aichat.AiChat
import com.arm.aichat.InferenceEngine
import com.notifyai.ai.metrics.InferenceMetrics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LlamaCppEngine @Inject constructor(
    private val context: Context,
    private val modelDownloader: ModelDownloader,
) : LocalLlmEngine {

    override val downloadState: StateFlow<ModelDownloader.State> = modelDownloader.state

    private val _isModelLoaded = MutableStateFlow(false)
    override val isModelLoaded: StateFlow<Boolean> = _isModelLoaded.asStateFlow()

    private val _isGenerating = MutableStateFlow(false)
    override val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    private val _lastMetrics = MutableStateFlow<InferenceMetrics?>(null)
    override val lastMetrics: StateFlow<InferenceMetrics?> = _lastMetrics.asStateFlow()

    private val _lastErrorMessage = MutableStateFlow<String?>(null)
    override val lastErrorMessage: StateFlow<String?> = _lastErrorMessage.asStateFlow()

    private val prefs by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
    private val _isDeviceCapable = MutableStateFlow(
        if (prefs.contains(PREF_KEY_IS_DEVICE_CAPABLE)) {
            prefs.getBoolean(PREF_KEY_IS_DEVICE_CAPABLE, true)
        } else {
            null
        }
    )
    override val isDeviceCapable: StateFlow<Boolean?> = _isDeviceCapable.asStateFlow()

    // Mutable so we can throw away a broken instance and get a fresh one.
    // The ARM AiChat SDK's InferenceEngine has an internal state machine that enters
    // a permanent Error state after a failure; re-using it causes "Cannot load model in Error!".
    private var engine: com.arm.aichat.InferenceEngine? = null
    private val modelFile by lazy { File(context.filesDir, "notifyai-model.gguf") }

    // Guards loadModel()/generate() so concurrent worker runs can't race on the
    // shared `engine` instance (e.g. one run disposing another's in-flight engine).
    private val engineMutex = Mutex()

    private suspend fun freshEngine(): com.arm.aichat.InferenceEngine {
        // Dispose old instance silently, then create a brand-new one.
        try { engine?.cleanUp() } catch (_: Exception) {}
        return AiChat.getInferenceEngine(context).also { created ->
            engine = created
            awaitNativeInitialization(created)
        }
    }

    private suspend fun awaitNativeInitialization(eng: com.arm.aichat.InferenceEngine) {
        withTimeoutOrNull(15_000) {
            while (eng.state.value is InferenceEngine.State.Initializing ||
                eng.state.value is InferenceEngine.State.Uninitialized
            ) {
                delay(50)
            }
        }
        if (eng.state.value is InferenceEngine.State.Error) {
            throw IllegalStateException(
                "Local engine initialization failed: ${(eng.state.value as InferenceEngine.State.Error).exception.message}"
            )
        }
    }

    override suspend fun loadModel(modelPath: String) {
        engineMutex.withLock { loadModelLocked(modelPath) }
    }

    private suspend fun loadModelLocked(modelPath: String) {
        withContext(Dispatchers.IO) {
            val start = System.currentTimeMillis()
            try {
                _lastErrorMessage.value = null
                // Always use a fresh engine instance to avoid stale Error state.
                val eng = freshEngine()
                // First run only — downloads the ~1 GB GGUF from the CDN into filesDir.
                // No-ops on subsequent runs when the file is already present.
                modelDownloader.ensureModelDownloaded(modelFile)
                eng.loadModel(modelFile.absolutePath)
                _isModelLoaded.value = true
                _lastMetrics.value = InferenceMetrics(loadTimeMs = System.currentTimeMillis() - start)
                probeDeviceCapabilityIfNeeded(eng)
            } catch (e: Exception) {
                _isModelLoaded.value = false
                _lastErrorMessage.value = e.message ?: "Failed to load local model."
                _lastMetrics.value = InferenceMetrics(success = false, errorMessage = e.message)
                throw IllegalStateException("Cannot load model", e)
            }
        }
    }

    override suspend fun generate(prompt: String, maxTokens: Int): String = engineMutex.withLock {
        withContext(Dispatchers.IO) {
            val currentEngine = engine
            if (!_isModelLoaded.value || currentEngine == null || currentEngine.state.value != InferenceEngine.State.ModelReady) {
                try {
                    loadModelLocked("notifyai-model.gguf")
                } catch (e: Exception) {
                    _lastErrorMessage.value = e.message ?: "Model loading failed."
                    throw IllegalStateException("Model not available", e)
                }
            }

            val readyEngine = engine
                ?: throw IllegalStateException("Engine not initialized after loadModel()")
            if (readyEngine.state.value != InferenceEngine.State.ModelReady) {
                _isModelLoaded.value = false
                throw IllegalStateException("Local engine is not ready: ${readyEngine.state.value.javaClass.simpleName}")
            }

            Log.d("Summarizer", "engine state before generate: ${readyEngine.state.value}")

            val start = System.currentTimeMillis()
            _lastErrorMessage.value = null
            _isGenerating.value = true

            try {
                val output = buildString {
                    readyEngine.sendUserPrompt(prompt, predictLength = maxTokens).collect { token ->
                        Log.v("Summarizer", "token received: $token")
                        append(token)
                    }
                }

                Log.d("Summarizer", "generation collect completed, output len=${output.length}")

                _lastMetrics.value = _lastMetrics.value?.copy(
                    generationTimeMs = System.currentTimeMillis() - start
                )
                output
            } finally {
                _isGenerating.value = false
            }
        }
    }

    override fun unloadModel() {
        try { engine?.cleanUp() } catch (_: Exception) {}
        engine = null
        _isModelLoaded.value = false
        _isGenerating.value = false
        _lastErrorMessage.value = null
    }

    /**
     * One-time probe that runs a small, bounded benchmark to check whether this
     * device can run native inference fast enough to avoid ART's thread-suspension
     * watchdog (which SIGABRTs the whole process on runaway native compute — see
     * the on-device crash investigation that led to this check). The verdict is
     * persisted so we never repeat the probe (or the real workload) on a device
     * that's already known to be too slow.
     */
    private suspend fun probeDeviceCapabilityIfNeeded(eng: com.arm.aichat.InferenceEngine) {
        if (_isDeviceCapable.value != null) return

        val capable = try {
            val start = System.currentTimeMillis()
            eng.bench(pp = BENCH_PROMPT_TOKENS, tg = BENCH_GEN_TOKENS, pl = BENCH_PREDICT_LENGTH, nr = 1)
            val elapsedMs = System.currentTimeMillis() - start
            Log.d("Summarizer", "Capability probe took ${elapsedMs}ms (threshold ${BENCH_THRESHOLD_MS}ms)")
            elapsedMs <= BENCH_THRESHOLD_MS
        } catch (e: Exception) {
            Log.e("Summarizer", "Capability probe failed, assuming device incapable: ${e.message}")
            false
        }

        Log.d("Summarizer", "Device capability verdict: ${if (capable) "capable" else "too slow, disabling on-device summarization"}")
        _isDeviceCapable.value = capable
        prefs.edit()
            .putBoolean(PREF_KEY_IS_DEVICE_CAPABLE, capable)
            .putLong(PREF_KEY_BENCHMARKED_AT, System.currentTimeMillis())
            .apply()
    }

    companion object {
        private const val PREFS_NAME = "notifyai_llm_capability"
        private const val PREF_KEY_IS_DEVICE_CAPABLE = "is_device_capable"
        private const val PREF_KEY_BENCHMARKED_AT = "benchmarked_at"

        // Small, bounded benchmark workload — must complete in seconds, never minutes,
        // so the probe itself can't trigger the very crash it's meant to detect.
        private const val BENCH_PROMPT_TOKENS = 16
        private const val BENCH_GEN_TOKENS = 16
        private const val BENCH_PREDICT_LENGTH = 16
        // Summarization runs as a periodic background worker, not an interactive flow,
        // so devices that generate at a couple seconds/token are still usable — they
        // just take longer. 30s was tuned for fast devices and rejected slower-but-fine
        // ones; 90s gives slower CPUs room to pass while still catching truly runaway cases.
        private const val BENCH_THRESHOLD_MS = 90_000L
    }
}
