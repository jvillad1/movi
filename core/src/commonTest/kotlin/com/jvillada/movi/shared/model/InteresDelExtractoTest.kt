package com.jvillada.movi.shared.model

import kotlinx.datetime.LocalDate
import kotlin.math.round
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * # El interés del extracto, en las condiciones del crédito, le gana a la estimación
 *
 * Con las cifras del Libre inversión ·9695: saldo $40.710.555 al 11,27 % E.A., cuota $1.204.064,
 * seguro $156.800. Movi estima ~$364.000 de interés; el extracto dice $594.567.
 */
class InteresDelExtractoTest {

    private val saldo = 40_710_555L
    private val estimado = round(saldo.toDouble() * tasaMensualDeUnaEA(11.27)).toLong()

    private fun terms(interesReal: Long? = 594_567L, cuota: String? = "2026-09") = CreditTerms(
        accountId = "acc-9695", bank = "Bancolombia", principal = 80_000_000L, rateEa = 11.27, termMonths = 132,
        installment = 1_204_064L, dayOfMonth = 15, startDate = "2021-06-15", insuranceMonthly = 156_800L,
        interesReal = interesReal, interesRealCuota = cuota,
    )

    private fun credito(t: CreditTerms) = CreditSummary(
        account = Account(id = "acc-9695", name = "Libre inversión 9695", type = AccountType.LOAN, balance = saldo),
        terms = t,
        paidPct = null,
    )

    @Test
    fun `vale solo para su cuota`() {
        assertEquals(594_567L, terms().interesRealDeLaCuota("2026-09"))
        assertNull(terms().interesRealDeLaCuota("2026-10"))
        assertNull(terms(cuota = null).interesRealDeLaCuota("2026-09"), "sin cuota no se sabe a cuál le gana")
        assertNull(terms(interesReal = null).interesRealDeLaCuota("2026-09"))
    }

    @Test
    fun `vigente hasta que pasa su cuota`() {
        assertEquals(594_567L, terms().interesRealVigente(LocalDate(2026, 9, 1)))
        assertEquals(594_567L, terms().interesRealVigente(LocalDate(2026, 9, 25)), "el 25 la cuota más cercana sigue siendo la del 15")
        assertNull(terms().interesRealVigente(LocalDate(2026, 10, 5)), "en octubre ya es otra cuota")
        assertEquals(594_567L, terms().interesRealVigente(null))
    }

    @Test
    fun `el plan usa el interes del extracto en el primer mes`() {
        val conExtracto = planDelCredito(credito(terms()), LocalDate(2026, 9, 1))!!
        assertEquals(594_567L, conExtracto.interes)
        assertTrue(conExtracto.interesDelExtracto)
        assertEquals(1_204_064L - 594_567L - 156_800L, conExtracto.capital, "el capital es el del banco: 452.697")

        val estimando = planDelCredito(credito(terms()), LocalDate(2026, 10, 20))!!
        assertEquals(estimado, estimando.interes)
        assertFalse(estimando.interesDelExtracto)
        // Más interés el primer mes = menos capital = el mismo plazo o más, nunca menos.
        assertTrue(conExtracto.mesesHastaLaUltimaCuota!! >= estimando.mesesHastaLaUltimaCuota!!)
        assertTrue(conExtracto.interesPorPagar!! > estimando.interesPorPagar!!)
    }

    @Test
    fun `sin interes del extracto el plan es el de siempre`() {
        val p = planDelCredito(credito(terms(interesReal = null)), LocalDate(2026, 9, 1))!!
        assertEquals(estimado, p.interes)
        assertFalse(p.interesDelExtracto)
    }

    @Test
    fun `la propuesta del extracto dice cada cifra al lado de la de Movi`() {
        val p = PropuestaDelExtractoDeCredito(
            resumen = ResumenDelExtractoDeCredito(
                saldoCapital = 40_710_555L, interesDelPeriodo = 594_567L, seguro = 156_800L, tasaEa = 11.27,
                fechaDePago = "2026-09-15",
            ),
            cuentaId = "acc-9695",
            saldoEnMovi = 41_000_000L,
            interesEstimado = 363_905L,
            seguroEnMovi = 124_800L,
            tasaEnMovi = 11.27,
        )
        assertEquals(
            listOf(
                "Saldo \$40.710.555 (Movi \$41.000.000)",
                "Interés del mes \$594.567 (Movi estimaba \$363.905)",
                "Seguro \$156.800 (Movi \$124.800)",
                "Tasa 11,27 % E.A., igual que en Movi",
            ),
            renglonesDelExtractoDeCredito(p),
        )
        assertTrue(hayAlgoQueActualizar(p))
        assertFalse(hayAlgoQueActualizar(p.copy(cuentaId = null)), "sin el crédito en Movi no hay qué actualizar")
    }
}
