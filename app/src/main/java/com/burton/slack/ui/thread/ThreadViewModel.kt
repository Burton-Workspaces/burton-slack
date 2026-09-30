package com.burton.slack.ui.thread

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.burton.slack.data.repository.SlackRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ThreadViewModel @Inject constructor(
    private val repository: SlackRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    val channelId: String = savedStateHandle["channelId"] ?: ""
    val threadTs: String = savedStateHandle["threadTs"] ?: ""
    val snapshot = repository.state
    val history = repository.threads.map { it[SlackRepository.threadKey(channelId, threadTs)] }

    private val _draft = MutableStateFlow("")
    val draft = _draft.asStateFlow()
    private val _busy = MutableStateFlow(false)
    val busy = _busy.asStateFlow()

    init {
        if (channelId.isNotBlank() && threadTs.isNotBlank()) {
            repository.openThread(channelId, threadTs)
        }
    }

    fun onDraft(value: String) {
        _draft.value = value
    }

    fun send() {
        val text = _draft.value.trim()
        if (text.isBlank()) return
        viewModelScope.launch {
            _busy.value = true
            runCatching { repository.send(channelId, text, threadTs) }
                .onSuccess { _draft.value = "" }
            _busy.value = false
        }
    }

    fun react(ts: String, emoji: String) {
        viewModelScope.launch {
            runCatching { repository.toggleReaction(channelId, ts, emoji) }
        }
    }
}
