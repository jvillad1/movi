package com.jvillada.movi.shared.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * **Cómo se presenta un movimiento** (vuelta 50): una prueba por caso, con las patas que escriben los
 * constructores de verdad ([pagoDeCuotaLegs], [transferLegsFor]), así que si la regla de plata cambia,
 * esto lo dice. La decisión sigue a [isCashFlow]: no hay una regla nueva.
 */
class ComoSePresentaTest {

    private val ahorros = Account("a1", "Bancolombia Ahorros", AccountType.SAVINGS, 15_534_069)
    private val cdt = Account("i1", "CDT", AccountType.INVESTMENT, 5_000_000)
    private val credito = Account("l1", "Libre inversión 9695", AccountType.LOAN, 40_710_555)
    private val tarjeta = Account("c1", "AMEX 9208", AccountType.CREDIT_CARD, 1_818_701)
    private val tipos = listOf(ahorros, cdt, credito, tarjeta).associate { it.id to it.type }

    private fun pago(desde: Account, deuda: Account, monto: Long) = pagoDeCuotaLegs(
        CreatePagoDeCuotaRequest(
            fromAccountId = desde.id,
            debtAccountId = deuda.id,
            amount = monto,
            timestamp = 1_790_000_000_000L,
            transferId = "tr",
            fromEventId = "sale",
            toEventId = "entra",
        ),
        desde,
        deuda,
        desglosarCuota(monto, deuda.type, deuda.balance, 11.27, null, null, yaCobradoEnElMes = 0L),
    )

    private fun traspaso(desde: Account, hacia: Account, monto: Long) = transferLegsFor(
        CreateTransferRequest(
            transferId = "tr",
            fromEventId = "sale",
            toEventId = "entra",
            fromAccountId = desde.id,
            toAccountId = hacia.id,
            amount = monto,
            timestamp = 1_790_000_000_000L,
        ),
        desde,
        hacia,
    )

    @Test
    fun `la cuota de un credito es un gasto, por la pata que sale de tu cuenta`() {
        val (dinero, deuda) = pago(ahorros, credito, 980_000L)

        assertEquals(PresentacionDelMovimiento.GASTO, comoSePresentaElPar(dinero, deuda, tipos))
        assertSame(dinero, laPataQueCuenta(dinero, deuda, tipos))
        assertEquals(980_000L, laPataQueCuenta(dinero, deuda, tipos)!!.amount)
        // Cada pata por su lado: la del dinero es el gasto; la del crédito no cuenta (baja la deuda).
        assertEquals(PresentacionDelMovimiento.GASTO, comoSePresenta(dinero, AccountType.SAVINGS))
        assertEquals(PresentacionDelMovimiento.NEUTRO, comoSePresenta(deuda, AccountType.LOAN))
        // Y sin los tipos de cuenta todavía, manda `countsAsCashFlow` de cada pata: lo mismo.
        assertEquals(PresentacionDelMovimiento.GASTO, comoSePresentaElPar(dinero, deuda))
        // Es la regla de «Gastos»: la fila y el total dicen lo mismo.
        assertTrue(esGastoDelPeriodo(dinero, AccountType.SAVINGS))
    }

    @Test
    fun `el pago de una tarjeta es neutro, no hay pata que cuente`() {
        val (dinero, deuda) = pago(ahorros, tarjeta, 100_000L)

        assertEquals(CARD_PAYMENT_CATEGORY, dinero.category)
        assertEquals(PresentacionDelMovimiento.NEUTRO, comoSePresentaElPar(dinero, deuda, tipos))
        assertNull(laPataQueCuenta(dinero, deuda, tipos))
        assertEquals(PresentacionDelMovimiento.NEUTRO, comoSePresenta(dinero, AccountType.SAVINGS))
        assertTrue(!esGastoDelPeriodo(dinero, AccountType.SAVINGS))
    }

    @Test
    fun `un traspaso entre cuentas propias es neutro`() {
        val (sale, entra) = traspaso(ahorros, cdt, 50_000L)

        assertEquals(PresentacionDelMovimiento.NEUTRO, comoSePresentaElPar(sale, entra, tipos))
        assertNull(laPataQueCuenta(sale, entra, tipos))
        assertEquals(PresentacionDelMovimiento.NEUTRO, comoSePresentaElPar(sale, entra))
    }

    @Test
    fun `el desembolso de un credito es un ingreso, por la pata que entra a tu cuenta`() {
        val (delCredito, alDinero) = traspaso(credito, ahorros, 10_000_000L)

        assertEquals(DESEMBOLSO_CATEGORY, alDinero.category)
        assertEquals(PresentacionDelMovimiento.INGRESO, comoSePresentaElPar(delCredito, alDinero, tipos))
        assertSame(alDinero, laPataQueCuenta(delCredito, alDinero, tipos))
        assertEquals(PresentacionDelMovimiento.NEUTRO, comoSePresenta(delCredito, AccountType.LOAN))
        assertEquals(PresentacionDelMovimiento.INGRESO, comoSePresentaElPar(delCredito, alDinero))
    }

    @Test
    fun `el abono extraordinario a un credito es neutro, es un traspaso`() {
        val (sale, alCredito) = traspaso(ahorros, credito, 2_000_000L)

        assertEquals(TRANSFER_CATEGORY, sale.category)
        assertEquals(PresentacionDelMovimiento.NEUTRO, comoSePresentaElPar(sale, alCredito, tipos))
        assertNull(laPataQueCuenta(sale, alCredito, tipos))
        assertTrue(!esGastoDelPeriodo(sale, AccountType.SAVINGS))
    }

    @Test
    fun `un movimiento suelto es gasto o ingreso, y no mira la moneda ni Por confirmar`() {
        val mercado = FinancialEvent(
            id = "m", accountId = ahorros.id, type = TransactionType.EXPENSE, amount = 27_500L,
            category = "Mercado", description = "Éxito", timestamp = 0L,
            reconciliationStatus = ReconciliationStatus.RECONCILED,
        )
        assertEquals(PresentacionDelMovimiento.GASTO, comoSePresenta(mercado))
        assertTrue(esGastoDelPeriodo(mercado, AccountType.SAVINGS))
        // Un cargo en dólares o sin confirmar se sigue viendo como gasto; el total en pesos lo deja afuera.
        val enDolares = mercado.copy(currency = "USD")
        val sinConfirmar = mercado.copy(reconciliationStatus = ReconciliationStatus.UNCONFIRMED)
        assertEquals(PresentacionDelMovimiento.GASTO, comoSePresenta(enDolares))
        assertEquals(PresentacionDelMovimiento.GASTO, comoSePresenta(sinConfirmar))
        assertTrue(!esGastoDelPeriodo(enDolares, AccountType.SAVINGS))
        assertTrue(!esGastoDelPeriodo(sinConfirmar, AccountType.SAVINGS))

        val nomina = mercado.copy(id = "n", type = TransactionType.INCOME, category = "Salario")
        assertEquals(PresentacionDelMovimiento.INGRESO, comoSePresenta(nomina))
        // Lo que el server ya marcó como fuera del flujo (un ajuste, una apertura) es neutro.
        assertEquals(PresentacionDelMovimiento.NEUTRO, comoSePresenta(mercado.copy(countsAsCashFlow = false)))
    }
}
