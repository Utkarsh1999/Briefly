package com.notifyai.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.util.Log
import com.notifyai.ai.engine.LocalLlmEngine
import com.notifyai.ai.engine.ModelDownloader
import com.notifyai.domain.model.SummaryResult
import com.notifyai.domain.usecase.GetSummariesUseCase
import com.notifyai.worker.WorkManagerScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    getSummariesUseCase: GetSummariesUseCase,
    private val workManagerScheduler: WorkManagerScheduler,
    llmEngine: LocalLlmEngine
) : ViewModel() {

    private val json = Json { ignoreUnknownKeys = true }

    val latestSummary: StateFlow<SummaryResult?> = getSummariesUseCase.observeLatest()
        .map { entity ->
            entity?.let {
                try {
                    json.decodeFromString<SummaryResult>(it.summaryJson)
                } catch (e: Exception) {
                    Log.e("HomeViewModel", "Failed to decode summaryJson: ${e.message}\nRaw: ${it.summaryJson.take(300)}")
                    // Return fallback so UI shows something rather than empty state
                    SummaryResult(
                        importantItems = emptyList(),
                        actionItems = emptyList(),
                        promotions = emptyList(),
                        socialUpdates = emptyList(),
                        summary = "Summary available but could not be displayed. Check logs."
                    )
                }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    private val _isSummarizing = MutableStateFlow(false)
    val isSummarizing: StateFlow<Boolean> = _isSummarizing.asStateFlow()

    val isModelGenerating: StateFlow<Boolean> = llmEngine.isGenerating

    val llmErrorMessage: StateFlow<String?> = llmEngine.lastErrorMessage

    /**
     * First-run model download progress. UI can show a progress card while this is
     * [ModelDownloader.State.Downloading] so the ~1 GB fetch doesn't look like the
     * app is hung or stuck on the placeholder.
     */
    val modelDownloadState: StateFlow<ModelDownloader.State> = llmEngine.downloadState

    fun forceSummarize() {
        _isSummarizing.value = true
        _showError.value = false
        workManagerScheduler.triggerImmediateSummarization()
    }

    fun onSummarizationFinished() {
        _isSummarizing.value = false
    }

    private val _showError = MutableStateFlow(false)
    val showError: StateFlow<Boolean> = _showError.asStateFlow()

    fun acknowledgeError() {
        _showError.value = false
    }

    fun syncEngineStatus() {
        _showError.value = llmErrorMessage.value != null
        if (llmErrorMessage.value == null && _isSummarizing.value && !isModelGenerating.value) {
            _isSummarizing.value = false
        }
    }
}
