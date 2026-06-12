package com.notifyai.ui.insights

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.notifyai.data.local.entity.SummaryEntity
import com.notifyai.domain.usecase.GetSummariesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class InsightsViewModel @Inject constructor(
    getSummariesUseCase: GetSummariesUseCase
) : ViewModel() {

    val historicalSummaries: StateFlow<List<SummaryEntity>> = getSummariesUseCase.observeAll()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
}
