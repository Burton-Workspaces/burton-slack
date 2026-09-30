package com.burton.slack

import androidx.lifecycle.ViewModel
import com.burton.slack.data.repository.SlackRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class AppViewModel @Inject constructor(
    repository: SlackRepository,
) : ViewModel() {
    val state = repository.state

    init {
        repository.start()
    }
}
