package com.jvillada.movi.server.db

import com.jvillada.movi.server.screens.seedScreens
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
        val rawUrl = readEnv("DATABASE_URL") ?: error("DATABASE_URL not set — add it to server/.env")
        val (dbUrl, user, pass) = parseDbUrl(rawUrl)

        val config = HikariConfig().apply {
            jdbcUrl         = dbUrl
            username        = user
            password        = pass
            driverClassName = "org.postgresql.Driver"
            maximumPoolSize = 10
        }
        Database.connect(HikariDataSource(config))
        transaction {
            // SchemaUtils.create emite CREATE TABLE IF NOT EXISTS: una tabla nueva se crea sola
            // al arrancar y las existentes quedan intactas. No hay archivos de migración en este
            // proyecto; createMissingTablesAndColumns está abajo y solo cubre COLUMNAS nuevas de
            // las tablas que las tuvieron. Una tabla nueva basta con agregarla acá.
            SchemaUtils.create(Users, Accounts, StatementImports, Events, VoidEvents, Budgets, RecurringRules, SmsMessages, Credits, Subscriptions, PushSubscriptions, Screens, PasswordResetTokens, CardPaymentDismissals)
            // Screens: `seed_version` (Ola 4) — sin esta columna una instalación ya desplegada
            // no podría recibir la generación nueva del Inicio.
            SchemaUtils.createMissingTablesAndColumns(Events, RecurringRules, Screens)
        }
        seedScreens()
    }

    // Extracts credentials from postgres:// or postgresql:// URLs and returns
    // (jdbcUrl without credentials, user, password) so JDBC gets them separately.
    private fun parseDbUrl(raw: String): Triple<String, String, String> {
        val isCloudUrl = raw.startsWith("postgres://") || raw.startsWith("postgresql://")
        if (!isCloudUrl) {
            return Triple(raw, readEnv("DATABASE_USER") ?: "movi", readEnv("DATABASE_PASSWORD") ?: "secret")
        }
        val withoutScheme = raw.removePrefix("postgres://").removePrefix("postgresql://")
        val atIdx = withoutScheme.indexOf('@')
        val userInfo = withoutScheme.substring(0, atIdx)
        val hostAndDb = withoutScheme.substring(atIdx + 1)
        val colonIdx = userInfo.indexOf(':')
        val user = if (colonIdx >= 0) userInfo.substring(0, colonIdx) else userInfo
        val pass = if (colonIdx >= 0) userInfo.substring(colonIdx + 1) else ""
        return Triple("jdbc:postgresql://$hostAndDb", user, pass)
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
