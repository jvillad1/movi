package com.jvillada.movi.shared.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * **Corregir cuánto debés no es gastar plata.**
 *
 * Lo encontró el dueño mirando sus propios movimientos: cuatro ajustes de saldo el mismo día,
 * tres pintados en gris y **uno en rojo**, restando del «Flujo del día». Los cuatro eran la misma
 * clase de corrección; el que restaba era el de una tarjeta.
 *
 * El motivo era que [isCashFlow] nunca nombró a [ADJUSTMENT_CATEGORY]. En una cuenta LOAN el
 * ajuste quedaba afuera igual, porque esas cuentas están excluidas enteras — o sea que la regla
 * se cumplía **por accidente**. En una tarjeta no: el ajuste que sube la deuda tiene tipo
 * EXPENSE, y esa es exactamente la rama que sí cuenta.
 *
 * El KDoc de `BalanceAdjustment` ya afirmaba «el ajuste ya no cuenta como flujo de caja (ver
 * `isCashFlow`)». Era verdad a medias, y la mitad falsa es la que le ensuciaba los gastos del mes.
 */
class AjusteDeSaldoNoEsGastoTest {

    /** Las cuatro combinaciones: los dos tipos, en los dos tipos de cuenta de deuda. */
    @Test
    fun `un ajuste de saldo no es flujo de caja en ninguna cuenta`() {
        listOf(AccountType.LOAN, AccountType.CREDIT_CARD, AccountType.SAVINGS).forEach { cuenta ->
            listOf(TransactionType.EXPENSE, TransactionType.INCOME).forEach { tipo ->
                assertFalse(
                    isCashFlow(cuenta, tipo, ADJUSTMENT_CATEGORY),
                    "un ajuste en $cuenta con tipo $tipo entró al flujo de caja",
                )
            }
        }
    }

    /**
     * El caso EXACTO del dueño, y el único que estaba roto: la tarjeta en dólares, cuyo ajuste
     * de US$208 aparecía como «−$208» restando de un total en pesos.
     */
    @Test
    fun `el ajuste que subio la deuda de una tarjeta ya no cuenta como compra`() {
        assertFalse(isCashFlow(AccountType.CREDIT_CARD, TransactionType.EXPENSE, ADJUSTMENT_CATEGORY))
    }

    /**
     * **Y una compra de verdad en la misma tarjeta sigue contando.** Sin esto, la corrección de
     * arriba podría haberse hecho apagando la rama entera de las tarjetas, que es justo la que
     * hace que un gasto con tarjeta cuente como gasto del mes.
     */
    @Test
    fun `una compra con tarjeta sigue siendo un gasto del mes`() {
        assertEquals(
            true,
            isCashFlow(AccountType.CREDIT_CARD, TransactionType.EXPENSE, "Entretenimiento"),
        )
    }

    /** Y pagar la tarjeta sigue sin contar: las compras ya contaron cuando se hicieron. */
    @Test
    fun `pagar la tarjeta sigue sin contar`() {
        assertFalse(isCashFlow(AccountType.CREDIT_CARD, TransactionType.EXPENSE, CARD_PAYMENT_CATEGORY))
    }
}
