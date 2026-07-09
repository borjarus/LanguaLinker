package com.mila.langualinker.data.database

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.worker.WebWorkerDriver
import org.w3c.dom.Worker

actual fun createSqlDriver(): SqlDriver =
    WebWorkerDriver(Worker(js("new URL('./worker.js', import.meta.url)")))
