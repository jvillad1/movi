package com.jvillada.movi.shared.db

import app.cash.sqldelight.db.SqlDriver

expect fun createSqlDriver(dbName: String): SqlDriver

fun createDatabase(dbName: String): MoviDatabase = MoviDatabase(createSqlDriver(dbName))
