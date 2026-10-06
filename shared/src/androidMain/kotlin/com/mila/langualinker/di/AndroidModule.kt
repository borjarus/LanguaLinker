package com.mila.langualinker.di

import android.content.Context
import app.cash.sqldelight.db.SqlDriver
import com.mila.langualinker.data.database.createAndroidSqlDriver
import com.mila.langualinker.data.settings.AppSettingsRepository
import com.mila.langualinker.data.settings.AppSettingsRepositoryImpl
import com.mila.langualinker.data.settings.createAndroidDataStore
import org.koin.dsl.module

fun androidModule(context: Context) = module {
    single<SqlDriver> { createAndroidSqlDriver(context) }
    single<AppSettingsRepository> { AppSettingsRepositoryImpl(createAndroidDataStore(context)) }
}
