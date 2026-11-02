package com.jvillada.movi.ui.sms

import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.SMS_STATE_PENDING
import com.jvillada.movi.shared.model.SmsMessage
import com.jvillada.movi.shared.model.UsoDeCuenta
import com.jvillada.movi.ui.components.OrigenDeLaCuentaDelBanco
import com.jvillada.movi.ui.components.conLaCuentaQueSugiereMovi
import com.jvillada.movi.ui.components.resolverCuentaDelBanco
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * # Vuelta 107 · el pago a Coomeva del 25-oct, del lado de la app
 *
 * El banco dice que los $138.600 salieron de la …8133; Movi los anotó en la «Bancolombia Ahorros 0031».
 * Los datos de producción (solo lectura) cuentan cómo: el SMS del 85784 («… desde tu producto 8133 …»)
 * se confirmó solo, 47 s después de llegar, y el correo de PSE llegó 2 min más tarde y se enlazó a ese
 * mismo movimiento con «Es este». Ese día la app no leía «desde tu producto 8133» como un número de cuenta
 * (llegó con la vuelta 91, el 1-nov), así que el SMS caía en la cuenta **por defecto**: la primera de banco
 * del abecedario, la 0031 («0031» va antes que «8133»). Textos sintéticos con la misma forma.
 */
class ElCoomevaDel25DeOctubreTest {
    private val b0031 = Account("b0031", "Bancolombia Ahorros 0031", AccountType.SAVINGS, 0L)
    private val b8133 = Account("b8133", "Bancolombia Ahorros 8133 · 4057", AccountType.SAVINGS, 0L)
    private val nu = Account("nu", "Nu", AccountType.SAVINGS, 0L)
    private val cuentas = listOf(b0031, b8133, nu)

    private val smsTexto =
        "Bancolombia: Pagaste \$138,600.00 a Coomeva Medicina Prepagada S A desde tu producto 8133 el 25/10/2026 23:47:16. ¿Dudas? Llamanos al 6045109095. Estamos cerca"
    private val pseTexto =
        "PSE - Transacción Aprobada ✅ CUS 771600001: ¡Hola! Los siguientes son los datos de tu transacción: " +
            "Valor: \$ 138.600,00 Empresa: Coomeva Medicina Prepagada S.A. Descripción: Coomeva Pago de saldo plan familiar " +
            "Fecha de la transacción: 25/10/2026 CUS: 771600001 Gracias por utilizar nuestro servicio."

    private fun aviso(id: String, bank: String, text: String, time: String) =
        SmsMessage(id = id, time = time, bank = bank, text = text, state = SMS_STATE_PENDING, det = "")

    @Test
    fun `la causa - sin leer el numero, el SMS caia en la primera cuenta de banco del abecedario`() {
        // Lo que hacía la app el 25-oct: el SMS sin un número que supiera leer.
        val sinNumero = smsTexto.replace("desde tu producto 8133", "desde tu producto")
        val entonces = resolverCuentaDelBanco(cuentas, UsoDeCuenta.ORIGEN_DE_GASTO, "85784", textoDelMensaje = sinNumero)
        assertEquals(OrigenDeLaCuentaDelBanco.POR_DEFECTO, entonces.origen)
        assertEquals(b0031, entonces.cuenta, "la 0031 es la primera de banco del abecedario")
    }

    @Test
    fun `hoy el SMS cae en la 8133 por el numero`() {
        val r = resolverCuentaDelBanco(cuentas, UsoDeCuenta.ORIGEN_DE_GASTO, "85784", textoDelMensaje = smsTexto)
        assertEquals(b8133, r.cuenta)
        assertEquals(OrigenDeLaCuentaDelBanco.POR_EL_NUMERO, r.origen)
    }

    @Test
    fun `el correo de PSE agrupado con su SMS hereda la cuenta del SMS, aunque la propuesta sea el correo`() {
        val correo = aviso("correo_pse", "Correo · achcolombia.com.co", pseTexto, "2026-10-25 23:49")
        val sms = aviso("sms_rt_coomeva", "85784", smsTexto, "2026-10-25 23:47")
        val r = resolverCuentaDelPago(cuentas, UsoDeCuenta.ORIGEN_DE_GASTO, listOf(correo, sms), elegidaAMano = null)
        assertEquals(b8133, r.cuenta)
        assertEquals(OrigenDeLaCuentaDelBanco.POR_EL_NUMERO, r.origen)
        // Y lo que sugiera el server (memoria) no le gana al número del SMS.
        assertEquals(b8133, conLaCuentaQueSugiereMovi(r, cuentas, sugeridaId = "b0031").cuenta)
    }

    @Test
    fun `el correo de PSE solo no elige la cuenta poco usada - la pide, o toma la que sugiere el server`() {
        val solo = resolverCuentaDelBanco(cuentas, UsoDeCuenta.ORIGEN_DE_GASTO, "Correo · achcolombia.com.co", textoDelMensaje = pseTexto)
        assertEquals(OrigenDeLaCuentaDelBanco.NINGUNA, solo.origen, "nunca la primera del abecedario")
        assertNull(solo.cuenta)
        // La que más usa PSE (o la regla de Coomeva) la propone el server en `cuentaSugeridaId`.
        val conMovi = conLaCuentaQueSugiereMovi(solo, cuentas, sugeridaId = "b8133")
        assertEquals(b8133, conMovi.cuenta)
        assertEquals(OrigenDeLaCuentaDelBanco.SUGERIDA_POR_MOVI, conMovi.origen)
    }
}
