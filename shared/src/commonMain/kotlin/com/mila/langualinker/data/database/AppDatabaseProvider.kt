package com.mila.langualinker.data.database

import app.cash.sqldelight.db.SqlDriver
import com.mila.langualinker.database.AppDatabase

fun createAppDatabase(driver: SqlDriver): AppDatabase = AppDatabase(driver)
