package com.jvillada.movi.shared.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

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
        assertEquals(TRANSFER_CARD_BLOCKED, validateTransfer(tarjeta, cdt, 100_000L))
        assertEquals(TRANSFER_CARD_BLOCKED, validateTransfer(ahorros, tarjeta, 100_000L))
    }

    /**
     * **Ola 14 — este test dice lo contrario que antes, a propósito.** Hasta acá afirmaba que un
     * préstamo tampoco podía ser punta de un traspaso; era la regla que dejaba sin registrar el
     * desembolso de un crédito (la deuda entraba a Créditos y la plata que el banco depositaba no
     * existía para Movi). El KDoc de [validateTransfer] tiene la comprobación de por qué la
     * objeción de entonces —«le cambiaría el signo a la deuda»— no aplica a los préstamos.
     */
    @Test
    fun `un prestamo si puede ser una de las dos puntas — desembolso y abono extraordinario`() {
        assertNull(validateTransfer(prestamo, ahorros, 257_000_000L))  // desembolso
        assertNull(validateTransfer(ahorros, prestamo, 5_000_000L))    // abono extraordinario
    }

    @Test
    fun `pero no las dos puntas a la vez`() {
        val otro = Account("acc_loan2", "Vehículo", AccountType.LOAN, balance = 40_000_000L)
        assertEquals(TRANSFER_BOTH_LOANS_BLOCKED, validateTransfer(prestamo, otro, 1_000_000L))
    }

    @Test
    fun `la tarjeta se rechaza aunque la otra punta sea un prestamo`() {
        assertEquals(TRANSFER_CARD_BLOCKED, validateTransfer(tarjeta, prestamo, 100_000L))
        assertEquals(TRANSFER_CARD_BLOCKED, validateTransfer(prestamo, tarjeta, 100_000L))
    }

    @Test
    fun `el mensaje de la tarjeta dice a donde ir, no solo que no`() {
        assertTrue(TRANSFER_CARD_BLOCKED.contains(CARD_PAYMENT_CATEGORY))
    }

    // ── Qué clase de traspaso es ──────────────────────────────────────────────

    @Test
    fun `del prestamo a una cuenta es un desembolso, y al reves un abono`() {
        assertEquals(TransferKind.DESEMBOLSO, transferKindFor(prestamo, ahorros))
        assertEquals(TransferKind.ABONO_EXTRAORDINARIO, transferKindFor(ahorros, prestamo))
        assertEquals(TransferKind.ENTRE_CUENTAS, transferKindFor(ahorros, cdt))
    }

    // ── Los signos: lo que este cambio tenía que comprobar antes de existir ───

    /**
     * **La comprobación que decidió la dirección de esta rama.** Un desembolso tiene que subir la
     * deuda y subir el efectivo; un abono extraordinario tiene que bajar los dos. Las patas son
     * eventos comunes y los saldos salen de [signedDelta], así que basta con sumarle a cada cuenta
     * lo que su propia pata le aporta — sin una línea nueva en `computeBalances`.
     */
    @Test
    fun `un desembolso sube la deuda y sube el efectivo, cada saldo una sola vez`() {
        val pedido = request().copy(
            fromAccountId = prestamo.id,
            toAccountId = ahorros.id,
            amount = 257_000_000L,
        )
        val (patePrestamo, pataCuenta) = transferLegsFor(pedido, prestamo, ahorros)

        assertEquals(257_000_000L, signedDelta(prestamo.type, patePrestamo.type, patePrestamo.amount))
        assertEquals(257_000_000L, signedDelta(ahorros.type, pataCuenta.type, pataCuenta.amount))
    }

    @Test
    fun `un abono extraordinario baja la deuda y baja el efectivo`() {
        val pedido = request().copy(
            fromAccountId = ahorros.id,
            toAccountId = prestamo.id,
            amount = 5_000_000L,
        )
        val (pataCuenta, pataPrestamo) = transferLegsFor(pedido, ahorros, prestamo)

        assertEquals(-5_000_000L, signedDelta(ahorros.type, pataCuenta.type, pataCuenta.amount))
        assertEquals(-5_000_000L, signedDelta(prestamo.type, pataPrestamo.type, pataPrestamo.amount))
    }

    /** Ni el desembolso ni el abono son ingreso o gasto del mes: los dos son patas de traspaso. */
    @Test
    fun `ninguna pata de un desembolso ni de un abono cuenta en el mes`() {
        val desembolso = transferLegsFor(request(), prestamo, ahorros)
        val abono = transferLegsFor(request(), ahorros, prestamo)
        listOf(
            prestamo.type to desembolso.first, ahorros.type to desembolso.second,
            ahorros.type to abono.first, prestamo.type to abono.second,
        ).forEach { (tipoCuenta, pata) ->
            assertFalse(isCashFlow(tipoCuenta, pata.type, pata.category))
            assertFalse(pata.countsAsCashFlow)
        }
    }

    @Test
    fun `las patas de un credito se llaman desembolso y abono extraordinario, no traspaso`() {
        val (delPrestamo, aLaCuenta) = transferLegsFor(request(), prestamo, ahorros)
        assertEquals("Desembolso a Ahorros", delPrestamo.description)
        assertEquals("Desembolso de Libranza", aLaCuenta.description)

        val (deLaCuenta, alPrestamo) = transferLegsFor(request(), ahorros, prestamo)
        assertEquals("Abono extraordinario a Libranza", deLaCuenta.description)
        assertEquals("Abono extraordinario desde Ahorros", alPrestamo.description)
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

    // ── La pata que sobrevive al borrado de la otra cuenta ────────────────────

    @Test
    fun `la pata suelta dice que la otra cuenta ya no existe`() {
        assertEquals("Traspaso a CDT · cuenta eliminada", orphanedLegDescription("Traspaso a CDT"))
    }

    /** Borrar dos cuentas, dos traspasos distintos: el sufijo no se encadena. */
    @Test
    fun `agregar el sufijo dos veces no lo repite`() {
        val una = orphanedLegDescription("Traspaso a CDT")
        assertEquals(una, orphanedLegDescription(una))
    }

    /**
     * La columna es `varchar(255)`: si no cabe, lo que se recorta es la descripción y NO el
     * sufijo — el sufijo es justo la parte que explica el renglón.
     */
    @Test
    fun `una descripcion al limite se recorta pero conserva el sufijo`() {
        val larga = "T".repeat(255)
        val resultado = orphanedLegDescription(larga)

        assertEquals(255, resultado.length)
        assertTrue(resultado.endsWith(ORPHANED_LEG_SUFFIX))
    }
}
