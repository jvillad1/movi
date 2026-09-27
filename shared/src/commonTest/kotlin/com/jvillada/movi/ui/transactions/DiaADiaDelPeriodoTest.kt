package com.jvillada.movi.ui.transactions

import com.jvillada.movi.shared.model.FinanceSummary
import com.jvillada.movi.shared.model.OccurrenceState
import com.jvillada.movi.shared.model.PaymentStatus
import com.jvillada.movi.shared.model.PeriodSettings
import com.jvillada.movi.shared.model.PeriodoFinanciero
import com.jvillada.movi.shared.model.RecurringRule
import com.jvillada.movi.shared.model.Scope
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.UpcomingPayment
import com.jvillada.movi.ui.dashboard.DashboardData
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * **De dónde sale la meta del «Día a día»**: la misma de la tarjeta «Disponible» (ver
 * `disponibleDelPeriodo`), con el día fijo en lunes 21 de septiembre de 2026 y el período del 25 de
 * agosto al 24 de septiembre (31 días).
 */
class DiaADiaDelPeriodoTest {

    private val hoy = LocalDate(2026, 9, 21)

    private fun datos(ingresos: Long, arriendo: Long, gasto: Map<String, Long>) = DashboardData(
        summary = FinanceSummary(scope = Scope.SELF, balance = 0, ingresos = ingresos, egresos = 0),
        upcoming = listOf(
            UpcomingPayment(
                rule = RecurringRule(
                    id = "rr_arriendo", name = "Arriendo", category = "Vivienda", amount = arriendo,
                    dayOfMonth = 5, type = TransactionType.EXPENSE,
                ),
                dueDate = "2026-10-05",
                daysUntil = 14,
                status = PaymentStatus.UPCOMING,
            ),
        ),
        ocurrencias = listOf(
            OccurrenceState(ruleId = "rr_arriendo", period = "2026-09", dueDate = "2026-09-05", occurred = true),
        ),
        gastoVariablePorDia = gasto,
        ajustesDePeriodo = PeriodSettings(cutoffDay = 25),
        periodoActual = PeriodoFinanciero(2026, 9),
    )

    @Test
    fun `la meta es la del disponible entre los dias del periodo`() {
        // $10M menos $3,1M de arriendo = $6,9M entre 31 días = $222.580 al día.
        val dia = assertNotNull(
            diaADiaDe(datos(10_000_000, 3_100_000, mapOf("2026-09-20" to 300_000L, "2026-09-19" to 50_000L)), hoy),
        )
        assertEquals("Día a día: \$300.000 de \$222.580 · te pasaste \$77.420", dia.linea("2026-09-20")?.texto)
        assertEquals("Día a día: \$50.000 de \$222.580", dia.linea("2026-09-19")?.texto)
        assertEquals("Día a día: \$0 de \$222.580", dia.linea("2026-09-18")?.texto, "un día sin gasto también dice")
    }

    @Test
    fun `hoy tiene linea, manana no, y tampoco el periodo anterior`() {
        val dia = assertNotNull(diaADiaDe(datos(10_000_000, 3_100_000, mapOf("2026-09-21" to 10_000L)), hoy))
        assertTrue(dia.aplicaA("2026-09-21"))
        assertFalse(dia.aplicaA("2026-09-22"), "los días futuros no tienen qué comparar")
        assertNull(dia.linea("2026-09-22"))
        assertTrue(dia.aplicaA("2026-08-25"), "el primer día del período")
        assertFalse(dia.aplicaA("2026-08-24"), "el período cerrado no se puede reconstruir")
        assertNull(dia.linea("2026-08-24"))
        assertFalse(dia.aplicaA("basura"))
    }

    @Test
    fun `sin margen no hay meta y no se dibuja nada`() {
        assertNull(diaADiaDe(datos(2_000_000, 3_100_000, emptyMap()), hoy))
    }

    @Test
    fun `sin el gasto o sin los vencimientos no se afirma nada`() {
        val completo = datos(10_000_000, 3_100_000, emptyMap())
        assertNull(diaADiaDe(completo.copy(gastoVariablePorDia = null), hoy))
        assertNull(diaADiaDe(completo.copy(upcoming = null), hoy))
        assertNull(diaADiaDe(completo.copy(ocurrencias = null), hoy))
        assertNull(diaADiaDe(completo.copy(summary = null), hoy))
    }

    @Test
    fun `fuera del periodo en curso no hay dia a dia`() {
        assertNull(diaADiaDe(datos(10_000_000, 3_100_000, emptyMap()), LocalDate(2026, 10, 2)))
    }
}
