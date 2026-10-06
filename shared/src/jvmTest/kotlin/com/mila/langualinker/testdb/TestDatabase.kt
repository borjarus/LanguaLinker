package com.mila.langualinker.testdb

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.mila.langualinker.database.AppDatabase
import java.util.Properties

/**
 * A real SQLite database, in memory.
 *
 * The repositories are thin by design — almost all their behaviour is the SQL itself, plus
 * the row/domain mapping. Testing them against fakes would exercise neither, so these tests
 * run against the actual schema.
 */
fun createTestDatabase(): AppDatabase {
    val driver = JdbcSqliteDriver(
        url = JdbcSqliteDriver.IN_MEMORY,
        properties = Properties().apply { put("foreign_keys", "true") },
        schema = AppDatabase.Schema,
    )
    return AppDatabase(driver)
}
