package com.mila.langualinker.data.database

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.mila.langualinker.database.AppDatabase
import java.util.Properties

actual fun createSqlDriver(): SqlDriver =
    JdbcSqliteDriver(
        url = JdbcSqliteDriver.IN_MEMORY,
        properties = Properties().apply { put("foreign_keys", "true") },
        schema = AppDatabase.Schema,
    )
