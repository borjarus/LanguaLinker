package com.mila.langualinker.data.settings

import java.io.File
import java.util.Properties
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Desktop settings store: a properties file next to the database.
 *
 * As on iOS, the LLM API key is not persisted here — a plaintext properties file in the user's
 * home directory is exactly what cross-cutting decision C6 rules out. It waits for the
 * platform keystore wrapper from the Phase 3.5 retrofit.
 */
class PropertiesAppSettingsRepository(
    private val file: File = defaultFile(),
) : AppSettingsRepository {

    private val state = MutableStateFlow(read())

    override fun getSettings(): Flow<AppSettings> = state.asStateFlow()

    private fun load(): Properties = Properties().apply {
        if (file.exists()) file.inputStream().use { load(it) }
    }

    private fun read(): AppSettings {
        val props = load()
        return AppSettings(
            requestRetention = props.getProperty(KEY_REQUEST_RETENTION)?.toFloatOrNull() ?: 0.9f,
            maximumInterval = props.getProperty(KEY_MAXIMUM_INTERVAL)?.toIntOrNull() ?: 36_500,
            theme = props.getProperty(KEY_THEME)
                ?.let { runCatching { AppTheme.valueOf(it) }.getOrNull() } ?: AppTheme.System,
            llmApiKey = "",
            llmApiProvider = props.getProperty(KEY_LLM_PROVIDER)
                ?.let { runCatching { LlmApiProvider.valueOf(it) }.getOrNull() }
                ?: LlmApiProvider.OpenAI,
            bundledDecksImported = props.getProperty(KEY_BUNDLED_IMPORTED)?.toBooleanStrictOrNull()
                ?: false,
        )
    }

    private fun write(key: String, value: String) {
        val props = load()
        props.setProperty(key, value)
        file.parentFile?.mkdirs()
        file.outputStream().use { props.store(it, "LanguaLinker settings") }
        state.value = read()
    }

    override suspend fun updateRequestRetention(value: Float) =
        write(KEY_REQUEST_RETENTION, value.toString())

    override suspend fun updateMaximumInterval(value: Int) =
        write(KEY_MAXIMUM_INTERVAL, value.toString())

    override suspend fun updateTheme(theme: AppTheme) = write(KEY_THEME, theme.name)

    /** No-op until the keystore-backed `SecureStorage` from C6 exists. */
    override suspend fun updateLlmApiKey(key: String) = Unit

    override suspend fun updateLlmApiProvider(provider: LlmApiProvider) =
        write(KEY_LLM_PROVIDER, provider.name)

    override suspend fun setBundledDecksImported(value: Boolean) =
        write(KEY_BUNDLED_IMPORTED, value.toString())

    companion object {
        private const val KEY_REQUEST_RETENTION = "request_retention"
        private const val KEY_MAXIMUM_INTERVAL = "maximum_interval"
        private const val KEY_THEME = "theme"
        private const val KEY_LLM_PROVIDER = "llm_api_provider"
        private const val KEY_BUNDLED_IMPORTED = "bundled_decks_imported"

        fun defaultFile(): File =
            File(System.getProperty("user.home"), ".langualinker/settings.properties")
    }
}
