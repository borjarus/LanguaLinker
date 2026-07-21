package com.mila.langualinker.data.settings

import kotlinx.coroutines.flow.Flow

interface AppSettingsRepository {
    fun getSettings(): Flow<AppSettings>
    suspend fun updateRequestRetention(value: Float)
    suspend fun updateMaximumInterval(value: Int)
    suspend fun updateTheme(theme: AppTheme)
    suspend fun updateLlmApiKey(key: String)
    suspend fun updateLlmApiProvider(provider: LlmApiProvider)
}
