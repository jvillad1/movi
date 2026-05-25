package com.jvillada.movi.shared.db

import android.content.Context
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import app.cash.sqldelight.db.SqlDriver

private lateinit var appContext: Context

object DatabaseDriverFactory {
    fun init(context: Context) { appContext = context }
}

actual fun createSqlDriver(dbName: String): SqlDriver =
    AndroidSqliteDriver(MoviDatabase.Schema, appContext, dbName)
