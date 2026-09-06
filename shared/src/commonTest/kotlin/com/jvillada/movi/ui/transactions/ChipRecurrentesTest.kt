package com.jvillada.movi.ui.transactions

import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.EventDay
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.RecurringRule
import com.jvillada.movi.shared.model.ReconciliationStatus
import com.jvillada.movi.shared.model.TRANSFER_CATEGORY
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.isCashFlow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * PR 1 del rediseño de Recurrentes (2026-09): el chip nuevo de Movimientos, con el mismo patrón
 * de prueba que ya cubre «Entre cuentas» en [ColorYEntreCuentasTest] — el chip es un filtro más,
 * armado sobre [matchesChip]/[diasVisibles], y no un mecanismo aparte.
 */
class ChipRecurrentesTest {

    private fun ev(
        id: String,
        cuenta: AccountType,
        type: TransactionType,
        category: String,
        description: String = id,
        amount: Long = 100_000L,
        transferId: String? = null,
    ) = FinancialEvent(
        id = id,
        accountId = "acc_${cuenta.name.lowercase()}",
        type = type,
        amount = amount,
        category = category,
        description = description,
        timestamp = 0L,
        transferId = transferId,
        reconciliationStatus = ReconciliationStatus.RECONCILED,
        countsAsCashFlow = isCashFlow(cuenta, type, category),
    )

    private fun regla(name: String) = RecurringRule(
        id = "rr_$name",
        name = name,
        category = "Vivienda",
        amount = 1_800_000L,
        dayOfMonth = 5,
        type = TransactionType.EXPENSE,
    )

    private val arriendo = ev("arriendo", AccountType.SAVINGS, TransactionType.EXPENSE, "Vivienda", description = "Arriendo", amount = 1_800_000L)
    private val netflix = ev("netflix", AccountType.SAVINGS, TransactionType.EXPENSE, "Entretenimiento", description = "Netflix", amount = 44_900L)
    private val mercado = ev("mercado", AccountType.SAVINGS, TransactionType.EXPENSE, "Mercado", description = "Mercado", amount = 250_000L)
    private val traspasoSale = ev("tr_out", AccountType.SAVINGS, TransactionType.EXPENSE, TRANSFER_CATEGORY, description = "Arriendo", transferId = "tr_1")

    private val reglas = listOf(regla("Arriendo"))
    private val nombresDeSuscripciones = listOf("Netflix")

    @Test
    fun `el chip reconoce un movimiento que matchea una regla o una suscripcion`() {
        assertTrue(matchesChip(arriendo, CHIP_RECURRENTES, reglas, nombresDeSuscripciones))
        assertTrue(matchesChip(netflix, CHIP_RECURRENTES, reglas, nombresDeSuscripciones))
    }

    @Test
    fun `el chip deja afuera lo que no matchea ninguna de las dos listas`() {
        assertFalse(matchesChip(mercado, CHIP_RECURRENTES, reglas, nombresDeSuscripciones))
    }

    @Test
    fun `sin reglas ni suscripciones cargadas el chip no reconoce nada, no explota`() {
        assertFalse(matchesChip(arriendo, CHIP_RECURRENTES))
        assertFalse(matchesChip(netflix, CHIP_RECURRENTES))
    }

    @Test
    fun `una pata de traspaso nunca entra al chip, aunque el nombre coincida con una regla`() {
        assertFalse(matchesChip(traspasoSale, CHIP_RECURRENTES, reglas, nombresDeSuscripciones))
    }

    @Test
    fun `diasVisibles filtra por el chip nuevo y recalcula el total del dia`() {
        val dia = EventDay(
            date = "2026-09-01",
            total = 0L,
            items = listOf(arriendo, netflix, mercado),
        )
        val visibles = diasVisibles(listOf(dia), CHIP_RECURRENTES, "", reglas, nombresDeSuscripciones).single()

        assertEquals(listOf("arriendo", "netflix"), visibles.items.map { it.id })
        assertEquals(-(arriendo.amount + netflix.amount), visibles.total)
    }

    @Test
    fun `el vacio del chip nuevo explica que son reglas y suscripciones ya reconocidas`() {
        val vacio = vacioDeMovimientos(CHIP_RECURRENTES, hayMovimientos = true)
        assertEquals("Nada recurrente", vacio.titulo)
        assertFalse(vacio.ofreceRegistrar)
    }

    @Test
    fun `el rotulo del chip nuevo esta en el indice nuevo`() {
        assertEquals("Recurrentes", CHIPS_DE_MOVIMIENTOS[CHIP_RECURRENTES])
    }

    // ── PR 2 del rediseño: el resumen de flujo libre y las candidatas ─────────

    @Test
    fun `el resumen de flujo libre solo se muestra con el chip Recurrentes activo`() {
        assertTrue(mostrarResumenDeRecurrentes(CHIP_RECURRENTES))
        assertFalse(mostrarResumenDeRecurrentes(CHIP_TODO))
        assertFalse(mostrarResumenDeRecurrentes(CHIP_GASTOS))
        assertFalse(mostrarResumenDeRecurrentes(CHIP_INGRESOS))
        assertFalse(mostrarResumenDeRecurrentes(CHIP_POR_CONFIRMAR))
        assertFalse(mostrarResumenDeRecurrentes(CHIP_ENTRE_CUENTAS))
    }
}
