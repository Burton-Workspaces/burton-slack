package com.burton.slack.ui.signin

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.burton.slack.BuildConfig
import com.burton.slack.data.repository.SlackRepository
import com.burton.slack.data.slack.SlackApiException
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SignInUi(
    val token: String = "",
    val busy: Boolean = false,
    val error: String? = null,
    val pasteOpen: Boolean = false,
    val oauthConfigured: Boolean = BuildConfig.SLACK_CLIENT_ID.isNotBlank(),
)

@HiltViewModel
class SignInViewModel @Inject constructor(
    private val repository: SlackRepository,
) : ViewModel() {
    private val _ui = MutableStateFlow(SignInUi())
    val ui = _ui.asStateFlow()

    init {
        viewModelScope.launch {
            repository.state.collect { snap ->
                if (snap.tokenPresent) return@collect
                if (snap.error != null) {
                    _ui.update { it.copy(busy = false, error = snap.error) }
                }
            }
        }
    }

    fun onTokenChange(value: String) {
        _ui.update { it.copy(token = value, error = null) }
    }

    fun togglePaste() {
        _ui.update { it.copy(pasteOpen = !it.pasteOpen, error = null) }
    }

    fun connectWithSlack(openUrl: (String) -> Unit) {
        if (BuildConfig.SLACK_CLIENT_ID.isBlank()) {
            _ui.update {
                it.copy(
                    error = "This build has no Slack Client ID. Fill slack/client-id.txt after installing the Slack app, or paste a user token.",
                    pasteOpen = true,
                )
            }
            return
        }
        viewModelScope.launch {
            _ui.update { it.copy(busy = true, error = null) }
            runCatching { repository.beginOAuth() }
                .onSuccess { url ->
                    _ui.update { it.copy(busy = false) }
                    openUrl(url)
                }
                .onFailure { error ->
                    _ui.update { it.copy(busy = false, error = connectError(error)) }
                }
        }
    }

    fun connect() {
        val token = _ui.value.token.trim()
        if (token.isBlank()) {
            _ui.update { it.copy(error = "Paste a Slack user token to connect.", pasteOpen = true) }
            return
        }
        viewModelScope.launch {
            _ui.update { it.copy(busy = true, error = null) }
            runCatching { repository.signIn(token) }
                .onFailure { error ->
                    _ui.update { it.copy(busy = false, error = connectError(error)) }
                }
        }
    }

    private fun connectError(error: Throwable): String {
        val code = (error as? SlackApiException)?.code ?: error.message.orEmpty()
        return when (code) {
            "invalid_auth", "not_authed" -> "That token was rejected."
            "missing_scope" -> "The token is missing a required scope."
            "missing_client_id" ->
                "This build has no Slack Client ID. Fill slack/client-id.txt after installing the Slack app."
            else -> error.message ?: "Could not connect."
        }
    }
}

fun openAuthorizeUrl(context: Context, url: String) {
    val uri = Uri.parse(url)
    try {
        CustomTabsIntent.Builder()
            .setShowTitle(true)
            .setColorScheme(CustomTabsIntent.COLOR_SCHEME_DARK)
            .build()
            .launchUrl(context, uri)
    } catch (_: Exception) {
        context.startActivity(Intent(Intent.ACTION_VIEW, uri))
    }
}
