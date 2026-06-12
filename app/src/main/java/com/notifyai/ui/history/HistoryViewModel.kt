package com.notifyai.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.notifyai.data.local.entity.NotificationEntity
import com.notifyai.domain.usecase.GetNotificationsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val getNotificationsUseCase: GetNotificationsUseCase
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private val _selectedApp = MutableStateFlow<String?>(null)
    val selectedApp = _selectedApp.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val notifications: StateFlow<List<NotificationEntity>> = combine(
        _searchQuery, _selectedApp
    ) { query, app ->
        Pair(query, app)
    }.flatMapLatest { (query, app) ->
        when {
            query.isNotBlank() -> getNotificationsUseCase.search(query)
            app != null -> getNotificationsUseCase.observeFiltered(0, Long.MAX_VALUE, pkg = app)
            else -> getNotificationsUseCase.observeAll()
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    private val _distinctApps = MutableStateFlow<List<String>>(emptyList())
    val distinctApps = _distinctApps.asStateFlow()

    init {
        viewModelScope.launch {
            _distinctApps.value = getNotificationsUseCase.getDistinctApps()
        }
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun selectApp(app: String?) {
        _selectedApp.value = app
    }
}
