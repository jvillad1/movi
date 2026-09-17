package com.jvillada.movi.server.reminders

import com.jvillada.movi.server.fx.TasaUsdCop
import com.jvillada.movi.server.push.buildPushPayload
import com.jvillada.movi.shared.model.CardTerms
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * # Una tarjeta en dólares no debe pesos
 *
 * `TarjetaNoInventaElPagoTest` fijó que el saldo de una tarjeta no se anuncie como si fuera la
 * cuota. Faltaba la otra mitad: **en qué moneda está ese saldo**. `virtualRuleForCard` pone en
 * `amount` la deuda en la moneda de la CUENTA —una tarjeta en dólares debe dólares— y la regla no
 * traía la moneda, así que los tres renderers la formateaban como pesos:
 *
 *  - Recurrentes y el Inicio: «saldo $1.200»
 *  - el correo, peor todavía, rotulándolo: «saldo $1.200 COP»
 *  - el push, el canal que suena: «saldo $1.200»
 *
 * La deuda real son unos $4.800.000. No es un error de redondeo: es un factor de 4.000, y estaba
 * escrito al lado de las tarjetas en pesos, que sí decían la verdad.
 *
 * **No se convierte, se dice en su moneda.** Es al revés que `pagoMinimoCop`, y por un motivo
 * concreto: aquel alimenta un total en pesos (por eso `minimoEnPesos` prefiere `null` antes que
 * usar la tasa de respaldo de $4.000), y esto es la cifra que el dueño va a reconocer contra su
 * extracto — que está en dólares. La prueba de abajo lo fija incluso **con** tasa de respaldo:
 * ninguna de las dos cosas que se muestran depende de la TRM.
 */
class TarjetaEnDolaresTest {

    private val amexUsd = CardTerms(
        accountId = "acc-amex-usd",
        bank = "American Express",
        paymentDay = 2,
        pagoMinimo = 60,
    )

    /** La misma tarjeta, pero en pesos: el contracaso de cada afirmación. */
    private val visaCop = CardTerms(
        accountId = "acc-visa",
        bank = "Bancolombia",
        paymentDay = 2,
        pagoMinimo = 1_843_014,
    )

    private val enDolares = virtualRuleForCard(
        amexUsd, "AMEX Internacional", currentDebt = 1_200, accountCurrency = "USD",
        tasa = TasaUsdCop(valor = 4_012.5, esRespaldo = false),
    )

    private val enPesos = virtualRuleForCard(
        visaCop, "Visa 1254", currentDebt = 27_501_150, accountCurrency = "COP", tasa = null,
    )

    /** 2 de agosto: el día de pago de las dos. */
    private val hoy = java.time.LocalDate.of(2026, 8, 2)

    @Test
    fun la_regla_de_una_tarjeta_viaja_con_su_moneda() {
        assertEquals("USD", enDolares.currency)
        assertEquals(1_200L, enDolares.amount, "el saldo NO se convierte: es lo que dice el extracto")
        assertEquals("COP", enPesos.currency, "y una tarjeta en pesos sigue diciendo COP")
    }

    /**
     * El mínimo sí se convierte, y eso no cambió: es el número que entra al «Flujo libre», que está
     * en pesos. Las dos decisiones opuestas conviven en la misma regla a propósito.
     */
    @Test
    fun el_minimo_se_sigue_convirtiendo_aunque_el_saldo_no() {
        assertEquals((60 * 4_012.5).toLong(), enDolares.pagoMinimoCop)
        assertEquals(1_843_014L, enPesos.pagoMinimoCop)
    }

    /**
     * **Con tasa de respaldo, el mínimo desaparece y el saldo sigue estando.** `FxRateService`
     * devuelve $4.000 sin avisar cuando la fuente oficial se cae, y `minimoEnPesos` prefiere el
     * `null` antes que restar un número inventado del disponible. El saldo no corre esa suerte
     * porque no se convierte: se muestra en dólares, que es como está.
     */
    @Test
    fun con_tasa_de_respaldo_el_saldo_se_muestra_igual() {
        val conRespaldo = virtualRuleForCard(
            amexUsd, "AMEX Internacional", currentDebt = 1_200, accountCurrency = "USD",
            tasa = TasaUsdCop(valor = 4_000.0, esRespaldo = true),
        )

        assertEquals(null, conRespaldo.pagoMinimoCop, "no se inventa una conversión con una tasa que no es una tasa")
        assertEquals("USD", conRespaldo.currency)
        assertEquals("saldo US\$1,200", montoConMoneda(conRespaldo), "el saldo no depende de la TRM")
    }

    // ── El correo ─────────────────────────────────────────────────────────────

    @Test
    fun el_correo_no_rotula_como_pesos_una_deuda_en_dolares() {
        val html = buildHtmlEmail(listOf(enDolares), hoy, leadDays = 3)

        assertTrue(html.contains("saldo US\$1,200 · revisa tu extracto"), html)
        assertFalse(
            html.contains("\$1,200 COP"),
            "afirmar «COP» sobre una deuda de US\$1.200 es decir que debe \$1.200 y no \$4.800.000: $html",
        )
    }

    @Test
    fun el_correo_de_una_tarjeta_en_pesos_no_cambio() {
        val html = buildHtmlEmail(listOf(enPesos), hoy, leadDays = 3)

        assertTrue(html.contains("saldo \$27,501,150 COP · revisa tu extracto"), html)
    }

    // ── El push ───────────────────────────────────────────────────────────────

    @Test
    fun el_push_dice_la_moneda_de_la_tarjeta() {
        val body = buildPushPayload(listOf(enDolares), hoy, leadDays = 3)

        assertTrue(body.contains("AMEX Internacional — saldo US\$1.200"), body)
        assertFalse(
            body.contains("— saldo \$1.200"),
            "en el canal que suena, «saldo \$1.200» por una deuda de US\$1.200: $body",
        )
    }

    @Test
    fun el_push_de_una_tarjeta_en_pesos_no_cambio() {
        val body = buildPushPayload(listOf(enPesos), hoy, leadDays = 3)

        assertTrue(body.contains("Visa 1254 — saldo \$27.501.150"), body)
        assertFalse(body.contains("US\$"), body)
    }
}
