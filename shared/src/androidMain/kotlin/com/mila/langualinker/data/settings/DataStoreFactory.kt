package com.mila.langualinker.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(
    name = DATASTORE_FILE_NAME
)

fun createAndroidDataStore(context: Context): DataStore<Preferences> = context.dataStore

internal const val DATASTORE_FILE_NAME = "app_settings.preferences_pb"
