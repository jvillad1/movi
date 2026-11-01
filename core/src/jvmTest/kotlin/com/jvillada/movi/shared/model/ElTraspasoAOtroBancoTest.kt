package com.jvillada.movi.shared.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * # Vuelta 91 · la plata que el dueño se pasa a sí mismo, de un banco a otro
 *
 * Los textos del 31-oct-2026 («Pagaste $1,500,000.00 a NU Compania de Financiamiento desde tu producto
 * 8133», «Recibimos tu depósito por 1.500.000,00», «¡Bravo! Pagaste tu tarjeta de crédito Nu…») y
 * variantes de PSE a otros bancos.
 */
class ElTraspasoAOtroBancoTest {
    private fun cuenta(id: String, nombre: String, tipo: AccountType, moneda: String = "COP") =
        Account(id = id, name = nombre, type = tipo, balance = 0L, currency = moneda)

    private val b8133 = cuenta("b8133", "Bancolombia Ahorros 8133 · 4057", AccountType.SAVINGS)
    private val b0031 = cuenta("b0031", "Bancolombia Ahorros 0031", AccountType.SAVINGS)
    private val nu = cuenta("nu", "Nu", AccountType.SAVINGS)
    private val nuTarjeta = cuenta("nut", "Nu Tarjeta", AccountType.CREDIT_CARD)
    private val amex = cuenta("amex", "AMEX 9208", AccountType.CREDIT_CARD)
    private val efectivo = cuenta("ef", "Efectivo", AccountType.CASH)
    private val cuentas = listOf(b0031, b8133, nu, nuTarjeta, amex, efectivo)
    private val bancolombia = BANCOS_CONOCIDOS.first { it.id == "bancolombia" }

    @Test
    fun `Nu se reconoce por NU Compania de Financiamiento, con o sin tilde, sin volver a Nu una palabra suelta`() {
        assertEquals("nu", marcaDelTexto("NU Compania de Financiamiento")?.id)
        assertEquals("nu", marcaDelTexto("NU Compañía de Financiamiento")?.id)
        assertEquals("nu", marcaDelTexto("Nu Colombia Compañía de Financiamiento S.A.")?.id)
        assertEquals("nu", marcaDelBeneficiario("NU Compania de Financiamiento")?.id)
        // Lo de antes, igual: «Nu» suelta solo es Nu en el nombre de una cuenta o en el origen.
        assertNull(marcaDelTexto("Nu"))
        assertNull(marcaDelTexto("Transferencia a Nubiel · arreglos"))
        assertEquals("nu", marcaDelBanco("Notificación · Nu")?.id)
        assertEquals("nu", marcaDeLaCuenta(nu)?.id)
        assertEquals("nu", marcaDeLaCuenta(nuTarjeta)?.id)
        assertEquals("bancolombia", marcaDeLaCuenta(b8133)?.id)
    }

    @Test
    fun `el beneficiario es un banco solo por sus patrones seguros`() {
        assertEquals("davivienda", marcaDelBeneficiario("DAVIVIENDA")?.id)
        assertEquals("banco_de_bogota", marcaDelBeneficiario("BANCO DE BOGOTA")?.id)
        assertNull(marcaDelBeneficiario("Coomeva Medicina Prepagada S A"), "Coomeva la EPS no es Bancoomeva")
        assertNull(marcaDelBeneficiario("RAPPI*LAS DOCE"), "un comercio no es un banco")
        assertNull(marcaDelBeneficiario("VISA"), "una red de tarjeta no recibe traspasos")
    }

    @Test
    fun `el deposito propio y el pago de la tarjeta Nu`() {
        assertTrue(esUnDepositoPropio("Recibimos tu depósito por 1.500.000,00: ¡No te preocupes! En unas horas podrás revisar la actualización en tu app."))
        assertFalse(esUnDepositoPropio("Recibiste 300.000,00 en tu cuenta: Te llegó dinero de CAROLINA"), "lo que manda un tercero no")
        assertFalse(esUnDepositoPropio("¡Bravo! Pagaste tu tarjeta de crédito Nu: Recibimos tu pago por \$150.000,00."))
        assertTrue(hablaDeUnDepositoPropio("Listo: tu depósito quedó en camino"))
        assertFalse(hablaDeUnDepositoPropio("Recargaste \$20.000 a tu celular"), "una recarga de celular es un gasto")
        assertTrue(esElPagoDeLaTarjetaNu("Notificación · Nu", "¡Bravo! Pagaste tu tarjeta de crédito Nu: Recibimos tu pago por \$1.306.937,91."))
        assertFalse(esElPagoDeLaTarjetaNu("85540", "Bancolombia: Recibimos tu pago"), "solo si lo avisa Nu")
        assertFalse(esElPagoDeLaTarjetaNu("Notificación · Nu", "Compra aprobada por \$79.000,00: Tu compra en DLO*GOOGLE con tu tarjeta terminada en 1336"))
    }

