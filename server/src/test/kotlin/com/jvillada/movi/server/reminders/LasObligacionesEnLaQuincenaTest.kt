package com.jvillada.movi.server.reminders

import com.jvillada.movi.shared.model.FrecuenciaDelPeriodo
import com.jvillada.movi.shared.model.PeriodSettings
import com.jvillada.movi.shared.model.RecurringRule
import com.jvillada.movi.shared.model.SiElCorteCaeEnFinDeSemana
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.mesDeReferencia
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * # Vuelta 105 · las obligaciones mensuales con el período por quincenas
 *
 * Los recurrentes, las cuotas y las tarjetas siguen siendo mensuales: se cuentan por el **mes de
 * referencia** (`mesDeReferencia`, diseño §2.6), y lo único que cambia es en qué ventana se ven. Sin
 * eso, un pago vencido desaparecía al cambiar de quincena (el «gimnasio del día 5» que ya se cerró una
 * vez con el mes).
 */
class LasObligacionesEnLaQuincenaTest {

    private fun regla(dia: Int) = RecurringRule("r$dia", "Pago", "Otros", 100_000, dia, TransactionType.EXPENSE)

    private val unoYDieciseis = PeriodSettings(cutoffDay = 1, frecuencia = FrecuenciaDelPeriodo.QUINCENAL, segundoDia = 16)
    private val quinceYUltimo = PeriodSettings(
        cutoffDay = 15,
        frecuencia = FrecuenciaDelPeriodo.QUINCENAL,
        segundoDia = 31,
        siCaeEnFinDeSemana = SiElCorteCaeEnFinDeSemana.DIA_HABIL_ANTERIOR,
    )

    @Test
    fun `el motor de obligaciones da lo mismo que con el mes de referencia, cada dia de 2026`() {
        for (q in listOf(unoYDieciseis, quinceYUltimo)) {
            val ref = q.mesDeReferencia()
            var hoy = LocalDate.of(2026, 1, 1)
            while (hoy.year == 2026) {
                for (dia in listOf(1, 5, 15, 16, 20, 28, 31)) {
                    val r = regla(dia)
                    assertEquals(dueDateFor(r, hoy, settings = ref), dueDateFor(r, hoy, settings = q), "dueDateFor($dia, $hoy)")
                    assertEquals(ocurrenciaPorPreguntar(hoy, r, ref), ocurrenciaPorPreguntar(hoy, r, q), "ocurrenciaPorPreguntar($dia, $hoy)")
                    assertEquals(reminderKeyFor(r, hoy, settings = ref), reminderKeyFor(r, hoy, settings = q))
                }
                hoy = hoy.plusDays(1)
            }
        }
    }

    @Test
    fun `el arriendo del 5 sin pagar sigue preguntandose toda la quincena siguiente`() {
        // Quincenas del 1 y del 16: el arriendo del 5 vence en la primera. El 20 ya es la segunda, y
        // la ocurrencia sigue en juego (para «pendientes de períodos anteriores»), no se esfuma.
        assertEquals(LocalDate.of(2026, 11, 5), ocurrenciaPorPreguntar(LocalDate.of(2026, 11, 20), regla(5), unoYDieciseis))
        assertEquals(LocalDate.of(2026, 11, 5), ocurrenciaPorPreguntar(LocalDate.of(2026, 11, 30), regla(5), unoYDieciseis))
        // Pero no está en el checklist de ESA quincena: venció en la anterior (D1, entero donde vence).
        assertNull(vencimientoEnElChecklist(regla(5), LocalDate.of(2026, 11, 20), unoYDieciseis, emptySet()))
        // Y en la quincena en que vence, sí.
        assertEquals(LocalDate.of(2026, 11, 5), vencimientoEnElChecklist(regla(5), LocalDate.of(2026, 11, 3), unoYDieciseis, emptySet()))
    }

    @Test
    fun `un pago mensual entra entero en la quincena en que vence`() {
        // 15 y el último: la quincena del 31-oct (sábado → viernes 30) al 12-nov (el 15 es domingo →
        // arranca el viernes 13). El arriendo del 5 está en ella; el gimnasio del 20 (de la del 15) no.
        val hoy = LocalDate.of(2026, 11, 3)
        assertEquals(LocalDate.of(2026, 11, 5), vencimientoEnElChecklist(regla(5), hoy, quinceYUltimo, emptySet()))
        assertNull(vencimientoEnElChecklist(regla(20), hoy, quinceYUltimo, setOf("2026-10")))
        assertEquals(LocalDate.of(2026, 11, 20), vencimientoEnElChecklist(regla(20), LocalDate.of(2026, 11, 16), quinceYUltimo, setOf("2026-10")))
    }

    @Test
    fun `una obligacion se nombra por el mes en que vence, no por el id de la quincena`() {
        assertEquals("2026-11", periodoDelDueno(LocalDate.of(2026, 11, 5), quinceYUltimo))
        assertEquals("2026-10", periodoDelDueno(LocalDate.of(2026, 10, 20), quinceYUltimo))
        // El mensual, como siempre: el período del dueño (corte 25 → el 26-oct ya es noviembre).
        assertEquals("2026-11", periodoDelDueno(LocalDate.of(2026, 10, 26), PeriodSettings(cutoffDay = 25)))
    }

    @Test
    fun `el salario de cada quincena se espera el dia que arranca la suya`() {
        val salario15 = RecurringRule("s15", "Salario", "Salario", 2_000_000, 15, TransactionType.INCOME)
        val salario31 = RecurringRule("s31", "Salario", "Salario", 2_000_000, 31, TransactionType.INCOME)
        // 15-nov-2026 es domingo: con «día hábil anterior» se espera el viernes 13. 31-oct es sábado → 30.
        assertEquals(LocalDate.of(2026, 11, 13), fechaEsperada(salario15, LocalDate.of(2026, 11, 15), quinceYUltimo))
        assertEquals(LocalDate.of(2026, 10, 30), fechaEsperada(salario31, LocalDate.of(2026, 10, 31), quinceYUltimo))
        val proximos = upcomingPayments(listOf(salario15, salario31), LocalDate.of(2026, 11, 3), leadDays = 3, settings = quinceYUltimo)
        // El del 31-oct (sábado → viernes 30) todavía no se emparejó: sigue esperándose, antes que el del 15.
        assertEquals(listOf("2026-10-30", "2026-11-13"), proximos.map { it.dueDate })
    }
}
