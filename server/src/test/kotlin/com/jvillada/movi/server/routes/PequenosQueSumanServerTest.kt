package com.jvillada.movi.server.routes

import com.jvillada.movi.server.db.Accounts
import com.jvillada.movi.server.db.Budgets
import com.jvillada.movi.server.db.Events
import com.jvillada.movi.server.db.OccurrenceRejections
import com.jvillada.movi.server.db.RecurringOccurrences
import com.jvillada.movi.server.db.RecurringRules
import com.jvillada.movi.server.db.Users
import com.jvillada.movi.server.db.VoidEvents
import com.jvillada.movi.server.time.appDateToEpochMillis
import com.jvillada.movi.shared.model.PeriodSettings
import com.jvillada.movi.shared.model.PequenosQueSuman
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDate
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * **«Pequeños que suman» en `GET /api/periodos/{id}`**: sale de los MISMOS gastos que suman «Salió»
 * (sin anulados ni «Por confirmar»), de la ventana del período y del dueño nada más.
 * Los umbrales y la agrupación los prueba `PequenosQueSumanTest` de `:core`.
 */
class PequenosQueSumanServerTest {

    private val uid = "user-pequenos"
    private val otro = "user-otro-pequenos"
    private val corte25 = PeriodSettings(cutoffDay = 25)
    private val ahora = appDateToEpochMillis(LocalDate.of(2026, 10, 2)) + 12 * 3_600_000L
    private var n = 0

    @BeforeTest
    fun setUp() {
        Database.connect(
            url = "jdbc:h2:mem:pequenos_que_suman;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
            driver = "org.h2.Driver",
        )
        transaction {
            val tablas = arrayOf(Users, Accounts, Events, VoidEvents, RecurringRules, RecurringOccurrences, OccurrenceRejections, Budgets)
            SchemaUtils.create(tables = tablas)
            SchemaUtils.drop(tables = tablas.reversedArray())
            SchemaUtils.create(tables = tablas)
            listOf(uid, otro).forEach { u ->
                Users.insert { it[id] = u; it[email] = "$u@test"; it[name] = u; it[passwordHash] = "h"; it[periodCutoffDay] = 25 }
                Accounts.insert { it[id] = "acc_$u"; it[userId] = u; it[name] = "Ahorros"; it[type] = "SAVINGS"; it[balance] = 0L }
            }
        }
    }

    private fun tinto(fecha: LocalDate, usuario: String = uid, estado: String = "RECONCILED"): String {
        val id = "ev_${n++}"
        transaction {
            Events.insert {
                it[Events.id] = id
                it[userId] = usuario
                it[accountId] = "acc_$usuario"
                it[type] = "EXPENSE"
                it[amount] = 25_000L
                it[currency] = "COP"
                it[category] = "Comida"
                it[description] = "JUAN VALDEZ CAFE"
                it[timestamp] = appDateToEpochMillis(fecha) + 12 * 3_600_000L
                it[reconciliationStatus] = estado
            }
        }
        return id
    }

    private fun anular(id: String) = transaction {
        VoidEvents.insert { it[VoidEvents.id] = "void-$id"; it[userId] = uid; it[originalEventId] = id; it[timestamp] = 0L }
    }

    private fun pequenos(): List<PequenosQueSuman> =
        transaction { detalleDePeriodo(uid, "2026-09", ahora, corte25) }!!.pequenosQueSuman

    @Test
    fun `cuenta los mismos gastos que Salio y nada mas`() {
        // Septiembre: 25-ago → 24-sep. Cinco tintos que suman.
        repeat(5) { tinto(LocalDate.of(2026, 9, 1 + it)) }
        // Lo que no suma en «Salió» tampoco suma acá.
        anular(tinto(LocalDate.of(2026, 9, 10)))
        tinto(LocalDate.of(2026, 9, 11), estado = "UNCONFIRMED")
        // Fuera de la ventana, y de otro dueño.
        tinto(LocalDate.of(2026, 9, 25))
        repeat(3) { tinto(LocalDate.of(2026, 9, 5), usuario = otro) }

        val grupo = pequenos().single()
        assertEquals("JUAN VALDEZ CAFE", grupo.nombre)
        assertEquals(5, grupo.veces)
        assertEquals(125_000L, grupo.total)
    }

    @Test
    fun `sin grupos que importen la lista viene vacia`() {
        repeat(3) { tinto(LocalDate.of(2026, 9, 1 + it)) } // $75.000: no llega a $100.000
        assertEquals(emptyList(), pequenos())
    }
}