    @Test
    fun `el remitente sale del rotulo o del comienzo del SMS`() {
        assertEquals("nu", marcaDelRemitente("Notificación · Nu", "Recibimos tu depósito…")?.id)
        assertEquals("bancolombia", marcaDelRemitente("85784", "Bancolombia: Pagaste \$1,500,000.00 a NU…")?.id)
        assertEquals("bancolombia", marcaDelRemitente("85540", "cualquier cosa")?.id)
        assertNull(marcaDelRemitente("Correo · achcolombia.com.co", "PSE - Transacción Aprobada ✅ CUS 787758754: …"))
    }

    @Test
    fun `a donde fue, una sola cuenta de plata sin tarjeta, o a elegir con la tarjeta`() {
        // Nu: la cuenta y la tarjeta → a elegir, las dos.
        assertEquals(DestinoEnOtroBanco(null, listOf("nu", "nut")), destinoEnOtroBanco("NU Compania de Financiamiento", cuentas, bancolombia))
        // Sin la tarjeta Nu → la cuenta Nu.
        assertEquals(DestinoEnOtroBanco("nu", listOf("nu")), destinoEnOtroBanco("NU Compania de Financiamiento", cuentas - nuTarjeta, bancolombia))
        // Sin cuenta de plata en ese banco: no es un traspaso propio (pagar la tarjeta va por su camino).
        assertNull(destinoEnOtroBanco("NU Compania de Financiamiento", cuentas - nu, bancolombia))
        // A su propio banco (el que avisa): no.
        assertNull(destinoEnOtroBanco("BANCOLOMBIA", cuentas, bancolombia))
        // Dos cuentas de plata en el banco → a elegir entre las dos.
        val dosDavi = listOf(cuenta("d1", "Davivienda Ahorros", AccountType.SAVINGS), cuenta("d2", "Davivienda Corriente", AccountType.CHECKING))
        assertEquals(DestinoEnOtroBanco(null, listOf("d1", "d2")), destinoEnOtroBanco("DAVIVIENDA", cuentas + dosDavi, bancolombia))
    }

    @Test
    fun `la cuenta del deposito es la unica de plata del banco que avisa`() {
        val texto = "Recibimos tu depósito por 1.500.000,00: ¡No te preocupes!"
        assertEquals(nu, cuentaDelDepositoPropio("Notificación · Nu", texto, cuentas))
        assertNull(cuentaDelDepositoPropio("Notificación · Nu", texto, cuentas + cuenta("nu2", "Nu Cajita", AccountType.SAVINGS)))
        assertNull(cuentaDelDepositoPropio("Notificación · Nu", "Compra aprobada por \$1.000", cuentas))
    }

    @Test
    fun `las puntas del pago de la tarjeta Nu, en su moneda`() {
        assertEquals(PuntasDelPagoNu(nu, nuTarjeta), puntasDelPagoDeLaTarjetaNu(cuentas, "COP"))
        assertEquals(PuntasDelPagoNu(nu, null), puntasDelPagoDeLaTarjetaNu(cuentas, "USD"))
    }

    @Test
    fun `la comision del traslado se dice solo de Bancolombia a otro banco por PSE`() {
        val sms = "Bancolombia: Pagaste \$1,500,000.00 a NU Compania de Financiamiento desde tu producto 8133 el 31/10/2026 11:12:19."
        assertTrue(diceLaComisionDelTraslado(b8133, nu, listOf(sms)))
        assertTrue(diceLaComisionDelTraslado(b8133, nu, listOf("PSE - Transacción Aprobada ✅ CUS 787758754")))
        assertFalse(diceLaComisionDelTraslado(b8133, b0031, listOf(sms)), "entre cuentas de Bancolombia no")
        assertFalse(diceLaComisionDelTraslado(nu, b8133, listOf(sms)), "desde Nu no la cobra Bancolombia")
        assertFalse(diceLaComisionDelTraslado(b8133, nu, listOf("Recibimos tu depósito por 1.500.000,00")), "sin PSE no se sabe")
    }
}
