package com.jvillada.movi.ui.recurrentes

import com.jvillada.movi.shared.model.CARD_PAYMENT_CATEGORY
import com.jvillada.movi.shared.model.CARD_RULE_PREFIX
import com.jvillada.movi.shared.model.CREDIT_RULE_PREFIX
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.RecurringRule
import com.jvillada.movi.shared.model.SubscriptionsResult
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.isCashFlow
import com.jvillada.movi.shared.model.AccountType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull

/**
 * **El pago de una tarjeta se ve en «Recurrentes» y NO entra al «Flujo libre».**
 *
 * El dueño: *«en recurrentes no veo el pago de la cuota de las tarjetas de crédito, deberían
 * estar»*. Puesta la tensión sobre la mesa —mostrar algo en una lista no es contarlo como gasto—
 * eligió las dos cosas: que se vea, marcado como recurrente, y que siga fuera de «Gastos del mes» y
 * del total de «Flujo libre». Las compras ya contaron cuando se hicieron.
 *
 * Este archivo fija la mitad que **no** se ve, que es la que cuesta plata si se rompe: que un pago
 * de tarjeta visible no mueva ni un peso del total. Hay dos razones por las que no lo mueve, y las
 * dos se prueban acá:
 *
 * 1. El total se calcula sobre **reglas y suscripciones** ([resumenRecurrentes]), no sobre
 *    movimientos: reconocer un evento no tiene por dónde llegar hasta ahí.
 * 2. Y si llegara, la regla sintética de una tarjeta ya está excluida por partida doble
 *    ([cuentaComoCompromisoMensual]). Con razón: su monto es el **saldo** de la tarjeta, así que
 *    sumarla diría que la AMEX le cuesta $27.501.150 al mes.
 */
class PagoDeTarjetaEnRecurrentesTest {

    // ── Lo que él tiene anotado, con sus cifras reales ───────────────────────

    private fun evento(
        id: String,
        cuenta: AccountType,
        type: TransactionType,
        description: String,
        amount: Long,
    ) = FinancialEvent(
        id = id,
        accountId = "acc_${cuenta.name.lowercase()}",
        type = type,
        amount = amount,
        category = CARD_PAYMENT_CATEGORY,
        description = description,
        timestamp = 1_756_684_800_000L,
        transferId = "tr_$id",
        countsAsCashFlow = isCashFlow(cuenta, type, CARD_PAYMENT_CATEGORY),
    )

    /** El pago de la AMEX del 30 de agosto: la pata del dinero. */
    private val amexDinero =
        evento("amex", AccountType.SAVINGS, TransactionType.EXPENSE, "Pago de AMEX", 1_008_902L)

    /** El de la Nu del 5 de septiembre. */
    private val nuDinero =
        evento("nu", AccountType.SAVINGS, TransactionType.EXPENSE, "Pago de Nu", 115_113L)

    private val vehiculo = RecurringRule(
        id = "${CREDIT_RULE_PREFIX}acc-carro",
        name = "Cuota Vehículo 4083",
        category = "Créditos",
        amount = 4_215_223L,
        dayOfMonth = 1,
        type = TransactionType.EXPENSE,
    )

    /** La regla sintética de la AMEX: su monto es el SALDO de la tarjeta, no un pago. */
    private val amexComoRegla = RecurringRule(
        id = "${CARD_RULE_PREFIX}acc-amex",
        name = "Pago tarjeta AMEX 9208",
        category = "Créditos",
        amount = 27_501_150L,
        dayOfMonth = 15,
        type = TransactionType.EXPENSE,
        montoEsSaldo = true,
    )

    private val sinSuscripciones = SubscriptionsResult(emptyList(), monthlyTotalCop = 0L)

    // ── Se ve ────────────────────────────────────────────────────────────────

    @Test
    fun `los dos pagos que tiene anotados se reconocen como recurrentes`() {
        assertNotNull(nombreRecurrenteDe(amexDinero, emptyList(), emptyList()))
        assertNotNull(nombreRecurrenteDe(nuDinero, emptyList(), emptyList()))
    }

    // ── Y no cuenta ──────────────────────────────────────────────────────────

    /**
     * **La regla de plata intacta**: la pata del dinero de un pago de tarjeta no es flujo de caja,
     * así que ni «Gastos del mes» ni el «Flujo del día» la suman, y la fila se pinta sin signo.
     * Esto no lo decide [nombreDePagoDeTarjeta] —que solo contesta si el movimiento se repite— sino
     * `isCashFlow`, y esta prueba está acá para que se rompa si alguien los junta.
     */
    @Test
    fun `reconocerlo no lo convierte en un gasto`() {
        assertFalse(amexDinero.countsAsCashFlow)
        assertFalse(nuDinero.countsAsCashFlow)
        assertFalse(isCashFlow(AccountType.SAVINGS, TransactionType.EXPENSE, CARD_PAYMENT_CATEGORY))
    }

    /**
     * El total del «Flujo libre» sale de reglas y suscripciones, y un movimiento visible no es
     * ninguna de las dos cosas: con sus dos pagos anotados, el total sigue siendo la cuota del
     * carro y nada más.
     */
    @Test
    fun `el Flujo libre no se mueve porque haya pagos de tarjeta a la vista`() {
        val total = resumenRecurrentes(listOf(vehiculo), sinSuscripciones)

        assertEquals(4_215_223L, total.gastos)
        assertEquals(-4_215_223L, total.flujoLibre)
        assertEquals(1, total.items.size)
        // Y los montos de los pagos no aparecen por ninguna puerta.
        assertFalse(total.gastos == 4_215_223L + amexDinero.amount + nuDinero.amount)
    }

    /**
     * Y la puerta que sí llega hasta el total —la regla sintética de la tarjeta— sigue cerrada.
     * Movi **no estima el pago mínimo** a propósito, así que lo único que esa regla lleva es el
     * saldo: sumarlo diría que la AMEX cuesta $27.501.150 al mes.
     */
    @Test
    fun `la regla sintetica de la tarjeta sigue afuera del total`() {
        assertFalse(cuentaComoCompromisoMensual(amexComoRegla))

        val total = resumenRecurrentes(listOf(vehiculo, amexComoRegla), sinSuscripciones)

        assertEquals(4_215_223L, total.gastos)
        assertFalse(total.gastos == 31_716_373L, "sumó el saldo de la AMEX")
        assertEquals(1, total.items.size)
    }
}
