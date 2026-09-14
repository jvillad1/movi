package com.jvillada.movi.ui.transactions

import com.jvillada.movi.shared.model.EventSource
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.ReconciliationStatus
import com.jvillada.movi.shared.model.TransactionType
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Un gasto que entró solo esperaba en «Por confirmar» fuera de «Gastos», pero un ingreso en la
 * misma situación sí aparecía en «Ingresos»: los dos chips contaban distinto lo mismo.
 */
class PorConfirmarNoSumaEnLosChipsTest {

    private fun ev(tipo: TransactionType, estado: ReconciliationStatus) = FinancialEvent(
        id = "e", accountId = "a", type = tipo, amount = 50_000, category = "Otro", description = "SMS",
        timestamp = 0, source = EventSource.SMS, reconciliationStatus = estado,
    )

    @Test
    fun `lo pendiente queda solo en Por confirmar, sea gasto o ingreso`() {
        for (tipo in listOf(TransactionType.EXPENSE, TransactionType.INCOME)) {
            val pendiente = ev(tipo, ReconciliationStatus.UNCONFIRMED)
            assertFalse(matchesChip(pendiente, CHIP_GASTOS), "$tipo en Gastos")
            assertFalse(matchesChip(pendiente, CHIP_INGRESOS), "$tipo en Ingresos")
            assertTrue(matchesChip(pendiente, CHIP_POR_CONFIRMAR))
        }
        assertTrue(matchesChip(ev(TransactionType.INCOME, ReconciliationStatus.RECONCILED), CHIP_INGRESOS))
    }
}
