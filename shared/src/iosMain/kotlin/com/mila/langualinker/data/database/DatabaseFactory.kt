package com.mila.langualinker.data.database

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import com.mila.langualinker.database.AppDatabase

actual fun createSqlDriver(): SqlDriver =
    NativeSqliteDriver(AppDatabase.Schema, "langualinker.db")
