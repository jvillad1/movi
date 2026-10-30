package com.jvillada.movi.ui.dashboard

import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.CajaProyectada
import com.jvillada.movi.shared.model.DiaDeLaCaja
import com.jvillada.movi.shared.model.GastoDelDiaADia
import com.jvillada.movi.shared.model.PaymentStatus
import com.jvillada.movi.shared.model.PeriodSettings
import com.jvillada.movi.shared.model.PeriodoFinanciero
import com.jvillada.movi.shared.model.RecurringRule
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.UpcomingPayment
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * # Vuelta 75 · el renglón «¿Me alcanza?» del héroe, sin pantalla
 *
 * Te alcanza / no te alcanza / sin datos. Se mide en el peor día de la caja (lo libre), la misma cifra
 * titular de la caja de Pagos.
 */
class RenglonMeAlcanzaTest {

    private val hoy = LocalDate(2026, 10, 29)

    private fun caja(vararg dias: Pair<String, Long>) =
        CajaProyectada(tuPlataHoy = dias.first().second, gastoDiario = 20_000L, dias = dias.map { DiaDeLaCaja(it.first, it.second) })

    @Test
    fun `te alcanza dice hasta cuando y el dia mas justo`() {
        val renglon = renglonMeAlcanza(caja("2026-10-29" to 900_000, "2026-11-05" to 180_000, "2026-11-25" to 400_000).copy(hastaElSalario = true), hoy)
        assertEquals("Te alcanza hasta el 25 nov · tu día más justo, el 5 nov", renglon.texto)
        assertEquals(RenglonMeAlcanza("Te alcanza hasta el 25 nov", "tu día más justo, el 5 nov", enRojo = false), renglon)
    }

    @Test
    fun `si el dia mas justo es hoy lo dice asi`() {
        val renglon = renglonMeAlcanza(caja("2026-10-29" to 100_000, "2026-11-25" to 300_000).copy(hastaElSalario = true), hoy)
        assertEquals("Te alcanza hasta el 25 nov · tu día más justo, hoy", renglon.texto)
    }

    /** Revisión de la 75: sin salario, nada entra el día que termina la caja; no se dice con fecha. */
    @Test
    fun `sin salario no dice hasta una fecha como si ese dia entrara algo`() {
        val dias = caja("2026-10-29" to 900_000, "2026-11-25" to 400_000)
        assertEquals("Te alcanza hasta el fin del período · tu día más justo, el 25 nov", renglonMeAlcanza(dias.copy(hastaElPeriodoSiguiente = true), hoy).texto)
        assertEquals("Te alcanza los próximos 30 días · tu día más justo, el 25 nov", renglonMeAlcanza(dias, hoy).texto)
    }

    @Test
    fun `no te alcanza dice el peor dia y su cifra`() {
        val renglon = renglonMeAlcanza(
            caja("2026-10-29" to 100_000, "2026-11-03" to -100_000, "2026-11-05" to -1_320_000, "2026-11-25" to 5_000_000),
            hoy,
        )
        assertEquals("No te alcanza: el 5 nov quedarías en\u00A0−\u2060\$1,3M", renglon.texto)
        assertTrue(renglon.enRojo)
        val chico = renglonMeAlcanza(caja("2026-10-29" to -683_361, "2026-11-25" to 1_000_000), hoy)
        assertEquals("No te alcanza: hoy quedarías en\u00A0−\u2060\$683\u00A0mil", chico.texto, "la cifra corta, como en la caja")
    }

    @Test
    fun `se mide en lo libre, no en el saldo`() {
        val conAhorro = CajaProyectada(
            tuPlataHoy = 3_000_000L,
            gastoDiario = null,
            dias = listOf(DiaDeLaCaja("2026-10-29", 3_000_000L, libre = 1_000_000L), DiaDeLaCaja("2026-11-25", 2_500_000L, libre = -500_000L)),
            ahorro = 2_000_000L,
        )
        assertEquals("No te alcanza: el 25 nov quedarías en\u00A0−\u2060\$500\u00A0mil", renglonMeAlcanza(conAhorro, hoy).texto)
    }

    @Test
    fun `sin datos no hay renglon`() {
        val ajustes = PeriodSettings(cutoffDay = 25)
        val data = DashboardData(
            accounts = listOf(Account("a1", "Bancolombia", AccountType.SAVINGS, 2_000_000L, "COP")),
            upcoming = listOf(
                UpcomingPayment(
                    RecurringRule(id = "rr", name = "Arriendo", category = "Vivienda", amount = 800_000L, dayOfMonth = 5, type = TransactionType.EXPENSE),
                    "2026-11-05", 7, PaymentStatus.UPCOMING,
                ),
            ),
            ocurrencias = emptyList(),
            ajustesDePeriodo = ajustes,
            periodoActual = PeriodoFinanciero(2026, 11),
        )
        // Con todo, la caja llega al arranque del período siguiente (sin salario, vuelta 75).
        assertEquals(
            "Te alcanza hasta el fin del período · tu día más justo, el 25 nov",
            renglonMeAlcanzaDe(data, GastoDelDiaADia(porDia = 20_000L), hoy)?.texto,
        )
        assertNull(renglonMeAlcanzaDe(data, gasto = null, hoy = hoy), "sin el supuesto del gasto, nada")
        assertNull(renglonMeAlcanzaDe(data.copy(upcoming = null), GastoDelDiaADia(porDia = 20_000L), hoy), "sin la lista")
        assertNull(renglonMeAlcanzaDe(data.copy(accounts = emptyList()), GastoDelDiaADia(porDia = 20_000L), hoy), "sin cuentas")
        assertNull(renglonMeAlcanzaDe(data.copy(periodoActual = null), GastoDelDiaADia(porDia = 20_000L), hoy), "sin período")
    }
}
