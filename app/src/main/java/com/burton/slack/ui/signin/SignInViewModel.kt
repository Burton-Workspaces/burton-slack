package com.burton.slack.ui.signin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.burton.slack.data.repository.SlackRepository
import com.burton.slack.data.slack.SlackApiException
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SignInUi(
    val token: String = "",
    val busy: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class SignInViewModel @Inject constructor(
    private val repository: SlackRepository,
) : ViewModel() {
    private val _ui = MutableStateFlow(SignInUi())
    val ui = _ui.asStateFlow()

    fun onTokenChange(value: String) {
        _ui.value = _ui.value.copy(token = value, error = null)
    }

    fun connect() {
        val token = _ui.value.token.trim()
        if (token.isBlank()) {
            _ui.value = _ui.value.copy(error = "Paste a Slack user token to connect.")
            return
        }
        viewModelScope.launch {
            _ui.value = _ui.value.copy(busy = true, error = null)
            runCatching { repository.signIn(token) }
                .onFailure { error ->
                    val code = (error as? SlackApiException)?.code
                    _ui.value = _ui.value.copy(
                        busy = false,
                        error = when (code) {
                            "invalid_auth", "not_authed" -> "That token was rejected."
                            "missing_scope" -> "The token is missing a required scope."
                            else -> error.message ?: "Could not connect."
                        },
                    )
                }
        }
    }
}
