package com.jvillada.movi.ui.porrevisar

import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.ParsedSms
import com.jvillada.movi.shared.model.SMS_STATE_PENDING
import com.jvillada.movi.shared.model.SmsMessage
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.ui.Screen
import com.jvillada.movi.ui.recurrentes.vieneDeUnPagoQueSeEspera
import com.jvillada.movi.ui.sdui.textosDeLaFranjaPorConfirmar
import com.jvillada.movi.ui.sms.TITULO_DE_UN_AVISO_SIN_BANCO
import com.jvillada.movi.ui.sms.tituloDelAvisoEnReconciliar
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Vuelta 44, lo puro: lo que dice la tarjeta, la franja, el título de Revisar aviso y el aviso de abajo. */
class LosAvisosDeUnVistazoLogicaTest {

    @Test
    fun `la linea de lo entendido dice lo que haya`() {
        assertEquals("Transporte · Bancolombia Ahorros", lineaDeLoEntendido("Transporte", "Bancolombia Ahorros"))
        assertEquals("Transporte", lineaDeLoEntendido("Transporte", null))
        assertEquals("Bancolombia Ahorros", lineaDeLoEntendido(" ", "Bancolombia Ahorros"))
        assertNull(lineaDeLoEntendido(null, null))
    }

    @Test
    fun `la tarjeta dice la categoria y la cuenta aunque Movi no las entienda del todo`() {
        val sms = SmsMessage(
            id = "s", time = "2026-10-28 12:05", bank = "85540", state = SMS_STATE_PENDING, det = "",
            text = "Bancolombia: Compraste \$18.300,00 en UBER RIDES con tu T.Deb *4057.",
        )
        val propuestas = com.jvillada.movi.shared.model.PropuestasDeLaBandeja(
            propuestas = listOf(
                com.jvillada.movi.shared.model.PropuestaDelAviso(
                    "s", leido = ParsedSms(18_300.0, "UBER RIDES", TransactionType.EXPENSE, "Transporte"),
                ),
            ),
            // «Transporte» no es del dueño: Movi duda, y la tarjeta igual dice lo que entendió.
            categoriasDelDueno = listOf("Comida"),
        )
        val cuenta = Account("c", "Bancolombia Ahorros 4057", AccountType.SAVINGS, 0L, "COP")
        val entendido = loQueMoviEntendio(sms, listOf(sms), propuestas, listOf(cuenta), ahora = 0L)
        assertNull(entendido.anotacion, "con una duda no se confirma desde la fila")
        assertEquals("Transporte · Bancolombia Ahorros 4057", entendido.linea)
    }

    @Test
    fun `la franja dice la suma solo entera`() {
        assertEquals("Por confirmar" to "\$103.600 en 3 avisos", textosDeLaFranjaPorConfirmar(ConteoPorRevisar(avisos = 3), 103_600L))
        assertEquals("Por confirmar" to "\$18.300 en 1 aviso", textosDeLaFranjaPorConfirmar(ConteoPorRevisar(avisos = 1), 18_300L))
        // Con algo más que espera, la cifra dice de qué es y lo demás va aparte.
        assertEquals(
            "Por confirmar" to "\$103.600 en 4 avisos · y 1 movimiento",
            textosDeLaFranjaPorConfirmar(ConteoPorRevisar(avisos = 4, movimientos = 1), 103_600L),
        )
        // Sin la suma (un aviso sin monto, otra moneda, un server viejo): el conteo, como antes.
        assertEquals("Por confirmar" to null, textosDeLaFranjaPorConfirmar(ConteoPorRevisar(avisos = 3), null))
        assertEquals(
            "Por confirmar" to "4 avisos · 1 movimiento",
            textosDeLaFranjaPorConfirmar(ConteoPorRevisar(avisos = 4, movimientos = 1), null),
        )
        // Sin avisos, una suma no tiene de qué ser.
        assertEquals("Por confirmar" to null, textosDeLaFranjaPorConfirmar(ConteoPorRevisar(movimientos = 2), 50_000L))
    }

    @Test
    fun `Revisar aviso dice el banco y no el codigo del remitente`() {
        assertEquals("Bancolombia", tituloDelAvisoEnReconciliar("85540"))
        assertEquals(TITULO_DE_UN_AVISO_SIN_BANCO, tituloDelAvisoEnReconciliar("899776"))
        assertEquals(TITULO_DE_UN_AVISO_SIN_BANCO, tituloDelAvisoEnReconciliar("SMS"))
        assertEquals("Notificación · Nu", tituloDelAvisoEnReconciliar("Notificación · Nu"))
        assertEquals("Comprobante · recibo.png", tituloDelAvisoEnReconciliar("Comprobante · recibo.png"))
    }

    @Test
    fun `el pago de algo que se espera no pregunta si se repite`() {
        assertTrue(vieneDeUnPagoQueSeEspera(Screen.QuickAdd(presetNota = "Arriendo", presetCategoria = "Vivienda", presetMonto = 1_800_000)))
        assertTrue(vieneDeUnPagoQueSeEspera(Screen.QuickAdd(presetDeudaId = "credito")))
        // El modo pago de la vuelta 43, que es lo que arma hoy «Anotar pago».
        assertTrue(
            vieneDeUnPagoQueSeEspera(
                Screen.QuickAdd(presetPago = com.jvillada.movi.ui.quickadd.PagoDeLaFactura(ruleId = "r", periodo = "2026-10", nombre = "Arriendo", mes = "octubre", cuentaDeLaRegla = null)),
            ),
        )
        assertFalse(vieneDeUnPagoQueSeEspera(Screen.QuickAdd()))
        assertFalse(vieneDeUnPagoQueSeEspera(Screen.QuickAdd(presetRepartir = true)))
    }
}
