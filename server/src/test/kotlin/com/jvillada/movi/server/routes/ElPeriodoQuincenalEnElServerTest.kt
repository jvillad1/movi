package com.jvillada.movi.server.routes

import com.jvillada.movi.server.conectarBaseDePrueba
import com.jvillada.movi.server.db.Accounts
import com.jvillada.movi.server.db.AnomaliasDescartadas
import com.jvillada.movi.server.db.Apartados
import com.jvillada.movi.server.db.Budgets
import com.jvillada.movi.server.db.Cards
import com.jvillada.movi.server.db.CierresDescartados
import com.jvillada.movi.server.db.CierresEnviados
import com.jvillada.movi.server.db.CierresGuardados
import com.jvillada.movi.server.db.Credits
import com.jvillada.movi.server.db.Events
import com.jvillada.movi.server.db.KnownDestinations
import com.jvillada.movi.server.db.OccurrenceRejections
import com.jvillada.movi.server.db.RecurringOccurrences
import com.jvillada.movi.server.db.RecurringRules
import com.jvillada.movi.server.db.Subscriptions
import com.jvillada.movi.server.db.Users
import com.jvillada.movi.server.db.VoidEvents
import com.jvillada.movi.server.reminders.mandarLosCierres
import com.jvillada.movi.server.time.ajustesDelPeriodoSinSuspender
import com.jvillada.movi.server.time.appDateToEpochMillis
import com.jvillada.movi.shared.model.OPENING_CATEGORY
import com.jvillada.movi.shared.model.seMuestraEnHoy
import com.jvillada.movi.shared.model.tituloDelCierre
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDate
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * # Vuelta 105 · el período quincenal de punta a punta en el server
 *
 * Una usuaria a la que le pagan el 15 y el último día del mes: «Tus períodos» lista quincenas con su id
 * de arranque y su nombre por fechas, el detalle mide los presupuestos a la mitad (D2), y el cierre
 * habla de la quincena que se cerró, contra la misma quincena del mes anterior (D6), una sola vez por
 * correo. Mismo arnés que [CierreDelPeriodoTest].
 */
class ElPeriodoQuincenalEnElServerTest {

    private val uid = "user-quincena"
    private var n = 0

    /** 1-nov-2026: día 2 de la quincena que arrancó el sábado 31 de octubre; la del 15 acaba de cerrar. */
    private val diaDos = LocalDate.of(2026, 11, 1)
    private val ahora get() = appDateToEpochMillis(diaDos) + 12 * 3_600_000L

    @BeforeTest
    fun setUp() {
        conectarBaseDePrueba(
            url = "jdbc:h2:mem:periodo_quincenal_server;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
            driver = "org.h2.Driver",
        )
        transaction {
            val tablas = arrayOf(
                Apartados, Users, Accounts, Events, VoidEvents, Credits, Cards, RecurringRules, RecurringOccurrences,
                OccurrenceRejections, KnownDestinations, Budgets, Subscriptions, AnomaliasDescartadas,
                CierresEnviados, CierresDescartados, CierresGuardados,
            )
            SchemaUtils.create(tables = tablas)
            SchemaUtils.drop(tables = tablas.reversedArray())
            SchemaUtils.create(tables = tablas)
            Users.insert {
                it[id] = uid; it[email] = "$uid@quincena.test"; it[name] = "Lina"; it[passwordHash] = "h"
                it[periodCutoffDay] = 15
                it[periodFrequency] = "QUINCENAL"
                it[periodSecondDay] = 31
            }
            Accounts.insert { it[id] = "acc"; it[userId] = uid; it[name] = "Ahorros"; it[type] = "SAVINGS"; it[balance] = 0L }
            Budgets.insert { it[userId] = uid; it[category] = "Comida"; it[monthlyLimit] = 800_001L }
        }
        // Desde agosto: la quincena homóloga (15 – 29 sep) está cubierta.
        anotar(LocalDate.of(2026, 8, 20), 3_000_000, OPENING_CATEGORY, tipo = "INCOME")
        anotar(LocalDate.of(2026, 9, 15), 2_000_000, "Salario", tipo = "INCOME")
        anotar(LocalDate.of(2026, 9, 20), 300_000, "Comida")
        anotar(LocalDate.of(2026, 9, 30), 2_000_000, "Salario", tipo = "INCOME")
        anotar(LocalDate.of(2026, 10, 5), 1_200_000, "Vivienda")
        // La quincena del 15 al 30 de octubre.
        anotar(LocalDate.of(2026, 10, 15), 2_000_000, "Salario", tipo = "INCOME")
        anotar(LocalDate.of(2026, 10, 18), 450_000, "Comida")
        anotar(LocalDate.of(2026, 10, 30), 50_000, "Transporte")
        // La del 31 (en curso).
        anotar(LocalDate.of(2026, 10, 31), 2_000_000, "Salario", tipo = "INCOME")
    }

