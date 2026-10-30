package com.jvillada.movi.ui.transactions

import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.EventDay
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.PeriodSettings
import com.jvillada.movi.shared.model.PeriodoFinanciero
import com.jvillada.movi.shared.model.ReconciliationStatus
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.time.AppTimeZone
import com.jvillada.movi.shared.time.epochMillisToAppDate
import kotlinx.datetime.LocalDate
import kotlinx.datetime.atStartOfDayIn
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * # Vuelta 67 · la franja Entró · Salió · Neto sigue al chip y cuadra con la lista
 *
 * La revisión de la vuelta 66: en «Compartidos» la franja decía $4.888.850 (todo el período) y la lista
 * de abajo sumaba $408.050. Decisión: la franja es la suma de la lista que tiene debajo.
 */
class LaFranjaSigueAlChipTest {

    private val corte25 = PeriodSettings(cutoffDay = 25)
    private val noviembre = PeriodoFinanciero(2026, 11)
    private val hoy = LocalDate.parse("2026-10-29")
    private val tipos = mapOf("ahorros" to AccountType.SAVINGS)

    private fun alMediodia(dia: String): Long =
        LocalDate.parse(dia).atStartOfDayIn(AppTimeZone.zone).toEpochMilliseconds() + 12 * 3_600_000L

    private fun mov(
        id: String, dia: String, monto: Long, categoria: String = "Comida",
        tipo: TransactionType = TransactionType.EXPENSE, espacio: String? = null, parteAjena: Long = 0L,
    ) = FinancialEvent(
        id = id, accountId = "ahorros", type = tipo, amount = monto, category = categoria, description = categoria,
        timestamp = alMediodia(dia), reconciliationStatus = ReconciliationStatus.RECONCILED,
        espacioId = espacio, parteAjena = parteAjena,
    )

    private val dias = listOf(
        mov("e_mercado", "2026-10-28", 184_000L, "Mercado", espacio = "esp_casa", parteAjena = 92_000L),
        mov("e_almuerzo", "2026-10-28", 30_000L),
        mov("e_salario", "2026-10-27", 850_000L, "Salario", TransactionType.INCOME),
        mov("e_d1", "2026-10-26", 64_300L, "Mercado", espacio = "esp_casa", parteAjena = 32_150L),
    ).groupBy { epochMillisToAppDate(it.timestamp).toString() }
        .map { (dia, del) -> EventDay(dia, 0L, del) }
        .sortedByDescending { it.date }

    private fun aLaVista(chip: Int) = diasDelPeriodo(diasVisibles(dias, chip, "", tipos), noviembre, corte25)
    private val delPeriodo = resumenDelPanel(dias, noviembre, corte25, hoy, tipos)

    @Test
    fun `con Todo la franja es la del periodo`() {
        val franja = laFranjaDeLaLista(delPeriodo, aLaVista(CHIP_TODO), tipos)
        assertEquals(delPeriodo.entro, franja.entro)
        assertEquals(delPeriodo.salio, franja.salio)
    }

    @Test
    fun `en Compartidos suma solo lo compartido, tu parte, como sus filas`() {
        val lista = aLaVista(CHIP_PERSONAS)
        val franja = laFranjaDeLaLista(delPeriodo, lista, tipos)
        assertEquals(92_000L + 32_150L, franja.salio)
        assertEquals(0L, franja.entro)
        assertEquals(lista.flatMap { it.items }.sumOf { montoPrincipalDelMovimiento(it) }, franja.salio, "la franja es la suma de las filas")
        assertEquals(-franja.salio, franja.neto)
    }

    @Test
    fun `en Ingresos solo entro`() {
        val franja = laFranjaDeLaLista(delPeriodo, aLaVista(CHIP_INGRESOS), tipos)
        assertEquals(850_000L, franja.entro)
        assertEquals(0L, franja.salio)
    }

    @Test
    fun `a 1280 el panel se titula Resumen del periodo, con o sin chip`() {
        // Vuelta 83: el mes ya lo dice el navegador de la lista de al lado.
        assertEquals("Resumen del período", TITULO_DEL_RESUMEN_EN_EL_PANEL)
    }
}
