package com.jvillada.movi.server.reminders

import com.jvillada.movi.shared.model.CardTerms
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * El monto de una tarjeta es su SALDO, y quien lo muestre tiene que saberlo.
 *
 * El dueño, mirando «Próximos pagos» en el Inicio: *«muestra como si fuese a pagar absolutamente
 * toda la tarjeta y no el pago mínimo»*. Movi le anunciaba **$27.501.150** como su próximo pago;
 * el mínimo de esa tarjeta ronda el 5 %.
 *
 * La decisión fue **no estimar** el mínimo. Cada banco lo calcula distinto —y cambia con los
 * diferidos y los avances—, así que un porcentaje inventado sería un número sobre su plata que él
 * no puede verificar. Fue su propia sugerencia: «si no lo quieres estimar, entonces simplemente no
 * mencionar el monto del pago».
 */
class TarjetaNoInventaElPagoTest {

    private fun terms(dia: Int = 2) = CardTerms(
        accountId = "acc-amex",
        bank = "American Express",
        paymentDay = dia,
    )

    @Test
    fun la_regla_de_una_tarjeta_declara_que_su_monto_es_un_saldo() {
        val regla = virtualRuleForCard(terms(), "AMEX 9208", currentDebt = 19_818_701)

        assertTrue(regla.montoEsSaldo, "sin esta bandera, la UI lo pinta como si fuera la cuota")
        assertEquals(19_818_701, regla.amount, "el saldo se conserva: es la mejor pista para emparejar el pago")
    }

    @Test
    fun la_de_un_credito_NO() {
        // Un préstamo sí tiene cuota fija, y ahí el monto es exactamente lo que va a salir.
        val credito = com.jvillada.movi.shared.model.CreditTerms(
            accountId = "acc-carro",
            bank = "Occidente",
            principal = 180_000_000,
            rateEa = 18.16,
            termMonths = 72,
            installment = 4_215_223,
            dayOfMonth = 17,
            startDate = "2026-06-16",
        )

        val regla = virtualRuleFor(credito, "Vehículo 4083")

        assertFalse(regla.montoEsSaldo)
        assertEquals(4_215_223, regla.amount)
    }

    // ── Y que alguien la MIRE ─────────────────────────────────────────────────
    //
    // Marcar la regla no sirve de nada si un renderer no lee la marca, y eso fue exactamente lo
    // que pasó: de los cuatro que pintan el monto, el push —el canal que suena— siguió
    // anunciando la deuda entera como el pago del mes. Ningún test lo notó porque los tests
    // miraban que la regla naciera marcada, no que alguien hiciera algo con la marca. (Los dos
    // renderers de `:shared` —el Inicio y Recurrentes— los fija `MontoEsSaldoEnPantallaTest`.)

    private val tarjetaDelDueno = virtualRuleForCard(terms(), "AMEX 9208", currentDebt = 27_501_150)

    private val cuotaDelCarro = virtualRuleFor(
        com.jvillada.movi.shared.model.CreditTerms(
            accountId = "acc-carro",
            bank = "Occidente",
            principal = 180_000_000,
            rateEa = 18.16,
            termMonths = 72,
            installment = 4_215_223,
            dayOfMonth = 17,
            startDate = "2026-06-16",
        ),
        "Vehículo 4083",
    )

    /** 2 de agosto: el día de pago de la tarjeta y el vencimiento de la cuota, los dos cerca. */
    private val hoy = java.time.LocalDate.of(2026, 8, 2)

    @Test
    fun el_push_dice_saldo_y_no_el_pago_del_mes() {
        // El defecto tal cual llegaba al teléfono: «Pago tarjeta AMEX 9208 — $27.501.150
        // (vence hoy)», bajo el título «Pagos próximos en movi».
        val body = com.jvillada.movi.server.push.buildPushPayload(listOf(tarjetaDelDueno), hoy, leadDays = 3)

        assertTrue(
            body.contains("Pago tarjeta AMEX 9208 — saldo ${'$'}27.501.150"),
            "el push tiene que decir «saldo», no anunciar la deuda entera como el pago: $body",
        )
        assertFalse(
            body.contains("9208 — ${'$'}27.501.150"),
            "sin la palabra «saldo» la notificación afirma que este mes le salen ${'$'}27,5M",
        )
    }

    @Test
    fun el_push_de_una_cuota_no_cambia() {
        // La garantía en la otra dirección: un préstamo sí tiene cuota fija y su push sigue
        // diciendo la cifra pelada, que es exactamente lo que va a salir de la cuenta.
        val body = com.jvillada.movi.server.push.buildPushPayload(listOf(cuotaDelCarro), hoy, leadDays = 3)

        assertTrue(body.contains("Vehículo 4083 — ${'$'}4.215.223"), body)
        assertFalse(body.contains("saldo"), body)
    }

    @Test
    fun el_correo_dice_saldo_y_manda_a_revisar_el_extracto() {
        val html = buildHtmlEmail(listOf(tarjetaDelDueno), hoy, leadDays = 3)

        assertTrue(html.contains("saldo ${'$'}27,501,150 COP · revisa tu extracto"), html)
    }

    @Test
    fun el_correo_de_una_cuota_no_cambia() {
        val html = buildHtmlEmail(listOf(cuotaDelCarro), hoy, leadDays = 3)

        assertTrue(html.contains("${'$'}4,215,223 COP"), html)
        assertFalse(html.contains("saldo"), html)
    }
}
