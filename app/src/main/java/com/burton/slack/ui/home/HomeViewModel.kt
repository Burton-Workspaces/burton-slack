package com.burton.slack.ui.home

import androidx.lifecycle.ViewModel
import com.burton.slack.data.repository.SlackRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: SlackRepository,
) : ViewModel() {
    val state = repository.state

    init {
        repository.start()
    }

    fun refresh() = repository.refresh()
}
