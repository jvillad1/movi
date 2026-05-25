package com.jvillada.movi.shared.db

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver

actual fun createSqlDriver(dbName: String): SqlDriver =
    NativeSqliteDriver(MoviDatabase.Schema, dbName)
