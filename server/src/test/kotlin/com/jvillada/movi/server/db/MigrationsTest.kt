package com.jvillada.movi.server.db

import com.jvillada.movi.server.db.Migrations.renameLegacyNewSmsStateToPending
import com.jvillada.movi.server.db.Migrations.restampStatementEventsToBogota
import com.jvillada.movi.shared.model.SMS_STATE_CONFIRMED
import com.jvillada.movi.shared.model.SMS_STATE_IGNORED
import com.jvillada.movi.shared.model.SMS_STATE_PENDING
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Migraciones de datos: los eventos de extracto sellados a 00:00Z (que con la fecha civil en
 * Bogotá retrocedían al día anterior) y los SMS que quedaron con el nombre viejo del estado
 * pendiente. Las dos tienen que ser idempotentes: correrlas dos veces no cambia nada la segunda.
 */
class MigrationsTest {

    private val midnightUtc = Instant.parse("2026-08-01T00:00:00Z").toEpochMilli()
    private val fiveHours = 5L * 3_600_000L

    @BeforeTest
    fun setUp() {
        Database.connect(
            url    = "jdbc:h2:mem:migrations_test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
            driver = "org.h2.Driver",
        )
        transaction {
            SchemaUtils.drop(Events, StatementImports, Accounts, SmsMessages, Users)
            SchemaUtils.create(Users, Accounts, StatementImports, Events, SmsMessages)
            Users.insert {
                it[id] = "u1"; it[email] = "u1@test"; it[name] = "U"; it[passwordHash] = "h"
            }
            Accounts.insert {
                it[id] = "acc1"; it[userId] = "u1"; it[name] = "Ahorros"; it[type] = "SAVINGS"; it[currency] = "COP"
            }
            seed("ev-statement", "STATEMENT", midnightUtc)
            seed("ev-sms-midnight", "SMS", midnightUtc)
            seed("ev-statement-noon", "STATEMENT", midnightUtc + 12L * 3_600_000L)
            seedSms("sms-legacy-1", "new")
            seedSms("sms-legacy-2", "new")
            seedSms("sms-pending", SMS_STATE_PENDING)
            seedSms("sms-confirmed", SMS_STATE_CONFIRMED)
            seedSms("sms-ignored", SMS_STATE_IGNORED)
        }
    }

    private fun seedSms(id: String, state: String) {
        SmsMessages.insert {
            it[SmsMessages.id]     = id
            it[SmsMessages.userId] = "u1"
            it[time]               = "2026-08-01 10:00"
            it[bank]               = "Bancolombia"
            it[text]               = id
            it[SmsMessages.state]  = state
            it[det]                = ""
        }
    }

    private fun smsStateOf(id: String): String = transaction {
        SmsMessages.selectAll().where { SmsMessages.id eq id }.single()[SmsMessages.state]
    }

    private fun seed(id: String, source: String, ts: Long) {
        Events.insert {
            it[Events.id] = id
            it[userId] = "u1"
            it[accountId] = "acc1"
            it[type] = "EXPENSE"
            it[amount] = 1000L
            it[currency] = "COP"
            it[category] = "Comida"
            it[description] = id
            it[timestamp] = ts
            it[eventSource] = source
            it[reconciliationStatus] = "UNCONFIRMED"
        }
    }

    private fun timestampOf(id: String): Long = transaction {
        Events.selectAll().where { Events.id eq id }.single()[Events.timestamp]
    }

    @Test
    fun `corre los eventos STATEMENT de 00 00Z a medianoche de Bogota, una sola vez`() {
        val first = transaction { restampStatementEventsToBogota() }
        assertEquals(1, first)
        assertEquals(midnightUtc + fiveHours, timestampOf("ev-statement"))

        val second = transaction { restampStatementEventsToBogota() }
        assertEquals(0, second)
        assertEquals(midnightUtc + fiveHours, timestampOf("ev-statement"))
    }

    @Test
    fun `renombra el estado viejo new a pending, una sola vez`() {
        val first = transaction { renameLegacyNewSmsStateToPending() }
        assertEquals(2, first, "las dos filas con el nombre viejo")
        assertEquals(SMS_STATE_PENDING, smsStateOf("sms-legacy-1"))
        assertEquals(SMS_STATE_PENDING, smsStateOf("sms-legacy-2"))

        // Segunda corrida: ya no queda ninguna fila en el nombre viejo.
        val second = transaction { renameLegacyNewSmsStateToPending() }
        assertEquals(0, second)
        assertEquals(SMS_STATE_PENDING, smsStateOf("sms-legacy-1"))
    }

    @Test
    fun `no toca lo que el dueno ya resolvio ni lo que ya estaba en pending`() {
        transaction { renameLegacyNewSmsStateToPending() }
        assertEquals(SMS_STATE_PENDING, smsStateOf("sms-pending"))
        assertEquals(SMS_STATE_CONFIRMED, smsStateOf("sms-confirmed"))
        assertEquals(SMS_STATE_IGNORED, smsStateOf("sms-ignored"))
    }

    @Test
    fun `no toca un SMS a 00 00Z exacto ni un STATEMENT que no esta en 00 00Z`() {
        transaction { restampStatementEventsToBogota() }
        assertEquals(midnightUtc, timestampOf("ev-sms-midnight"))
        assertEquals(midnightUtc + 12L * 3_600_000L, timestampOf("ev-statement-noon"))
    }
}
