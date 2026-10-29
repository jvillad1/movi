package com.jvillada.movi.avisos

import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.EventDay
import com.jvillada.movi.shared.model.FinanceSummary
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.OccurrenceState
import com.jvillada.movi.shared.model.PaymentStatus
import com.jvillada.movi.shared.model.PeriodSettings
import com.jvillada.movi.shared.model.PeriodoFinanciero
import com.jvillada.movi.shared.model.RecurringRule
import com.jvillada.movi.shared.model.ReconciliationStatus
import com.jvillada.movi.shared.model.Scope
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.UpcomingPayment
import com.jvillada.movi.shared.time.AppTimeZone
import com.jvillada.movi.ui.Screen
import com.jvillada.movi.ui.components.PLATA_OCULTA
import com.jvillada.movi.ui.dashboard.DashboardData
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * **El resumen de la mañana** (vuelta 68), decidido sin red: qué dice, cuándo no sale y su huella.
 *
 * El escenario es el de `TeQuedanEnHoyTest`: lunes 21 de septiembre de 2026 a las 8:00 de Bogotá,
 * período del 25 de agosto al 24 de septiembre (quedan 4 días), $10M de ingresos y $3,1M de arriendo
 * pagado: con $100.000 gastados, «te quedan $6,8M para 4 días».
 */
class ResumenDeLaMananaTest {

    private fun a(dia: Int, hora: Int, mes: Int = 9): Long =
        LocalDateTime(2026, mes, dia, hora, 0).toInstant(AppTimeZone.zone).toEpochMilliseconds()

    private val ahora = a(21, 8)

    private val nu = Account(id = "nu", name = "Nu", type = AccountType.CHECKING, balance = 0)

    private fun datos(pendientes: Int = 0, movimientos: Int = 0) = DashboardData(
        summary = FinanceSummary(scope = Scope.SELF, balance = 0, ingresos = 10_000_000, egresos = 0),
        upcoming = listOf(
            UpcomingPayment(
                rule = RecurringRule(
                    id = "rr_arriendo", name = "Arriendo", category = "Vivienda", amount = 3_100_000,
                    dayOfMonth = 5, type = TransactionType.EXPENSE,
                ),
                dueDate = "2026-10-05",
                daysUntil = 14,
                status = PaymentStatus.UPCOMING,
            ),
        ),
        ocurrencias = listOf(OccurrenceState(ruleId = "rr_arriendo", period = "2026-09", dueDate = "2026-09-05", occurred = true)),
        gastoVariablePorDia = mapOf("2026-08-26" to 100_000L),
        ajustesDePeriodo = PeriodSettings(cutoffDay = 25),
        periodoActual = PeriodoFinanciero(2026, 9),
        accounts = listOf(nu),
        pendingSms = pendientes,
        movimientosPorConfirmar = movimientos,
    )

    private fun gasto(id: String, monto: Long, dia: Int, hora: Int = 12) = FinancialEvent(
        id = id, accountId = "nu", type = TransactionType.EXPENSE, amount = monto, category = "Comida",
        description = "Almuerzo", timestamp = a(dia, hora), reconciliationStatus = ReconciliationStatus.RECONCILED,
    )

    private val tipos = mapOf("nu" to AccountType.CHECKING)

    // ── Qué dice ──────────────────────────────────────────────────────────────

    @Test
    fun `dice lo de ayer, lo que queda y lo pendiente`() {
        val r = assertNotNull(resumenDeLaManana(datos(pendientes = 2), leidaEn = ahora, gastoDeAyer = 85_000, ahora = ahora, yaAvisado = null, ocultos = false))
        assertEquals("Ayer gastaste \$85.000", r.texto.titulo)
        assertEquals("Te quedan \$6,8M para 4 días · 2 avisos por confirmar", r.texto.texto)
        assertEquals("manana:2026-09-21", r.huella)
    }

    @Test
    fun `con otras cosas pendientes dice cuantas por revisar`() {
        val r = assertNotNull(resumenDeLaManana(datos(pendientes = 2, movimientos = 1), ahora, 85_000, ahora, null, false))
        assertEquals("Te quedan \$6,8M para 4 días · 3 por revisar", r.texto.texto)
    }

    @Test
    fun `sin el gasto de ayer arranca con lo que queda`() {
        val r = assertNotNull(resumenDeLaManana(datos(), ahora, gastoDeAyer = null, ahora = ahora, yaAvisado = null, ocultos = false))
        assertEquals("Te quedan \$6,8M para 4 días", r.texto.titulo)
        assertEquals(TOCA_PARA_VER_TU_INICIO, r.texto.texto)
    }

