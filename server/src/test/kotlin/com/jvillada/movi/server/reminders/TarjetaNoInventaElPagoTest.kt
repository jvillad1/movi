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

    @Test
    fun una_regla_escrita_a_mano_tampoco() {
        // El gimnasio, el colegio: el dueño escribió el monto y es el que se paga.
        val gimnasio = com.jvillada.movi.shared.model.RecurringRule(
            id = "r1",
            name = "Gimnasio",
            category = "Salud",
            amount = 180_000,
            dayOfMonth = 5,
            type = com.jvillada.movi.shared.model.TransactionType.EXPENSE,
        )

        assertFalse(gimnasio.montoEsSaldo, "el default tiene que ser false, o toda regla vieja mentiría")
    }
}
