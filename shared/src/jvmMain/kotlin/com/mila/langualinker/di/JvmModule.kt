package com.mila.langualinker.di

import app.cash.sqldelight.db.SqlDriver
import com.mila.langualinker.data.database.createSqlDriver
import com.mila.langualinker.data.settings.AppSettingsRepository
import com.mila.langualinker.data.settings.PropertiesAppSettingsRepository
import org.koin.dsl.module

val jvmModule = module {
    single<SqlDriver> { createSqlDriver() }
    single<AppSettingsRepository> { PropertiesAppSettingsRepository() }
}
