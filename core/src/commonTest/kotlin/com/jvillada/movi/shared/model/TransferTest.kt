package com.jvillada.movi.shared.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Fija el modelo del **traspaso**: dos patas enlazadas por [FinancialEvent.transferId], las dos
 * con la categoría reservada [TRANSFER_CATEGORY]. Ver el KDoc de [transferLegsFor] para el
 * porqué de la forma; estos tests solo blindan el comportamiento.
 */
class TransferTest {

    private val ahorros = Account("acc_ahorros", "Ahorros", AccountType.SAVINGS, balance = 1_000_000L)
    private val cdt = Account("acc_cdt", "CDT", AccountType.INVESTMENT, balance = 0L)
    private val efectivoUsd = Account("acc_usd", "Efectivo USD", AccountType.CASH, balance = 0L, currency = "USD")
    private val tarjeta = Account("acc_tc", "Visa", AccountType.CREDIT_CARD, balance = 500_000L)
    private val prestamo = Account("acc_loan", "Libranza", AccountType.LOAN, balance = 9_000_000L)

    // ── Validación ────────────────────────────────────────────────────────────

    @Test
    fun `un traspaso entre dos cuentas de la misma moneda es valido`() {
        assertNull(validateTransfer(ahorros, cdt, 250_000L))
    }

    @Test
    fun `falta elegir una de las dos cuentas`() {
        assertEquals("Elige la cuenta de origen y la de destino", validateTransfer(null, cdt, 100L))
        assertEquals("Elige la cuenta de origen y la de destino", validateTransfer(ahorros, null, 100L))
    }

    @Test
    fun `origen y destino no pueden ser la misma cuenta`() {
        assertEquals(
            "El origen y el destino tienen que ser cuentas distintas",
            validateTransfer(ahorros, ahorros, 100_000L),
        )
    }

    @Test
    fun `el monto tiene que ser mayor que cero`() {
        assertEquals("El monto tiene que ser mayor que cero", validateTransfer(ahorros, cdt, 0L))
        assertEquals("El monto tiene que ser mayor que cero", validateTransfer(ahorros, cdt, -5L))
    }

    @Test
    fun `una tarjeta no puede ser origen ni destino de un traspaso`() {
        val esperado = "Las tarjetas y los préstamos se manejan en Créditos, no con un traspaso"
        assertEquals(esperado, validateTransfer(tarjeta, cdt, 100_000L))
        assertEquals(esperado, validateTransfer(ahorros, tarjeta, 100_000L))
    }

    @Test
    fun `un prestamo tampoco puede ser origen ni destino`() {
        val esperado = "Las tarjetas y los préstamos se manejan en Créditos, no con un traspaso"
        assertEquals(esperado, validateTransfer(prestamo, cdt, 100_000L))
        assertEquals(esperado, validateTransfer(ahorros, prestamo, 100_000L))
    }

    @Test
    fun `no se puede traspasar entre monedas distintas`() {
        assertEquals(
            "Por ahora solo entre cuentas de la misma moneda",
            validateTransfer(ahorros, efectivoUsd, 100_000L),
        )
    }

    // ── Las dos patas ─────────────────────────────────────────────────────────

    private fun request(note: String? = null) = CreateTransferRequest(
        transferId = "tr_1",
        fromEventId = "ev_from",
        toEventId = "ev_to",
        fromAccountId = ahorros.id,
        toAccountId = cdt.id,
        amount = 250_000L,
        timestamp = 1_700_000_000_000L,
        note = note,
    )

    @Test
    fun `la pata de origen es un EXPENSE y la de destino un INCOME, las dos con el mismo transferId`() {
        val (from, to) = transferLegsFor(request(), ahorros, cdt)

        assertEquals(TransactionType.EXPENSE, from.type)
        assertEquals(ahorros.id, from.accountId)
        assertEquals(TransactionType.INCOME, to.type)
        assertEquals(cdt.id, to.accountId)
        assertEquals("tr_1", from.transferId)
        assertEquals("tr_1", to.transferId)
        assertEquals(250_000L, from.amount)
        assertEquals(250_000L, to.amount)
        assertEquals(1_700_000_000_000L, from.timestamp)
        assertEquals(1_700_000_000_000L, to.timestamp)
    }

    @Test
    fun `las dos patas llevan la categoria reservada y quedan fuera del flujo de caja`() {
        val (from, to) = transferLegsFor(request(), ahorros, cdt)

        assertEquals(TRANSFER_CATEGORY, from.category)
        assertEquals(TRANSFER_CATEGORY, to.category)
        assertFalse(from.countsAsCashFlow)
        assertFalse(to.countsAsCashFlow)
    }

    @Test
    fun `la descripcion de cada pata dice hacia donde va la plata`() {
        val (from, to) = transferLegsFor(request(), ahorros, cdt)

        assertEquals("Traspaso a CDT", from.description)
        assertEquals("Traspaso desde Ahorros", to.description)
    }

    @Test
    fun `la nota se agrega a la descripcion de las dos patas`() {
        val (from, to) = transferLegsFor(request(note = "apertura del CDT"), ahorros, cdt)

        assertEquals("Traspaso a CDT · apertura del CDT", from.description)
        assertEquals("Traspaso desde Ahorros · apertura del CDT", to.description)
    }

    @Test
    fun `una nota en blanco no ensucia la descripcion`() {
        val (from, _) = transferLegsFor(request(note = "   "), ahorros, cdt)
        assertEquals("Traspaso a CDT", from.description)
    }

    @Test
    fun `las patas heredan la moneda de las cuentas y quedan conciliadas`() {
        val (from, to) = transferLegsFor(request(), ahorros, cdt)

        assertEquals("COP", from.currency)
        assertEquals("COP", to.currency)
        assertEquals(ReconciliationStatus.RECONCILED, from.reconciliationStatus)
        assertEquals(ReconciliationStatus.RECONCILED, to.reconciliationStatus)
        assertEquals(EventSource.MANUAL, from.source)
    }

    @Test
    fun `cada pata usa el id que mando el cliente`() {
        val (from, to) = transferLegsFor(request(), ahorros, cdt)
        assertEquals("ev_from", from.id)
        assertEquals("ev_to", to.id)
    }

    // ── Saldos vs. flujo de caja ──────────────────────────────────────────────

    /**
     * El corazón del diseño: los saldos SÍ se mueven (cada pata es un evento normal de su
     * cuenta, y `signedDelta` no sabe nada de traspasos) pero el mes NO se entera.
     */
    @Test
    fun `un traspaso mueve los dos saldos pero no cuenta como ingreso ni como egreso`() {
        val (from, to) = transferLegsFor(request(), ahorros, cdt)

        assertEquals(-250_000L, signedDelta(ahorros.type, from.type, from.amount))
        assertEquals(+250_000L, signedDelta(cdt.type, to.type, to.amount))
        assertFalse(isCashFlow(ahorros.type, from.type, from.category))
        assertFalse(isCashFlow(cdt.type, to.type, to.category))
    }

    @Test
    fun `un evento cualquiera no trae transferId`() {
        assertNull(
            FinancialEvent(
                id = "ev_1", accountId = "acc_1", type = TransactionType.EXPENSE, amount = 1L,
                category = "Mercado", description = "pan", timestamp = 0L,
            ).transferId,
        )
        assertNotNull(transferLegsFor(request(), ahorros, cdt).first.transferId)
    }
}
