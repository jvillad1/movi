package com.jvillada.movi.ui.transactions

import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.CARD_PAYMENT_CATEGORY
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.TransactionType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Ola U — «Flujo del día $0» no puede quedar sin explicación cuando salió plata de verdad.
 *
 * El caso real del dueño: pagó dos tarjetas desde Bancolombia Ahorros el mismo día ($386.902 +
 * $1.542.634 = $1.929.536) y Movimientos le dijo «Flujo del día $0» — cierto para el flujo del
 * mes, pero leído sin explicación se ve como una mentira.
 */
class PagoDeTarjetaEnElDiaTest {

    private val cuentaDeAhorros = "acc-ahorros"
    private val cuentaDeTarjeta = "acc-tarjeta"
    private val accountTypes = mapOf(
        cuentaDeAhorros to AccountType.SAVINGS,
        cuentaDeTarjeta to AccountType.CREDIT_CARD,
    )

    private fun ev(
        id: String,
        accountId: String,
        type: TransactionType,
        category: String,
        amount: Long,
        currency: String = "COP",
    ) = FinancialEvent(
        id = id,
        accountId = accountId,
        type = type,
        amount = amount,
        currency = currency,
        category = category,
        description = id,
        timestamp = 0L,
    )

    @Test
    fun `suma los dos pagos de tarjeta del dia del dueno`() {
        val items = listOf(
            ev("p1", cuentaDeAhorros, TransactionType.EXPENSE, CARD_PAYMENT_CATEGORY, 386_902L),
            ev("p2", cuentaDeAhorros, TransactionType.EXPENSE, CARD_PAYMENT_CATEGORY, 1_542_634L),
        )
        assertEquals(1_929_536L, montoPagoDeTarjetaEnElDia(items, accountTypes))
        assertEquals(
            "Además, \$1.929.536 salieron a pagar tarjeta (ya contado al comprar)",
            textoPagoDeTarjetaEnElDia(montoPagoDeTarjetaEnElDia(items, accountTypes)),
        )
    }

    @Test
    fun `un dia sin pagos de tarjeta no agrega nada`() {
        val items = listOf(
            ev("g1", cuentaDeAhorros, TransactionType.EXPENSE, "Mercado", 50_000L),
            ev("i1", cuentaDeAhorros, TransactionType.INCOME, "Salario", 4_000_000L),
        )
        assertEquals(0L, montoPagoDeTarjetaEnElDia(items, accountTypes))
        assertNull(textoPagoDeTarjetaEnElDia(montoPagoDeTarjetaEnElDia(items, accountTypes)))
    }

    @Test
    fun `no cuenta la pata de la propia tarjeta, solo la que sale de Tu plata`() {
        val items = listOf(
            // La pata que baja la deuda en la tarjeta: INCOME, y en la cuenta equivocada.
            ev("t1", cuentaDeTarjeta, TransactionType.INCOME, CARD_PAYMENT_CATEGORY, 1_929_536L),
        )
        assertEquals(0L, montoPagoDeTarjetaEnElDia(items, accountTypes))
        assertNull(textoPagoDeTarjetaEnElDia(montoPagoDeTarjetaEnElDia(items, accountTypes)))
    }

    @Test
    fun `un pago de tarjeta en dolares no se suma, igual que Flujo del dia`() {
        val items = listOf(
            ev("u1", cuentaDeAhorros, TransactionType.EXPENSE, CARD_PAYMENT_CATEGORY, 20L, currency = "USD"),
        )
        assertEquals(0L, montoPagoDeTarjetaEnElDia(items, accountTypes))
    }

    @Test
    fun `un dia con gastos normales Y un pago de tarjeta suma solo el pago de tarjeta`() {
        val items = listOf(
            ev("g1", cuentaDeAhorros, TransactionType.EXPENSE, "Mercado", 50_000L),
            ev("p1", cuentaDeAhorros, TransactionType.EXPENSE, CARD_PAYMENT_CATEGORY, 386_902L),
        )
        assertEquals(386_902L, montoPagoDeTarjetaEnElDia(items, accountTypes))
        assertEquals(
            "Además, \$386.902 salieron a pagar tarjeta (ya contado al comprar)",
            textoPagoDeTarjetaEnElDia(montoPagoDeTarjetaEnElDia(items, accountTypes)),
        )
    }

    @Test
    fun `una cuenta desconocida no cuenta, igual que si no fuera Tu plata`() {
        val items = listOf(
            ev("p1", "acc-sin-tipo", TransactionType.EXPENSE, CARD_PAYMENT_CATEGORY, 100_000L),
        )
        assertEquals(0L, montoPagoDeTarjetaEnElDia(items, emptyMap()))
    }
}
