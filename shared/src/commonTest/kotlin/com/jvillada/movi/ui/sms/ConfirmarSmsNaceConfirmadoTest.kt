package com.jvillada.movi.ui.sms

import com.jvillada.movi.shared.model.EventSource
import com.jvillada.movi.shared.model.ParsedSms
import com.jvillada.movi.shared.model.ReconciliationStatus
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.cuentaEnGastosEIngresos
import com.jvillada.movi.ui.transactions.CHIP_GASTOS
import com.jvillada.movi.ui.transactions.CHIP_POR_CONFIRMAR
import com.jvillada.movi.ui.transactions.matchesChip
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Confirmar un SMS en la bandeja creaba el movimiento con el default del modelo, `UNCONFIRMED`:
 * lo que el dueño acababa de confirmar quedaba esperando en «Por confirmar» y fuera de «Gastos».
 */
class ConfirmarSmsNaceConfirmadoTest {

    private val leido = ParsedSms(
        amount = 15.44,
        merchant = "EXITO",
        type = TransactionType.EXPENSE,
        category = "Mercado",
        currency = "USD",
    )

    @Test
    fun `el movimiento de un SMS confirmado nace RECONCILED y cuenta en Gastos`() {
        val ev = movimientoConfirmadoDelSms(
            id = "ev_1",
            cuentaId = "acc",
            leido = leido,
            categoria = "Mercado",
            momento = 1_000L,
        )

        assertEquals(ReconciliationStatus.RECONCILED, ev.reconciliationStatus)
        assertEquals(EventSource.SMS, ev.source)
        assertTrue(cuentaEnGastosEIngresos(ev))
        assertTrue(matchesChip(ev, CHIP_GASTOS))
        assertFalse(matchesChip(ev, CHIP_POR_CONFIRMAR))
        // Lo que ya hacía y no se pierde en el camino.
        assertEquals(15L, ev.amount)
        assertEquals("USD", ev.currency)
        assertEquals(1_000L, ev.timestamp)
    }
}
