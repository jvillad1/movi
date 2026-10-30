package com.jvillada.movi.server.reminders

import com.jvillada.movi.server.db.Accounts
import com.jvillada.movi.server.db.Cards
import com.jvillada.movi.server.db.Events
import com.jvillada.movi.server.db.Users
import com.jvillada.movi.server.db.VoidEvents
import com.jvillada.movi.server.time.appDateToEpochMillis
import com.jvillada.movi.shared.model.CARD_PAYMENT_CATEGORY
import com.jvillada.movi.shared.model.MinimoDelExtracto
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDate
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * **Revisión de la vuelta 72 · el mínimo de la tarjeta es del extracto, en su fecha de pago.** La regla
 * sintética de Nu (corte el 20, paga el 5, mínimo $180.000) lleva el mínimo del extracto cortado el 20-oct,
 * que vence el 5-nov, con lo pagado desde el corte: la misma cuenta que la hoja «Pagar».
 */
class ElMinimoDelExtractoEnLaReglaTest {

    private val uid = "user-minimo-extracto"
    private val nu = "acc_nu_min"
    private val hoy = LocalDate.of(2026, 10, 29)

    @BeforeTest
    fun setUp() {
        Database.connect(
            url = "jdbc:h2:mem:minimo_extracto_test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
            driver = "org.h2.Driver",
        )
        transaction {
            val tablas = arrayOf(Users, Accounts, Events, VoidEvents, Cards)
            SchemaUtils.create(tables = tablas)
            SchemaUtils.drop(tables = tablas.reversedArray())
            SchemaUtils.create(tables = tablas)
            Users.insert { it[id] = uid; it[email] = "$uid@t.test"; it[name] = uid; it[passwordHash] = "h" }
            Accounts.insert { it[id] = nu; it[userId] = uid; it[name] = "Nu"; it[type] = "CREDIT_CARD"; it[currency] = "COP"; it[balance] = 0L }
            Cards.insert {
                it[accountId] = nu; it[userId] = uid; it[bank] = "Nu"; it[cutoffDay] = 20; it[paymentDay] = 5
                it[pagoMinimo] = 180_000L
            }
        }
    }

    private fun movimiento(id: String, tipo: String, monto: Long, dia: LocalDate, categoria: String) = transaction {
        Events.insert {
            it[Events.id] = id; it[userId] = uid; it[accountId] = nu; it[type] = tipo; it[amount] = monto
            it[currency] = "COP"; it[category] = categoria; it[description] = id
            it[timestamp] = appDateToEpochMillis(dia) + 12 * 3_600_000L
            it[eventSource] = "MANUAL"; it[reconciliationStatus] = "RECONCILED"
        }
    }

    private fun minimo(): MinimoDelExtracto? = runBlocking { loadCardRulePairs(uid, hoy) }.single().first.minimoDelExtracto

    @Test
    fun `el minimo va con la fecha de pago del extracto y lo pagado desde el corte`() {
        movimiento("ev_compra", "EXPENSE", 1_583_000, LocalDate.of(2026, 10, 10), "Compras")
        movimiento("ev_pago_100", "INCOME", 100_000, LocalDate.of(2026, 10, 28), CARD_PAYMENT_CATEGORY)
        assertEquals(MinimoDelExtracto("2026-11-05", 180_000, 100_000), minimo())
        assertEquals(80_000L, minimo()!!.resto)
        // Pagado el resto: no queda nada del mínimo.
        movimiento("ev_pago_80", "INCOME", 80_000, LocalDate.of(2026, 10, 29), CARD_PAYMENT_CATEGORY)
        assertEquals(0L, minimo()!!.resto)
    }
}
