package com.jvillada.movi.server.db

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.transactions.transaction
import java.io.File

object DatabaseFactory {
    fun init() {
        val url  = readEnv("DATABASE_URL")  ?: error("DATABASE_URL not set — add it to server/.env")
        val user = readEnv("DATABASE_USER") ?: "movi"
        val pass = readEnv("DATABASE_PASSWORD") ?: "secret"

        val config = HikariConfig().apply {
            jdbcUrl         = url
            username        = user
            password        = pass
            driverClassName = "org.postgresql.Driver"
            maximumPoolSize = 10
        }
        Database.connect(HikariDataSource(config))
        transaction {
            SchemaUtils.create(Users, Accounts, Events, VoidEvents)
        }
    }

    private fun readEnv(key: String): String? {
        System.getenv(key)?.let { return it }
        val files = listOf(
            File(System.getProperty("user.dir"), "server/.env"),
            File(System.getProperty("user.dir"), ".env"),
        )
        return files.firstNotNullOfOrNull { f ->
            if (!f.exists()) null
            else f.readLines().firstOrNull { it.startsWith("$key=") }?.substringAfter("=")?.trim()
        }
    }
}

suspend fun <T> dbQuery(block: Transaction.() -> T): T =
    withContext(Dispatchers.IO) { transaction { block() } }
