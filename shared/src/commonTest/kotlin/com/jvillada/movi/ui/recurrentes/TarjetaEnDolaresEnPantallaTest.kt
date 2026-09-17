package com.jvillada.movi.ui.recurrentes

import com.jvillada.movi.shared.model.CARD_RULE_PREFIX
import com.jvillada.movi.shared.model.OccurrenceState
import com.jvillada.movi.shared.model.PaymentStatus
import com.jvillada.movi.shared.model.PeriodSettings
import com.jvillada.movi.shared.model.PeriodoFinanciero
import com.jvillada.movi.shared.model.RecurringRule
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.UpcomingPayment
import com.jvillada.movi.ui.dashboard.checklistDelPeriodo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * # El saldo de una tarjeta se dice en la moneda de la tarjeta
 *
 * Hermano de `MontoEsSaldoEnPantallaTest`: ahí se fijó que el monto de una tarjeta se diga «saldo»
 * y no como si fuera la cuota; acá, **en qué moneda**. `virtualRuleForCard` pone la deuda en la
 * moneda de la cuenta, y mientras la regla no llevó la moneda encima esta pantalla la formateaba
 * como pesos: US$1.200 —unos $4.800.000— se leían «saldo $1.200», en la misma lista donde las
 * tarjetas en pesos decían bien la suya.
 *
 * (El correo y el push viven en `:server` y lo fija `TarjetaEnDolaresTest`, con las mismas cifras.)
 */
class TarjetaEnDolaresEnPantallaTest {

    private val enDolares = RecurringRule(
        id = "${CARD_RULE_PREFIX}acc-amex-usd",
        name = "Pago tarjeta AMEX Internacional",
        category = "Créditos",
        amount = 1_200,
        currency = "USD",
        dayOfMonth = 2,
        type = TransactionType.EXPENSE,
        montoEsSaldo = true,
    )

    private val enPesos = RecurringRule(
        id = "${CARD_RULE_PREFIX}acc-visa",
        name = "Pago tarjeta Visa 1254",
        category = "Créditos",
        amount = 27_501_150,
        dayOfMonth = 2,
        type = TransactionType.EXPENSE,
        montoEsSaldo = true,
    )

    @Test
    fun el_saldo_en_dolares_se_dice_en_dolares() {
        assertEquals("saldo US\$1.200", textoDelMonto(enDolares))
        // Un saldo nunca lleva signo, y eso no cambia por la moneda.
        assertEquals("saldo US\$1.200", textoDelMonto(enDolares, conSigno = true))
    }

    @Test
    fun una_tarjeta_en_pesos_se_sigue_diciendo_igual() {
        assertEquals("saldo \$27.501.150", textoDelMonto(enPesos))
    }

    /**
     * La regla que el dueño escribió no tiene moneda en la tabla, así que nace en COP y su texto
     * es exactamente el de antes. Es el contracaso que evita que «arreglar la moneda» haya sido
     * cambiarle el formato a toda la app.
     */
    @Test
    fun una_regla_escrita_a_mano_sigue_en_pesos() {
        val arriendo = RecurringRule(
            id = "r-arriendo", name = "Arriendo", category = "Vivienda", amount = 1_800_000,
            dayOfMonth = 5, type = TransactionType.EXPENSE,
        )

        assertEquals("COP", arriendo.currency)
        assertEquals("\$1.800.000", textoDelMonto(arriendo))
        assertEquals("−\$1.800.000", textoDelMonto(arriendo, conSigno = true))
    }

    /**
     * **Y la moneda llega hasta el checklist del período**, que es el otro lugar del Inicio donde
     * este saldo se pinta. Sin el dato, la fila usaba el formato compacto de pesos («$1.200», o
     * «$1,2M» si la deuda fuera grande) sobre una cifra en dólares.
     */
    @Test
    fun el_checklist_del_periodo_arrastra_la_moneda() {
        val checklist = checklistDelPeriodo(
            upcoming = listOf(
                UpcomingPayment(
                    rule = enDolares,
                    dueDate = "2026-09-02",
                    daysUntil = 1,
                    status = PaymentStatus.DUE_SOON,
                ),
            ),
            ocurrencias = emptyList<OccurrenceState>(),
            periodo = PeriodoFinanciero(2026, 9),
            settings = PeriodSettings(),
        )

        assertEquals(1, checklist.size, "el pago tiene que caer dentro del período")
        assertEquals("USD", checklist[0].moneda)
        assertTrue(checklist[0].montoEsSaldo)
    }
}
