package com.jvillada.movi.server.credits

import com.jvillada.movi.server.time.AppClock
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.CARD_PAYMENT_CATEGORY
import com.jvillada.movi.shared.model.CardTerms
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.TransactionType
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Vuelta 62 · `GET /api/cards` (y el `PUT` de sus términos) traen el extracto del corte más reciente:
 * la misma función de `:core` sobre los movimientos sin anulados de la tarjeta, en su moneda.
 */
class ElExtractoEnLaTarjetaTest {

    private val nu = Account("nt", "Nu", AccountType.CREDIT_CARD, 1_583_000L)
    private val terms = CardTerms(accountId = "nt", bank = "Nu", cutoffDay = 20, paymentDay = 5, pagoMinimo = 180_000L)

    private fun ev(fecha: String, tipo: TransactionType, monto: Long, moneda: String = "COP") = FinancialEvent(
        id = "$fecha-$monto",
        accountId = "nt",
        type = tipo,
        amount = monto,
        currency = moneda,
        category = if (tipo == TransactionType.EXPENSE) "Mercado" else CARD_PAYMENT_CATEGORY,
        description = "",
        timestamp = LocalDate.parse(fecha).atTime(12, 0).atZone(AppClock.zone).toInstant().toEpochMilli(),
    )

    private val eventos = listOf(
        ev("2026-10-12", TransactionType.EXPENSE, 1_392_700L),
        ev("2026-10-24", TransactionType.EXPENSE, 290_300L),
        ev("2026-10-26", TransactionType.INCOME, 100_000L),
        ev("2026-10-13", TransactionType.EXPENSE, 50L, moneda = "USD"),
    )

    @Test
    fun `la tarjeta trae lo facturado al corte, el minimo y lo que va al proximo`() {
        val resumen = cardSummaryFor(nu, terms, eventos, hoy = LocalDate.of(2026, 10, 29))
        val extracto = assertNotNull(resumen.extracto)
        assertEquals("2026-10-20", extracto.corte)
        assertEquals("2026-11-05", extracto.fechaDePago)
        assertEquals(1_292_700L, extracto.facturado)
        assertEquals(180_000L, extracto.minimo)
        assertEquals(290_300L, extracto.despuesDelCorte)
        assertEquals("COP", extracto.moneda, "los dólares de la hermana no se mezclan")
    }

    @Test
    fun `sin movimientos o sin corte no hay extracto`() {
        assertNull(cardSummaryFor(nu, terms).extracto)
        assertNull(cardSummaryFor(nu, terms.copy(cutoffDay = null), eventos).extracto)
        assertNull(cardSummaryFor(nu, null, eventos).extracto)
    }
}
