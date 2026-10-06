package com.mila.langualinker.data.settings

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Settings held in memory only.
 *
 * Used by tests and by the web target, which has no persistent settings store yet. Nothing
 * survives a restart, so on the web every launch is a first launch.
 */
class InMemoryAppSettingsRepository(
    initial: AppSettings = AppSettings(),
) : AppSettingsRepository {

    private val state = MutableStateFlow(initial)

    override fun getSettings(): Flow<AppSettings> = state.asStateFlow()

    override suspend fun updateRequestRetention(value: Float) {
        state.update { it.copy(requestRetention = value) }
    }

    override suspend fun updateMaximumInterval(value: Int) {
        state.update { it.copy(maximumInterval = value) }
    }

    override suspend fun updateTheme(theme: AppTheme) {
        state.update { it.copy(theme = theme) }
    }

    override suspend fun updateLlmApiKey(key: String) {
        state.update { it.copy(llmApiKey = key) }
    }

    override suspend fun updateLlmApiProvider(provider: LlmApiProvider) {
        state.update { it.copy(llmApiProvider = provider) }
    }

    override suspend fun setBundledDecksImported(value: Boolean) {
        state.update { it.copy(bundledDecksImported = value) }
    }
}
