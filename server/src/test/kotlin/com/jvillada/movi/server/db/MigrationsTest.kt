package com.jvillada.movi.server.db

import com.jvillada.movi.server.db.Migrations.createUniqueTransferLegIndex
import com.jvillada.movi.server.db.Migrations.duplicateTransferLegs
import com.jvillada.movi.server.db.Migrations.renameLegacyNewSmsStateToPending
import com.jvillada.movi.server.db.Migrations.restampStatementEventsToBogota
import com.jvillada.movi.shared.model.SMS_STATE_CONFIRMED
import com.jvillada.movi.shared.model.SMS_STATE_IGNORED
import com.jvillada.movi.shared.model.SMS_STATE_PENDING
import org.jetbrains.exposed.exceptions.ExposedSQLException
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

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

    // ── El índice único de las patas de traspaso ──────────────────────────────

    /** Una pata: mismo molde que [seed] pero con `transfer_id` y el lado que se le pida. */
    private fun seedLeg(id: String, transferId: String, typeValue: String, uid: String = "u1") {
        Events.insert {
            it[Events.id] = id
            it[userId] = uid
            it[accountId] = "acc1"
            it[type] = typeValue
            it[amount] = 250_000L
            it[currency] = "COP"
            it[category] = "Traspaso"
            it[description] = id
            it[timestamp] = midnightUtc
            it[eventSource] = "MANUAL"
            it[reconciliationStatus] = "RECONCILED"
            it[Events.transferId] = transferId
        }
    }

    @Test
    fun `crea el indice unico de patas y aguanta correrse dos veces`() {
        assertTrue(transaction { createUniqueTransferLegIndex() })
        assertTrue(transaction { createUniqueTransferLegIndex() }, "idempotente: la segunda no rompe")
    }

    /** El traspaso normal —una salida y una entrada— sigue entrando sin quejas. */
    @Test
    fun `las dos patas de un traspaso conviven bajo el indice`() {
        transaction { createUniqueTransferLegIndex() }
        transaction {
            seedLeg("ev-from", "tr-1", "EXPENSE")
            seedLeg("ev-to", "tr-1", "INCOME")
        }
        assertEquals(2, transaction { Events.selectAll().where { Events.transferId eq "tr-1" }.count().toInt() })
    }

    /** Lo que T3 vino a impedir: un tercer evento colgado del mismo traspaso. */
    @Test
    fun `una tercera pata del mismo lado no entra`() {
        transaction { createUniqueTransferLegIndex() }
        transaction {
            seedLeg("ev-from", "tr-1", "EXPENSE")
            seedLeg("ev-to", "tr-1", "INCOME")
        }
        assertFailsWith<ExposedSQLException> {
            transaction { seedLeg("ev-from-otra-vez", "tr-1", "EXPENSE") }
        }
    }

    /**
     * La suposición riesgosa de este índice, fijada acá: la columna es NULL en TODOS los eventos
     * que no son traspasos, y una clave con algún NULL no choca con ninguna otra (Postgres y H2
     * se comportan igual). Sin esto, el índice habría rechazado el segundo gasto del mes.
     */
    @Test
    fun `los eventos normales, con transfer_id en NULL, no se estorban entre si`() {
        transaction { createUniqueTransferLegIndex() }
        transaction {
            seed("ev-mercado", "MANUAL", midnightUtc)
            seed("ev-taxi", "MANUAL", midnightUtc)
        }
        assertEquals(2, transaction { Events.selectAll().where { Events.id inList listOf("ev-mercado", "ev-taxi") }.count().toInt() })
    }

    /** Cada usuario con su propio espacio de ids: el de al lado no lo puede bloquear. */
    @Test
    fun `dos usuarios pueden tener el mismo transferId`() {
        transaction {
            Users.insert { it[id] = "u2"; it[email] = "u2@test"; it[name] = "U2"; it[passwordHash] = "h" }
            createUniqueTransferLegIndex()
            seedLeg("ev-u1", "tr-igual", "EXPENSE", uid = "u1")
            seedLeg("ev-u2", "tr-igual", "EXPENSE", uid = "u2")
        }
        assertEquals(2, transaction { Events.selectAll().where { Events.transferId eq "tr-igual" }.count().toInt() })
    }

    /**
     * El escenario que no puede tumbar el arranque: la base ya trae datos que violan la regla.
     * La migración **no crea el índice y devuelve false** en vez de reventar la transacción del
     * esquema y dejar el server sin levantar.
     */
    @Test
    fun `con datos ya en conflicto no se crea el indice, pero el arranque sigue`() {
        transaction {
            seedLeg("ev-a", "tr-rota", "EXPENSE")
            seedLeg("ev-b", "tr-rota", "EXPENSE")
        }
        assertEquals(listOf("tr-rota"), transaction { duplicateTransferLegs() })
        assertFalse(transaction { createUniqueTransferLegIndex() })
        // Y el resto de las migraciones no se entera: siguen corriendo normalmente.
        assertEquals(1, transaction { restampStatementEventsToBogota() })
    }
}