    private fun anotar(fecha: LocalDate, monto: Long, categoria: String, tipo: String = "EXPENSE") {
        val id = "ev_${n++}"
        transaction {
            Events.insert {
                it[Events.id] = id
                it[userId] = uid
                it[accountId] = "acc"
                it[type] = tipo
                it[amount] = monto
                it[currency] = "COP"
                it[category] = categoria
                it[description] = categoria
                it[timestamp] = appDateToEpochMillis(fecha) + 12 * 3_600_000L
                it[reconciliationStatus] = "RECONCILED"
            }
        }
    }

    @Test
    fun `tus periodos son quincenas, contiguas y nombradas por fechas`() {
        val ajustes = transaction { ajustesDelPeriodoSinSuspender(uid) }
        assertTrue(ajustes.esQuincenal)
        val lista = transaction { resumenesDePeriodos(uid, ahora, ajustes) }
        assertEquals(listOf("2026-10-31", "2026-10-15", "2026-09-30", "2026-09-15", "2026-08-31", "2026-08-15"), lista.map { it.id })
        val octubre = lista.first { it.id == "2026-10-15" }
        assertEquals("15 – 30 oct 2026", octubre.nombre)
        assertEquals("2026-10-15", octubre.desde)
        assertEquals("2026-10-30", octubre.hasta)
        assertEquals(2_000_000, octubre.entradas)
        assertEquals(500_000, octubre.salidas)
        // Contiguas: cada una termina el día antes de que arranque la siguiente.
        lista.zipWithNext().forEach { (nueva, vieja) ->
            assertEquals(LocalDate.parse(vieja.hasta).plusDays(1), LocalDate.parse(nueva.desde))
        }
    }

    @Test
    fun `el detalle acepta el id de la quincena y no uno de mes, y mide el presupuesto a la mitad`() {
        val ajustes = transaction { ajustesDelPeriodoSinSuspender(uid) }
        assertNull(transaction { detalleDePeriodo(uid, "2026-10", ahora, ajustes) }, "un id de mes no es una quincena")
        assertNull(transaction { detalleDePeriodo(uid, "2026-10-20", ahora, ajustes) }, "ni un día que no arranca ninguna")
        val detalle = assertNotNull(transaction { detalleDePeriodo(uid, "2026-10-15", ahora, ajustes) })
        val comida = detalle.presupuestos.single()
        assertEquals(400_001, comida.limite, "la quincena del día A se lleva el peso impar")
        val laOtra = assertNotNull(transaction { detalleDePeriodo(uid, "2026-10-31", ahora, ajustes) })
        assertEquals(400_000, laOtra.presupuestos.single().limite)
    }

    @Test
    fun `el cierre es de la quincena, contra la misma quincena del mes anterior`() = runBlocking<Unit> {
        val c = assertNotNull(cierreDelPeriodoDe(uid, diaDos))
        assertEquals("2026-10-15", c.periodo)
        assertEquals(2, c.diaDelPeriodoNuevo)
        assertEquals("Cerró tu quincena del 15 – 30 oct", tituloDelCierre(c))
        assertEquals(2_000_000, c.entro)
        assertEquals(500_000, c.salio)
        // D6: la homóloga (15 – 29 sep), no la inmediata (30 sep – 14 oct, la del arriendo).
        assertEquals("2026-09-15", c.anterior?.periodo)
        assertEquals(300_000, c.anterior?.salio)
        assertTrue(seMuestraEnHoy(c))
        // En una quincena la tarjeta dura 3 días, no 5.
        assertNull(cierreDelPeriodoDe(uid, LocalDate.of(2026, 11, 4), soloSiSeMuestra = true))
        assertFalse(seMuestraEnHoy(c.copy(diaDelPeriodoNuevo = 4)))
    }

    @Test
    fun `el correo del cierre sale una vez por quincena, con su clave de 10`() = runBlocking<Unit> {
        val enviados = mutableListOf<String>()
        val enviar: suspend (String, String, String) -> Boolean = { _, asunto, _ -> enviados += asunto; true }
        assertEquals(1, mandarLosCierres(diaDos, "re_clave", enviar))
        assertEquals(0, mandarLosCierres(diaDos.plusDays(1), "re_clave", enviar))
        assertEquals(listOf("Cerró tu quincena del 15 – 30 oct"), enviados)
        assertEquals("2026-10-15", transaction { CierresEnviados.selectAll().single()[CierresEnviados.periodo] })
    }
}
