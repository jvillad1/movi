package com.jvillada.movi.ui.plan

import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.CajaProyectada
import com.jvillada.movi.shared.model.DiaDeLaCaja
import com.jvillada.movi.shared.model.GastoDelDiaADia
import com.jvillada.movi.shared.model.GastoVariableDeUnPeriodo
import com.jvillada.movi.shared.model.MovimientoDeLaCaja
import com.jvillada.movi.shared.model.PaymentStatus
import com.jvillada.movi.shared.model.PeriodSettings
import com.jvillada.movi.shared.model.PeriodoFinanciero
import com.jvillada.movi.shared.model.RecurringRule
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.UpcomingPayment
import com.jvillada.movi.ui.dashboard.DashboardData
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Lo que dice «Tu plata, día a día» en Plan (Ola 4). La aritmética la prueba `CajaProyectadaTest`
 * en `:core`; acá se prueba que la pantalla la arme con la lista del período de Plan y que las
 * frases digan lo que pasa —fecha, cifra y por qué— sin inventar nada.
 */
class CajaDelPeriodoTest {

    private val hoy = LocalDate(2026, 10, 2)

    private fun dia(fecha: String, saldo: Long, vararg movimientos: Pair<String, Long>) =
        DiaDeLaCaja(fecha, saldo, movimientos.map { MovimientoDeLaCaja(it.first, it.second) })

    @Test
    fun `el dia mas bajo nombra el pago de ese dia`() {
        val caja = CajaProyectada(
            tuPlataHoy = 12_900_000L,
            gastoDiario = null,
            dias = listOf(
                dia("2026-10-02", 12_900_000L),
                dia("2026-10-17", 8_798_877L, "Cuota Vehiculo 8761" to -4_101_123L),
                dia("2026-10-20", 17_798_877L, "Sueldo" to 9_000_000L),
            ),
        )
        assertEquals("El 17 de octubre llegas a \$8,8M, el día de Cuota Vehiculo 8761", fraseDelDiaMasBajo(caja, hoy))
        assertNull(fraseDelRojo(caja, hoy))
    }

    @Test
    fun `sin pago ese dia, el dia mas bajo es la vispera del sueldo`() {
        val caja = CajaProyectada(
            tuPlataHoy = 3_000_000L,
            gastoDiario = 100_000L,
            dias = listOf(
                dia("2026-10-18", 3_000_000L),
                dia("2026-10-19", 2_900_000L),
                dia("2026-10-20", 11_800_000L, "Sueldo" to 9_000_000L),
            ),
        )
        assertTrue(fraseDelDiaMasBajo(caja, hoy).endsWith("un día antes de que llegue Sueldo"), fraseDelDiaMasBajo(caja, hoy))
    }

    @Test
    fun `una fecha en negativo se dice con el pago que la causa`() {
        val caja = CajaProyectada(
            tuPlataHoy = 1_000_000L,
            gastoDiario = null,
            dias = listOf(
                dia("2026-10-02", 1_000_000L),
                dia("2026-10-17", -3_101_123L, "Cuota Vehiculo 8761" to -4_101_123L),
            ),
        )
        val rojo = fraseDelRojo(caja, hoy)
        assertNotNull(rojo)
        assertTrue(rojo.startsWith("El 17 de octubre quedarías en"), rojo)
        assertTrue("por Cuota Vehiculo 8761 (\$4.101.123)" in rojo, rojo)
    }

    @Test
    fun `el supuesto dice de que periodos sale, y si no hay, que no hay`() {
        val uno = GastoDelDiaADia(100_000L, listOf(GastoVariableDeUnPeriodo("2026-09", 3_100_000L, 31)))
        assertEquals(
            "Suponiendo que gastas como siempre, unos \$100.000 por día (tu promedio del período de septiembre).",
            fraseDelSupuesto(uno),
        )
        val dos = GastoDelDiaADia(
            100_000L,
            listOf(GastoVariableDeUnPeriodo("2026-09", 3_100_000L, 31), GastoVariableDeUnPeriodo("2026-08", 2_900_000L, 29)),
        )
        assertTrue("tu promedio de los últimos 2 períodos" in fraseDelSupuesto(dos))
        assertTrue(fraseDelSupuesto(GastoDelDiaADia()).startsWith("Todavía no hay un período cerrado"))
    }

    @Test
    fun `lo que no entra se dice, y si entra todo no se dice nada`() {
        val caja = CajaProyectada(0L, null, listOf(dia("2026-10-02", 0L)), sinContar = listOf("AMEX: falta el pago mínimo"))
        assertEquals("No entra a la cuenta: AMEX: falta el pago mínimo.", fraseDeLoQueFalta(caja))
        assertNull(fraseDeLoQueFalta(caja.copy(sinContar = emptyList())))
    }

    /** La sección usa la lista «Pagos del período» de Plan y Tu plata de las cuentas. */
    @Test
    fun `la caja sale de la lista del periodo de plan y de tu plata`() {
        val arriendo = RecurringRule(
            id = "rr_arriendo", name = "Arriendo", category = "Vivienda",
            amount = 2_000_000L, dayOfMonth = 5, type = TransactionType.EXPENSE,
        )
        val data = DashboardData(
            accounts = listOf(
                Account("a1", "Bancolombia", AccountType.SAVINGS, 5_000_000L, "COP"),
                Account("tc", "Master Black", AccountType.CREDIT_CARD, 27_000_000L, "COP"),
            ),
            upcoming = listOf(UpcomingPayment(arriendo, "2026-10-05", 3, PaymentStatus.UPCOMING)),
            ocurrencias = emptyList(),
            ajustesDePeriodo = PeriodSettings(cutoffDay = 25),
            periodoActual = PeriodoFinanciero(2026, 10),
        )
        val caja = cajaDelPeriodoDe(data, GastoDelDiaADia(), hoy)
        assertNotNull(caja)
        assertEquals(5_000_000L, caja.tuPlataHoy, "la tarjeta es deuda: no está en Tu plata")
        assertEquals("2026-10-24", caja.alCierre.fecha)
        assertEquals(3_000_000L, caja.alCierre.saldo)
        assertNull(cajaDelPeriodoDe(data.copy(ocurrencias = null), GastoDelDiaADia(), hoy), "sin la lista no se proyecta")
    }
}
