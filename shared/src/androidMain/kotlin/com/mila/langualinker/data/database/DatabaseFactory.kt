package com.mila.langualinker.data.database

import android.content.Context
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.mila.langualinker.database.AppDatabase

actual fun createSqlDriver(): SqlDriver {
    throw IllegalStateException("Use createAndroidSqlDriver(context) instead")
}

fun createAndroidSqlDriver(context: Context): SqlDriver =
    AndroidSqliteDriver(AppDatabase.Schema, context, "langualinker.db")
