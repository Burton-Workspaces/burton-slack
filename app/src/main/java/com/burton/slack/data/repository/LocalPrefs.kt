package com.burton.slack.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.burtonStore: DataStore<Preferences> by preferencesDataStore("burton_slack")

@Singleton
class LocalPrefs @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val store = context.burtonStore

    val token: Flow<String> = store.data.map { it[TOKEN].orEmpty() }

    suspend fun setToken(value: String) {
        store.edit { prefs ->
            val trimmed = value.trim()
            if (trimmed.isBlank()) prefs.remove(TOKEN) else prefs[TOKEN] = trimmed
        }
    }

    suspend fun clearToken() = setToken("")

    private companion object {
        val TOKEN = stringPreferencesKey("user_token")
    }
}
