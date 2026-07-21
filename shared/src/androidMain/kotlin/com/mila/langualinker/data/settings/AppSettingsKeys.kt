package com.mila.langualinker.data.settings

import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey

internal object AppSettingsKeys {
    val REQUEST_RETENTION = floatPreferencesKey("request_retention")
    val MAXIMUM_INTERVAL = intPreferencesKey("maximum_interval")
    val THEME = stringPreferencesKey("theme")
    val LLM_API_KEY = stringPreferencesKey("llm_api_key")
    val LLM_API_PROVIDER = stringPreferencesKey("llm_api_provider")
}
