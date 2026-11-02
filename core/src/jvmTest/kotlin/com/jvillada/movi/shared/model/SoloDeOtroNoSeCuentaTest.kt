package com.jvillada.movi.shared.model

import com.jvillada.movi.shared.time.AppTimeZone
import kotlinx.datetime.LocalDate
import kotlinx.datetime.atStartOfDayIn
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * # Revisión de la vuelta 97 · un gasto «Solo de Caro» no es una vez tuya
 *
 * Con todo el gasto para otra persona, [montoContado] es 0. Los agregadores que **cuentan** movimientos
 * —«Pequeños que suman», los cobros duplicados y los comercios nuevos de lo que se sale de lo normal, y
 * las veces de cada fila de «En qué se va»— no lo cuentan.
 */
class SoloDeOtroNoSeCuentaTest {

    private val octubre = PeriodoFinanciero(2026, 10)
    private val corte25 = PeriodSettings(cutoffDay = 25)
    private var n = 0

    private fun gasto(dia: String, monto: Long, comercio: String, soloDeOtro: Boolean = false, hora: Int = 12) = FinancialEvent(
        id = "e${n++}", accountId = "ahorros", type = TransactionType.EXPENSE, amount = monto, category = "Comida",
        description = comercio, merchant = comercio,
        timestamp = LocalDate.parse(dia).atStartOfDayIn(AppTimeZone.zone).toEpochMilliseconds() + hora * 3_600_000L,
        reconciliationStatus = ReconciliationStatus.RECONCILED,
        parteAjena = if (soloDeOtro) monto else 0L,
        espacioId = if (soloDeOtro) "esp_casa" else null,
    )

    @Test
    fun `pequenos que suman no cuenta un gasto solo de Caro`() {
        val tuyos = List(2) { gasto("2026-10-0${it + 1}", 40_000, "Tienda D1") }
        val deCaro = List(3) { gasto("2026-10-0${it + 3}", 40_000, "Tienda D1", soloDeOtro = true) }
        assertEquals(0, montoContado(deCaro.first()))
        assertTrue(pequenosQueSuman(tuyos + deCaro).isEmpty(), "2 tuyos no llegan a 3 veces")
    }

    @Test
    fun `un cobro duplicado o nuevo que es todo de Caro no avisa`() {
        val dos = listOf(gasto("2026-10-01", 200_000, "Falabella", soloDeOtro = true), gasto("2026-10-01", 200_000, "Falabella", soloDeOtro = true, hora = 13))
        assertTrue(cobrosDuplicados(dos, emptyMap()) { it.toString() }.isEmpty())
        assertEquals(1, cobrosDuplicados(dos.map { it.copy(parteAjena = 0L, espacioId = null) }, emptyMap()) { it.toString() }.size, "tuyos sí")
        val nuevo = gasto("2026-10-20", 900_000, "Joyería", soloDeOtro = true)
        val desde = LocalDate.parse("2026-01-01").atStartOfDayIn(AppTimeZone.zone).toEpochMilliseconds()
        assertTrue(comerciosNuevosConMontoAlto(listOf(nuevo), emptyList(), desde, emptyMap()) { it.toString() }.isEmpty())
    }

    @Test
    fun `En que se va no arma una vez ni una fila de cero con un gasto solo de Caro`() {
        val a = analisisDelPeriodo(
            listOf(gasto("2026-10-01", 30_000, "Crepes"), gasto("2026-10-02", 90_000, "Joyería", soloDeOtro = true)),
            octubre, corte25, LocalDate.parse("2026-10-10"), mapOf("ahorros" to AccountType.SAVINGS),
        )
        val filas = filasDelAnalisis(a, VistaDelAnalisis.COMERCIO)
        assertEquals(1, filas.size)
        assertEquals(1, filas.single().veces)
        assertEquals(30_000, a.total)
    }
}
