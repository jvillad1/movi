package com.jvillada.movi.server.reminders

import com.jvillada.movi.shared.model.CreditTerms
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * # Vuelta 77 (revisión) · un pago antes de la primera cuota salda la primera cuota
 *
 * Un crédito con «ya pagué la cuota de este período» (`cuotasDesde = 2026-11-01`, día 15) pagado el 30 de
 * octubre desde «Pagar la cuota de noviembre» sellaba «2026-10» —una cuota que no existe— y noviembre
 * seguía pendiente: riesgo de pagarla dos veces. Lo mismo un crédito recién desembolsado pagado antes de
 * su primera cuota. `periodoQueSalda` ya no devuelve un período anterior al arranque de la regla.
 */
class ElPagoAdelantadoSaldaLaPrimeraCuotaTest {

    private fun terminos(desde: String, cuotasDesde: String? = null) = CreditTerms(
        accountId = "acc_r6", bank = "Bancolombia", principal = 25_000_000L, rateEa = 21.0, termMonths = 48,
        installment = 770_000L, dayOfMonth = 15, startDate = desde, cuotasDesde = cuotasDesde,
    )

    @Test
    fun `con ya pague, el pago del 30 de octubre salda noviembre y la proxima es la de diciembre`() {
        val regla = virtualRuleFor(terminos("2025-06-01", cuotasDesde = "2026-11-01"), "Libre Inversión R6")
        val salda = periodoQueSalda(regla, LocalDate.of(2026, 10, 30))
        assertEquals("2026-11", salda)
        // /upcoming con noviembre saldado: la del 15 de diciembre.
        assertEquals(LocalDate.of(2026, 12, 15), dueDateFor(regla, LocalDate.of(2026, 10, 30), occurredPeriods = setOf(salda)))
    }

    @Test
    fun `un credito desembolsado pagado antes de su primera cuota salda la primera`() {
        val regla = virtualRuleFor(terminos("2026-10-20"), "Libre Inversión R6")
        assertEquals("2026-11", periodoQueSalda(regla, LocalDate.of(2026, 10, 30)))
    }

    @Test
    fun `un credito de siempre sigue saldando la cuota de su periodo`() {
        val regla = virtualRuleFor(terminos("2025-06-01"), "Libre Inversión R6")
        assertEquals("2026-10", periodoQueSalda(regla, LocalDate.of(2026, 10, 30)))
        assertEquals("2026-10", periodoQueSalda(regla, LocalDate.of(2026, 10, 10)))
    }
}
