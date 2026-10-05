package com.jvillada.movi.shared.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Lo que cliente y server comparten de un aviso de dos patas: qué operación admiten dos cuentas y qué patas salen. */
class LasDosPatasDelAvisoTest {

    private val ahorros = Account("a", "Bancolombia Ahorros", AccountType.SAVINGS, 0)
    private val fidu = Account("f", "Fiducuenta 9586", AccountType.INVESTMENT, 0)
    private val amex = Account("x", "AMEX 9208", AccountType.CREDIT_CARD, 0)
    private val credito = Account("c", "Vehículo 8761", AccountType.LOAN, 0)
    private val otroCredito = Account("c2", "Libranza 4608", AccountType.LOAN, 0)
    private val enDolares = Account("u", "Master Black 3684 USD", AccountType.CREDIT_CARD, 0, currency = "USD")

    private fun patas(operacion: OperacionDelAviso, origen: Account, destino: Account, monto: Long = 100_000L) = DosPatasDelAviso(
        operacion = operacion, origenId = origen.id, destinoId = destino.id, monto = monto, timestamp = 1_790_000_000_000L,
        transferId = "tr", origenEventId = "ev1", destinoEventId = "ev2",
    )

    @Test
    fun `la operacion la deciden los tipos de las dos cuentas`() {
        assertEquals(OperacionDelAviso.PAGO_DE_TARJETA, operacionEntre(ahorros, amex))
        assertEquals(OperacionDelAviso.CUOTA, operacionEntre(ahorros, credito))
        assertEquals(OperacionDelAviso.TRASPASO, operacionEntre(fidu, ahorros))
        assertEquals(OperacionDelAviso.AVANCE, operacionEntre(amex, ahorros))
        assertNull(operacionEntre(credito, ahorros), "un desembolso se anota desde Agregar, no llega como aviso de estos")
        assertNull(operacionEntre(amex, credito), "dos deudas no")
        assertNull(operacionEntre(credito, otroCredito))
        assertNull(operacionEntre(ahorros, ahorros))
    }

    @Test
    fun `se rechaza lo que no encaja y se aceptan las cuatro filas`() {
        assertNull(validarDosPatas(patas(OperacionDelAviso.PAGO_DE_TARJETA, ahorros, amex), ahorros, amex))
        assertNull(validarDosPatas(patas(OperacionDelAviso.CUOTA, ahorros, credito), ahorros, credito))
        assertNull(validarDosPatas(patas(OperacionDelAviso.TRASPASO, fidu, ahorros), fidu, ahorros))
        assertNull(validarDosPatas(patas(OperacionDelAviso.AVANCE, amex, ahorros), amex, ahorros))

        assertEquals(DOS_PATAS_NO_ENCAJAN, validarDosPatas(patas(OperacionDelAviso.CUOTA, ahorros, amex), ahorros, amex))
        assertNotNull(validarDosPatas(patas(OperacionDelAviso.TRASPASO, fidu, ahorros, monto = 0), fidu, ahorros))
        assertNotNull(validarDosPatas(patas(OperacionDelAviso.TRASPASO, fidu, ahorros), fidu, null))
        // Una tarjeta en dólares sin decir cuánto bajó: la misma regla del pago de cuota.
        assertEquals(faltaElMontoEnLaDeuda("USD"), validarDosPatas(patas(OperacionDelAviso.PAGO_DE_TARJETA, ahorros, enDolares), ahorros, enDolares))
        assertNull(
            validarDosPatas(
                patas(OperacionDelAviso.PAGO_DE_TARJETA, ahorros, enDolares).copy(montoEnLaMonedaDeLaDeuda = 25),
                ahorros, enDolares,
            ),
        )
    }

    @Test
    fun `el avance - la tarjeta como traspaso fuera del mes, la cuenta como desembolso que si entra`() {
        val (deLaTarjeta, deLaCuenta) = patasDelAvance(patas(OperacionDelAviso.AVANCE, amex, ahorros, 6_200_000L), amex, ahorros)
        assertEquals(amex.id, deLaTarjeta.accountId)
        assertEquals(TransactionType.EXPENSE, deLaTarjeta.type)
        assertEquals(TRANSFER_CATEGORY, deLaTarjeta.category)
        assertFalse(isCashFlow(AccountType.CREDIT_CARD, deLaTarjeta.type, deLaTarjeta.category))
        assertFalse(deLaTarjeta.countsAsCashFlow)
        // Con desembolso en la tarjeta contaría como gasto: por eso no va así.
        assertTrue(isCashFlow(AccountType.CREDIT_CARD, TransactionType.EXPENSE, DESEMBOLSO_CATEGORY))

        assertEquals(ahorros.id, deLaCuenta.accountId)
        assertEquals(TransactionType.INCOME, deLaCuenta.type)
        assertEquals(DESEMBOLSO_CATEGORY, deLaCuenta.category)
        assertTrue(deLaCuenta.countsAsCashFlow, "suma en «Entró»")
        assertEquals("tr", deLaTarjeta.transferId)
        assertEquals("tr", deLaCuenta.transferId)
        assertEquals("ev2", patas(OperacionDelAviso.AVANCE, amex, ahorros).pataDelAviso, "el aviso queda con la pata de la cuenta")
        assertEquals("ev1", patas(OperacionDelAviso.PAGO_DE_TARJETA, ahorros, amex).pataDelAviso)
    }

    @Test
    fun `el resumen dice de donde sale y a donde entra`() {
        val mb = Account("m", "Master Black 3684", AccountType.CREDIT_CARD, 0)
        assertEquals("Sale de Bancolombia Ahorros · entra a Master Black 3684 como pago", resumenDeLasDosPatas(OperacionDelAviso.PAGO_DE_TARJETA, ahorros, mb))
        assertEquals("Sale de Bancolombia Ahorros · entra a Vehículo 8761 como cuota", resumenDeLasDosPatas(OperacionDelAviso.CUOTA, ahorros, credito))
        assertEquals("Sale de Fiducuenta 9586 · entra a Bancolombia Ahorros como traspaso", resumenDeLasDosPatas(OperacionDelAviso.TRASPASO, fidu, ahorros))
        assertEquals("Sale de AMEX 9208 como avance · entra a Bancolombia Ahorros", resumenDeLasDosPatas(OperacionDelAviso.AVANCE, amex, ahorros))
    }
}
