package com.burton.slack.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.burton.slack.data.repository.SlackRepository
import com.burton.slack.domain.SearchHit
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SearchUi(
    val query: String = "",
    val hits: List<SearchHit> = emptyList(),
    val loading: Boolean = false,
    val searched: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val repository: SlackRepository,
) : ViewModel() {
    val snapshot = repository.state
    private val _ui = MutableStateFlow(SearchUi())
    val ui = _ui.asStateFlow()
    private var job: Job? = null

    fun onQueryChange(value: String) {
        _ui.value = _ui.value.copy(query = value, error = null)
        job?.cancel()
        if (value.isBlank()) {
            _ui.value = SearchUi()
            return
        }
        job = viewModelScope.launch {
            delay(350)
            _ui.value = _ui.value.copy(loading = true)
            runCatching { repository.search(value.trim()) }
                .onSuccess { hits ->
                    _ui.value = _ui.value.copy(hits = hits, loading = false, searched = true, error = null)
                }
                .onFailure { error ->
                    _ui.value = _ui.value.copy(loading = false, searched = true, error = error.message)
                }
        }
    }

    fun clear() {
        job?.cancel()
        _ui.value = SearchUi()
    }
}
