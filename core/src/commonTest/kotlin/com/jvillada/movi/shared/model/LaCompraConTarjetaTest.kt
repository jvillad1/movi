package com.jvillada.movi.shared.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Vuelta 71: las cuotas de una compra con tarjeta y qué partes acepta dividirla. */
class LaCompraConTarjetaTest {

    @Test
    fun `la cuota del mes es la division entera, y una cuota es el total`() {
        assertEquals(100_000L, cuotaDeLaCompra(1_200_000L, 12))
        assertEquals(333_333L, cuotaDeLaCompra(1_000_000L, 3))
        assertEquals(45_000L, cuotaDeLaCompra(45_000L, 1))
    }

    @Test
    fun `las cuotas validas van de 1 al tope`() {
        assertTrue(cuotasValidas(1))
        assertTrue(cuotasValidas(MAXIMO_DE_CUOTAS))
        assertFalse(cuotasValidas(0))
        assertFalse(cuotasValidas(MAXIMO_DE_CUOTAS + 1))
        assertEquals(listOf(1, 3, 6, 12, 24, 36), CUOTAS_FRECUENTES)
    }

    private fun gasto(monto: Long) = ParteDelReparto(
        monto = monto,
        tipo = QueFueLaParte.GASTO,
        evento = FinancialEvent(id = "p$monto", accountId = "nu", type = TransactionType.EXPENSE, amount = monto, category = "Mercado", description = "x", timestamp = 0L),
    )

    @Test
    fun `de una tarjeta solo se divide en gastos, de lo demas todo lo de siempre`() {
        val soloGastos = listOf(gasto(1), gasto(2))
        val conCuota = soloGastos + ParteDelReparto(
            monto = 3,
            tipo = QueFueLaParte.CUOTA,
            patas = DosPatasDelAviso(OperacionDelAviso.CUOTA, "nu", "libranza", 3, 0L, "tr", "a", "b"),
        )
        assertTrue(partesQueSeAceptanDesde(AccountType.CREDIT_CARD, soloGastos))
        assertFalse(partesQueSeAceptanDesde(AccountType.CREDIT_CARD, conCuota))
        assertFalse(partesQueSeAceptanDesde(AccountType.CREDIT_CARD, listOf(gasto(1).copy(tipo = QueFueLaParte.A_UN_TERCERO))))
        assertTrue(partesQueSeAceptanDesde(AccountType.SAVINGS, conCuota))
        assertEquals(listOf(QueFueLaParte.GASTO), tiposDeParteDesde(AccountType.CREDIT_CARD))
        assertNull(tiposDeParteDesde(AccountType.CHECKING))
    }
}
