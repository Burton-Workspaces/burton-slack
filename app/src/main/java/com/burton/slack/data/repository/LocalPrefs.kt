package com.burton.slack.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.burton.slack.data.slack.SlackAuth
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.burtonStore: DataStore<Preferences> by preferencesDataStore("burton_slack")

data class PendingOauth(
    val verifier: String,
    val state: String,
)

@Singleton
class LocalPrefs @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val store = context.burtonStore

    val auth: Flow<SlackAuth> = store.data.map { prefs ->
        SlackAuth(
            accessToken = prefs[TOKEN].orEmpty(),
            refreshToken = prefs[REFRESH].orEmpty(),
            expiresAtEpochMs = prefs[EXPIRES] ?: 0L,
        )
    }

    val token: Flow<String> = auth.map { it.accessToken }

    val pendingOauth: Flow<PendingOauth?> = store.data.map { prefs ->
        val verifier = prefs[OAUTH_VERIFIER].orEmpty()
        val state = prefs[OAUTH_STATE].orEmpty()
        if (verifier.isBlank() || state.isBlank()) null else PendingOauth(verifier, state)
    }

    suspend fun setAuth(value: SlackAuth) {
        store.edit { prefs ->
            val access = value.accessToken.trim()
            if (access.isBlank()) {
                prefs.remove(TOKEN)
                prefs.remove(REFRESH)
                prefs.remove(EXPIRES)
            } else {
                prefs[TOKEN] = access
                val refresh = value.refreshToken.trim()
                if (refresh.isBlank()) prefs.remove(REFRESH) else prefs[REFRESH] = refresh
                if (value.expiresAtEpochMs <= 0L) {
                    prefs.remove(EXPIRES)
                } else {
                    prefs[EXPIRES] = value.expiresAtEpochMs
                }
            }
            prefs.remove(OAUTH_VERIFIER)
            prefs.remove(OAUTH_STATE)
        }
    }

    suspend fun setToken(value: String) {
        setAuth(SlackAuth(accessToken = value.trim()))
    }

    suspend fun clearToken() = setAuth(SlackAuth())

    suspend fun setPendingOauth(verifier: String, state: String) {
        store.edit { prefs ->
            prefs[OAUTH_VERIFIER] = verifier
            prefs[OAUTH_STATE] = state
        }
    }

    suspend fun clearPendingOauth() {
        store.edit { prefs ->
            prefs.remove(OAUTH_VERIFIER)
            prefs.remove(OAUTH_STATE)
        }
    }

    private companion object {
        val TOKEN = stringPreferencesKey("user_token")
        val REFRESH = stringPreferencesKey("refresh_token")
        val EXPIRES = longPreferencesKey("token_expires_at")
        val OAUTH_VERIFIER = stringPreferencesKey("oauth_verifier")
        val OAUTH_STATE = stringPreferencesKey("oauth_state")
    }
}
