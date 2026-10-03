package com.burton.slack.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.burton.slack.data.repository.SlackRepository
import com.burton.slack.domain.SearchHit
import com.burton.slack.domain.SlackUser
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SearchUi(
    val query: String = "",
    val users: List<SlackUser> = emptyList(),
    val hits: List<SearchHit> = emptyList(),
    val loading: Boolean = false,
    val searched: Boolean = false,
    val error: String? = null,
    val openingUserId: String? = null,
    val pendingChannelId: String? = null,
)

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val repository: SlackRepository,
) : ViewModel() {
    val snapshot = repository.state
    private val _ui = MutableStateFlow(SearchUi())
    val ui = _ui.asStateFlow()
    private var job: Job? = null

    init {
        viewModelScope.launch {
            snapshot.map { it.users }.distinctUntilChanged().collect { users ->
                val query = _ui.value.query
                if (query.isBlank()) return@collect
                val next = snapshot.value.copy(users = users).matchingUsers(query)
                if (next != _ui.value.users) {
                    _ui.value = _ui.value.copy(users = next)
                }
            }
        }
    }

    fun onQueryChange(value: String) {
        job?.cancel()
        if (value.isBlank()) {
            _ui.value = SearchUi()
            return
        }
        _ui.value = _ui.value.copy(
            query = value,
            users = snapshot.value.matchingUsers(value),
            error = null,
        )
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

    fun openUser(userId: String) {
        if (userId.isBlank() || _ui.value.openingUserId != null) return
        viewModelScope.launch {
            _ui.value = _ui.value.copy(openingUserId = userId, error = null)
            runCatching { repository.openDirectMessage(userId) }
                .onSuccess { channelId ->
                    _ui.value = _ui.value.copy(openingUserId = null, pendingChannelId = channelId)
                }
                .onFailure { error ->
                    _ui.value = _ui.value.copy(openingUserId = null, error = error.message)
                }
        }
    }

    fun consumePendingChannel() {
        _ui.value = _ui.value.copy(pendingChannelId = null)
    }

    fun clear() {
        job?.cancel()
        _ui.value = SearchUi()
    }
}
