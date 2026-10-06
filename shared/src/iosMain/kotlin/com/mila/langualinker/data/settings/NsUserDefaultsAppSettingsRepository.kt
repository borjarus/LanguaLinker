package com.mila.langualinker.data.settings

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import platform.Foundation.NSUserDefaults

/**
 * iOS settings store.
 *
 * DataStore is Android-only in this build, and `NSUserDefaults` is the platform-native
 * equivalent for small preferences — no extra dependency, and it is what an iOS user's device
 * backup already covers.
 *
 * The LLM API key is deliberately **not** written here: `NSUserDefaults` is a plaintext plist
 * inside the app container. It belongs in the Keychain, which is what cross-cutting decision
 * C6 schedules for the Phase 3.5 retrofit; until `SecureStorage` exists, setting the key on
 * iOS is a no-op rather than a silent leak.
 */
class NsUserDefaultsAppSettingsRepository(
    private val defaults: NSUserDefaults = NSUserDefaults.standardUserDefaults,
) : AppSettingsRepository {

    private val state = MutableStateFlow(read())

    override fun getSettings(): Flow<AppSettings> = state.asStateFlow()

    private fun read(): AppSettings = AppSettings(
        requestRetention = defaults.objectForKey(KEY_REQUEST_RETENTION)
            ?.let { defaults.floatForKey(KEY_REQUEST_RETENTION) } ?: 0.9f,
        maximumInterval = defaults.objectForKey(KEY_MAXIMUM_INTERVAL)
            ?.let { defaults.integerForKey(KEY_MAXIMUM_INTERVAL).toInt() } ?: 36_500,
        theme = defaults.stringForKey(KEY_THEME)
            ?.let { runCatching { AppTheme.valueOf(it) }.getOrNull() } ?: AppTheme.System,
        llmApiKey = "",
        llmApiProvider = defaults.stringForKey(KEY_LLM_PROVIDER)
            ?.let { runCatching { LlmApiProvider.valueOf(it) }.getOrNull() } ?: LlmApiProvider.OpenAI,
        bundledDecksImported = defaults.boolForKey(KEY_BUNDLED_IMPORTED),
    )

    override suspend fun updateRequestRetention(value: Float) {
        defaults.setFloat(value, KEY_REQUEST_RETENTION)
        state.value = read()
    }

    override suspend fun updateMaximumInterval(value: Int) {
        defaults.setInteger(value.toLong(), KEY_MAXIMUM_INTERVAL)
        state.value = read()
    }

    override suspend fun updateTheme(theme: AppTheme) {
        defaults.setObject(theme.name, KEY_THEME)
        state.value = read()
    }

    /** No-op until the Keychain-backed `SecureStorage` from C6 exists. */
    override suspend fun updateLlmApiKey(key: String) = Unit

    override suspend fun updateLlmApiProvider(provider: LlmApiProvider) {
        defaults.setObject(provider.name, KEY_LLM_PROVIDER)
        state.value = read()
    }

    override suspend fun setBundledDecksImported(value: Boolean) {
        defaults.setBool(value, KEY_BUNDLED_IMPORTED)
        state.value = read()
    }

    private companion object {
        const val KEY_REQUEST_RETENTION = "request_retention"
        const val KEY_MAXIMUM_INTERVAL = "maximum_interval"
        const val KEY_THEME = "theme"
        const val KEY_LLM_PROVIDER = "llm_api_provider"
        const val KEY_BUNDLED_IMPORTED = "bundled_decks_imported"
    }
}
