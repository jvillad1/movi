package com.jvillada.movi.server.reminders

import com.jvillada.movi.shared.model.CreditTerms
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * A quién le avisa Movi que se le vence una cuota.
 *
 * El filtro que decide esto vivía dentro de un `dbQuery` y no tenía prueba. Se extrajo a
 * [entraAlBarridoDeAvisos] justamente para poder escribirla, porque el modo de falla es caro y
 * silencioso: avisarle al dueño que pague algo que Skandia gira sola no solo es ruido — si él
 * lo registra como gasto, la plata se descuenta dos veces.
 */
class QuienPagaNoRecibeAvisoTest {

    private fun terms(payroll: Boolean = false, paga: String? = null) = CreditTerms(
        accountId = "acc-1254",
        bank = "Davibank",
        principal = 784_000_000,
        rateEa = 10.99,
        termMonths = 180,
        installment = 9_147_408,
        dayOfMonth = 12,
        startDate = "2025-06-12",
        payrollDeduction = payroll,
        paidBy = paga,
    )

    @Test
    fun un_credito_normal_si_recibe_aviso() {
        assertTrue(entraAlBarridoDeAvisos(terms()))
    }

    @Test
    fun una_libranza_no() {
        assertFalse(entraAlBarridoDeAvisos(terms(payroll = true)))
    }

    @Test
    fun una_cuota_que_paga_otro_tampoco() {
        // Las dos hipotecas de Davibank: $11.761.122/mes que gira Skandia. Con este test en
        // rojo, el dueño recibiría dos avisos mensuales de una plata que él no mueve.
        assertFalse(entraAlBarridoDeAvisos(terms(paga = "Skandia")))
        // Y el Cotrafa, que está a su nombre y paga su esposa.
        assertFalse(entraAlBarridoDeAvisos(terms(paga = "Caro")))
    }

    @Test
    fun un_nombre_en_blanco_no_cuenta_como_tercero() {
        // El server normaliza a null lo que llegue vacío (ver el upsert de CreditRoutes), pero un
        // cliente viejo o una fila anterior podrían traer "" o "  ". Si eso apagara los avisos, el
        // dueño dejaría de saber de un crédito que sí paga él — el error caro en esta dirección.
        //
        // La primera versión de este test pasaba `null` y por lo tanto no probaba nada: era el
        // primer test escrito de nuevo con otro nombre.
        assertTrue(entraAlBarridoDeAvisos(terms(paga = "")))
        assertTrue(entraAlBarridoDeAvisos(terms(paga = "   ")))
    }
}
