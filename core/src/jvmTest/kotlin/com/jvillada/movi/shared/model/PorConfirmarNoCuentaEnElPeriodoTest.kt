package com.jvillada.movi.shared.model

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * La regla única de «Por confirmar»: lo pendiente no entra en «Gastos» ni en «Ingresos» del
 * período. El server (con el estado como texto) y el cliente (con el enum) preguntan lo mismo.
 */
class PorConfirmarNoCuentaEnElPeriodoTest {

    private fun ev(estado: ReconciliationStatus, flujo: Boolean = true) = FinancialEvent(
        id = "e", accountId = "a", type = TransactionType.EXPENSE, amount = 10_000,
        category = "Comida", description = "", timestamp = 0,
        reconciliationStatus = estado, countsAsCashFlow = flujo,
    )

    @Test
    fun `solo lo confirmado que es flujo cuenta`() {
        assertTrue(cuentaEnGastosEIngresos(ev(ReconciliationStatus.RECONCILED)))
        assertFalse(cuentaEnGastosEIngresos(ev(ReconciliationStatus.UNCONFIRMED)))
        assertFalse(cuentaEnGastosEIngresos(ev(ReconciliationStatus.RECONCILED, flujo = false)))
    }

    @Test
    fun `las dos mitades dicen lo mismo para cada estado`() {
        ReconciliationStatus.entries.forEach { estado ->
            kotlin.test.assertEquals(esperaEnPorConfirmar(estado), esperaEnPorConfirmar(estado.name), estado.name)
        }
    }
}
