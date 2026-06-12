package com.notifyai.ai.engine

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Downloads the on-device LLM model on first run.
 *
 * The model is too large (~1 GB) to ship inside the APK, so it lives on a CDN
 * and is fetched into [Context.getFilesDir] the first time the engine needs it.
 * Downloads stream to a `.part` sidecar and are renamed atomically on success,
 * so an interrupted run never leaves a half-written file that the loader would
 * later mistake for a complete model.
 *
 * Exposes [state] as a [StateFlow] so the UI can render progress instead of
 * showing the placeholder fallback while the user waits.
 */
@Singleton
class ModelDownloader @Inject constructor(
    private val context: Context
) {
    sealed class State {
        object Idle : State()
        data class Downloading(val bytesDownloaded: Long, val totalBytes: Long) : State() {
            /** 0..100, or -1 when totalBytes is unknown (server didn't send Content-Length). */
            val percent: Int
                get() = if (totalBytes > 0) ((bytesDownloaded * 100) / totalBytes).toInt() else -1
        }
        object Ready : State()
        data class Failed(val message: String) : State()
    }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    /**
     * Ensures [targetFile] exists and is fully downloaded. No-op if already present.
     * Suspends until the download finishes; throws on failure.
     */
    suspend fun ensureModelDownloaded(targetFile: File) = withContext(Dispatchers.IO) {
        if (targetFile.exists() && targetFile.length() > 0L) {
            _state.value = State.Ready
            return@withContext
        }

        val partFile = File(targetFile.parentFile, targetFile.name + ".part")
        // Don't resume — Hugging Face's resolve URL can redirect and may not always honor
        // Range requests reliably; restart cleanly to keep this code simple and correct.
        if (partFile.exists()) partFile.delete()

        var connection: HttpURLConnection? = null
        try {
            Log.d(TAG, "Starting model download from $MODEL_URL")
            connection = (URL(MODEL_URL).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = true
                connectTimeout = 30_000
                readTimeout = 30_000
                requestMethod = "GET"
            }

            val responseCode = connection.responseCode
            if (responseCode !in 200..299) {
                throw IOException("Model download failed: HTTP $responseCode")
            }

            val totalBytes = connection.contentLengthLong.takeIf { it > 0L } ?: -1L
            _state.value = State.Downloading(0L, totalBytes)

            connection.inputStream.use { input ->
                partFile.outputStream().use { output ->
                    val buf = ByteArray(64 * 1024)
                    var downloaded = 0L
                    var lastReportedPct = -1
                    while (true) {
                        val n = input.read(buf)
                        if (n == -1) break
                        output.write(buf, 0, n)
                        downloaded += n
                        // Throttle StateFlow updates to whole-percent changes so we don't
                        // wake observers thousands of times for a multi-hundred-MB download.
                        if (totalBytes > 0) {
                            val pct = ((downloaded * 100) / totalBytes).toInt()
                            if (pct != lastReportedPct) {
                                lastReportedPct = pct
                                _state.value = State.Downloading(downloaded, totalBytes)
                            }
                        } else {
                            // Unknown size — report every ~4 MB so the UI still feels alive.
                            if (downloaded / (4 * 1024 * 1024) !=
                                (_state.value as? State.Downloading)?.bytesDownloaded?.div(4 * 1024 * 1024)
                            ) {
                                _state.value = State.Downloading(downloaded, -1L)
                            }
                        }
                    }
                    output.fd.sync()
                }
            }

            if (!partFile.renameTo(targetFile)) {
                throw IOException("Failed to finalize model file at ${targetFile.absolutePath}")
            }
            Log.d(TAG, "Model download complete: ${targetFile.length()} bytes")
            _state.value = State.Ready
        } catch (e: Exception) {
            Log.e(TAG, "Model download failed: ${e.message}", e)
            // Leave .part in place for debugging; loader will retry from scratch next time
            // (we delete it at the top of the next attempt).
            _state.value = State.Failed(e.message ?: "Download failed")
            throw e
        } finally {
            connection?.disconnect()
        }
    }

    companion object {
        private const val TAG = "ModelDownloader"

        // Direct GGUF download for Qwen2.5-1.5B-Instruct Q4_K_M from the official
        // Hugging Face repo. Same file that used to ship in assets/models/.
        // Hosting on Hugging Face is fine for now; swap to your own CDN if you want
        // tighter control over availability/bandwidth.
        const val MODEL_URL =
            "https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct-GGUF/resolve/main/qwen2.5-1.5b-instruct-q4_k_m.gguf?download=true"
    }
}