    @Test
    fun `ayer sin gastos lo dice`() {
        val r = assertNotNull(resumenDeLaManana(datos(), ahora, 0, ahora, null, false))
        assertEquals("Ayer no anotaste gastos", r.texto.titulo)
    }

    @Test
    fun `con los saldos ocultos ninguna cifra sale en claro`() {
        val r = assertNotNull(resumenDeLaManana(datos(pendientes = 1), ahora, 85_000, ahora, null, ocultos = true))
        assertEquals("Ayer gastaste $PLATA_OCULTA", r.texto.titulo)
        assertEquals("Te quedan $PLATA_OCULTA para 4 días · 1 aviso por confirmar", r.texto.texto)
        assertFalse("85.000" in r.texto.titulo + r.texto.texto)
    }

    // ── Cuándo no sale ────────────────────────────────────────────────────────

    @Test
    fun `sin cifras no sale`() {
        assertNull(resumenDeLaManana(null, ahora, 85_000, ahora, null, false))
    }

    @Test
    fun `con cifras de ayer no sale`() {
        // La instantánea es de anoche: no se dice como si fuera de hoy (el Worker pide las nuevas antes).
        assertNull(resumenDeLaManana(datos(pendientes = 2), leidaEn = a(20, 21), gastoDeAyer = 85_000, ahora = ahora, yaAvisado = null, ocultos = false))
        assertNull(resumenDeLaManana(datos(pendientes = 2), leidaEn = null, gastoDeAyer = 85_000, ahora = ahora, yaAvisado = null, ocultos = false))
        assertTrue(esDeHoy(a(21, 1), ahora))
        assertFalse(esDeHoy(a(20, 23), ahora))
    }

    @Test
    fun `sin nada util que decir no sale`() {
        // Sin disponible que afirmar (faltan las ocurrencias), sin gasto de ayer y sin pendientes.
        assertNull(resumenDeLaManana(datos().copy(ocurrencias = null), ahora, null, ahora, null, false))
    }

    @Test
    fun `la misma huella no suena dos veces el mismo dia`() {
        val huella = huellaDelResumenDeLaManana(ahora)
        assertNull(resumenDeLaManana(datos(pendientes = 2), ahora, 85_000, ahora, yaAvisado = huella, ocultos = false))
        // Otro día sí: la huella de ayer no lo calla.
        val manana = a(22, 8)
        assertNotNull(resumenDeLaManana(datos(pendientes = 2), manana, 85_000, manana, yaAvisado = huella, ocultos = false))
        assertEquals("manana:2026-09-22", huellaDelResumenDeLaManana(manana))
    }

    // ── Lo gastado ayer, con la regla de «Gastos» ─────────────────────────────

    @Test
    fun `el gasto de ayer es el Gastaste del dia en Actividad`() {
        val dias = listOf(
            EventDay("2026-09-21", 0, listOf(gasto("hoy", 10_000, 21, 7))),
            EventDay("2026-09-20", 0, listOf(gasto("a", 60_000, 20), gasto("b", 25_000, 20, 18))),
            EventDay("2026-09-18", 0, listOf(gasto("c", 5_000, 18))),
        )
        assertEquals(85_000, gastoDeAyer(dias, emptyList(), ahora, tipos))
    }

    @Test
    fun `sin movimientos ayer y con un dia mas viejo es cero`() {
        val dias = listOf(
            EventDay("2026-09-21", 0, listOf(gasto("hoy", 10_000, 21, 7))),
            EventDay("2026-09-18", 0, listOf(gasto("c", 5_000, 18))),
        )
        assertEquals(0, gastoDeAyer(dias, emptyList(), ahora, tipos))
    }

    @Test
    fun `si la lista no llega hasta ayer no se sabe`() {
        val dias = listOf(EventDay("2026-09-21", 0, listOf(gasto("hoy", 10_000, 21, 7))))
        assertNull(gastoDeAyer(dias, emptyList(), ahora, tipos))
        assertNull(gastoDeAyer(null, emptyList(), ahora, tipos))
    }

    @Test
    fun `tocarlo abre el Inicio`() {
        assertEquals(Screen.Dashboard, destinoDeAviso(ABRIR_INICIO))
    }
}
