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
            // CategoryPrefs (Ola 10) es tabla NUEVA: entra por acá y no por
            // createMissingTablesAndColumns — un CREATE TABLE IF NOT EXISTS sobre una base que no
            // la tiene no puede fallar, y sobre una que ya la tiene no emite nada.
            // RecurringOccurrences («ya ocurrió», Ola 11) es tabla NUEVA por el mismo motivo:
            // su clave primaria compuesta viaja dentro del CREATE TABLE, así que no queda ningún
            // CREATE INDEX suelto que pueda fallar sobre una base con datos.
            // Documents (Ola 18) es tabla NUEVA y entra por acá por el mismo motivo que las dos
            // de arriba: su único índice es sobre `user_id` y viaja dentro del CREATE TABLE, así
            // que no queda ningún CREATE INDEX suelto que pueda fallar sobre una base con datos
            // — y un CREATE INDEX que falla acá deja el server sin arrancar, porque todo esto
            // corre DENTRO de la transacción de arranque.
            SchemaUtils.create(Users, Accounts, StatementImports, Events, VoidEvents, Budgets, RecurringRules, RecurringOccurrences, SmsMessages, Credits, Cards, Subscriptions, PushSubscriptions, Screens, PasswordResetTokens, CardPaymentDismissals, Goals, CategoryPrefs, Documents)
            // Screens: `seed_version` (Ola 4) — sin esta columna una instalación ya desplegada
            // no podría recibir la generación nueva del Inicio.
            // Users: `avatar_color` (F42 · F46) — mismo motivo, columna nueva en tabla vieja.
            // RecurringRules · Credits · Cards: `remind_me` — la casilla «Recordarme unos días
            // antes». La columna se declara `.default(true)`, así que el ALTER que emite esta
            // llamada deja en TRUE las filas que ya existían: quien ya recibía recordatorios
            // los sigue recibiendo, sin migración manual.
            // RecurringRules: `account_id` (Ola 9 · D) — a qué cuenta entra o de cuál sale el
            // pago. Es NULLABLE, así que el ALTER que emite esta llamada no puede fallar sobre
            // una tabla con datos y las reglas que ya existen quedan en NULL, que es la verdad:
            // nacieron sin cuenta porque el campo no existía. Idempotente por construcción —
            // la segunda vez la columna ya está y no se emite nada.
            SchemaUtils.createMissingTablesAndColumns(Events, RecurringRules, Screens, Users, Credits, Cards)
            // Migraciones de datos (idempotentes), después del schema — ver Migrations.kt.
            with(Migrations) { runAll() }
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
