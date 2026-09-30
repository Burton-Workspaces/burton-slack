package com.burton.slack.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.burton.slack.data.repository.SlackRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: SlackRepository,
) : ViewModel() {
    val state = repository.state

    fun signOut() {
        viewModelScope.launch { repository.signOut() }
    }
}
