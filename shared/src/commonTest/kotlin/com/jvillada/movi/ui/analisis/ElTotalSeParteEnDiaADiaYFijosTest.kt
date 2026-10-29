package com.jvillada.movi.ui.analisis

import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.PeriodSettings
import com.jvillada.movi.shared.model.PeriodoFinanciero
import com.jvillada.movi.shared.model.ReconciliationStatus
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.analisisDelPeriodo
import com.jvillada.movi.shared.time.AppTimeZone
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.atTime
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * # «En qué se va»: el total dice de qué está hecho (vuelta 60)
 *
 * «Llevas gastado $4.888.850» al lado de «Cuánto puedes gastar · Período $2,1M»: el total se parte en
 * «Día a día $2,1M + fijos $2,8M», y el día a día es la cifra de la otra pantalla.
 */
class ElTotalSeParteEnDiaADiaYFijosTest {

    private val ajustes = PeriodSettings(cutoffDay = 25)
    private val hoy = LocalDate(2026, 10, 20)

    private fun gasto(id: String, monto: Long, dia: LocalDate) = FinancialEvent(
        id = id, accountId = "a", type = TransactionType.EXPENSE, amount = monto, category = "Otros", description = id,
        timestamp = dia.atTime(LocalTime(12, 0)).toInstant(AppTimeZone.zone).toEpochMilliseconds(),
        reconciliationStatus = ReconciliationStatus.RECONCILED,
    )

    private val analisis = analisisDelPeriodo(
        listOf(gasto("arriendo", 2_788_850, LocalDate(2026, 9, 26)), gasto("mercado", 2_100_000, LocalDate(2026, 10, 2))),
        PeriodoFinanciero(2026, 10),
        ajustes,
        hoy,
    )

    @Test
    fun `en el periodo en curso el total es dia a dia mas fijos`() {
        val desglose = assertNotNull(desgloseDelAnalisis(analisis, mapOf("2026-10-02" to 2_100_000L), hoy))
        assertEquals(4_888_850L, analisis.total)
        assertEquals(analisis.total, desglose.diaADia + desglose.fijos)
        assertEquals("Día a día \$2,1M + fijos pagados \$2,8M", textoDelDesglose(desglose))
    }

    @Test
    fun `en un periodo cerrado, o sin el gasto del dia a dia, no se parte`() {
        assertNull(desgloseDelAnalisis(analisis, mapOf("2026-10-02" to 2_100_000L), LocalDate(2026, 11, 3)))
        assertNull(desgloseDelAnalisis(analisis, null, hoy))
    }
}
