package com.mila.langualinker.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class AppSettingsRepositoryImpl(
    private val dataStore: DataStore<Preferences>,
) : AppSettingsRepository {

    override fun getSettings(): Flow<AppSettings> = dataStore.data.map { prefs ->
        AppSettings(
            requestRetention = prefs[AppSettingsKeys.REQUEST_RETENTION] ?: 0.9f,
            maximumInterval = prefs[AppSettingsKeys.MAXIMUM_INTERVAL] ?: 36500,
            theme = prefs[AppSettingsKeys.THEME]?.let { runCatching { AppTheme.valueOf(it) }.getOrNull() } ?: AppTheme.System,
            llmApiKey = prefs[AppSettingsKeys.LLM_API_KEY] ?: "",
            llmApiProvider = prefs[AppSettingsKeys.LLM_API_PROVIDER]?.let { runCatching { LlmApiProvider.valueOf(it) }.getOrNull() } ?: LlmApiProvider.OpenAI,
        )
    }

    override suspend fun updateRequestRetention(value: Float) {
        dataStore.edit { it[AppSettingsKeys.REQUEST_RETENTION] = value }
    }

    override suspend fun updateMaximumInterval(value: Int) {
        dataStore.edit { it[AppSettingsKeys.MAXIMUM_INTERVAL] = value }
    }

    override suspend fun updateTheme(theme: AppTheme) {
        dataStore.edit { it[AppSettingsKeys.THEME] = theme.name }
    }

    override suspend fun updateLlmApiKey(key: String) {
        dataStore.edit { it[AppSettingsKeys.LLM_API_KEY] = key }
    }

    override suspend fun updateLlmApiProvider(provider: LlmApiProvider) {
        dataStore.edit { it[AppSettingsKeys.LLM_API_PROVIDER] = provider.name }
    }
}
