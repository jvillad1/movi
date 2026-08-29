package com.jvillada.movi.ui.accounts

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * El aviso del **patrimonio** en la hoja del botón rojo.
 *
 * Existe porque la ola 15 decidió dejar el salto de patrimonio fuera de alcance —evitarlo sería no
 * dejar borrar la cuenta, o tocarle el saldo a una cuenta que el dueño no tocó— y una consecuencia
 * que se decide no evitar hay que decirla con su cifra. Antes de estos tests el KDoc prometía este
 * aviso y la hoja no lo renderizaba: la promesa era falsa.
 */
class DeleteAccountSheetTest {

    /**
     * El caso caro y el que motivó la rama: borrar el crédito desembolsado. El patrimonio sube
     * $257.000.000 sin que se haya pagado un peso, porque la plata prestada se queda en la otra
     * cuenta. La cifra tiene que estar en el texto — es todo el aviso.
     */
    @Test
    fun `el aviso de una deuda dice cuanto sube el patrimonio, con la cifra`() {
        val aviso = balanceWarningLabel(257_000_000L, isDebt = true, currency = "COP")!!
        assertTrue("\$257.000.000" in aviso, "falta la cifra: $aviso")
        assertTrue("subir" in aviso, "tiene que decir que SUBE, que es lo contraintuitivo: $aviso")
        assertTrue("sin que hayas pagado nada" in aviso)
    }

    /** El caso normal: borrar una cuenta con plata. También se dice, aunque no sorprenda. */
    @Test
    fun `el aviso de un activo dice que el patrimonio baja`() {
        val aviso = balanceWarningLabel(1_000_000L, isDebt = false, currency = "COP")!!
        assertTrue("\$1.000.000" in aviso, "falta la cifra: $aviso")
        assertTrue("bajar" in aviso, aviso)
    }

    /** Una cuenta vacía no tiene nada que avisar, y un bloque vacío sería ruido en esa hoja. */
    @Test
    fun `una cuenta en cero no genera aviso`() {
        assertNull(balanceWarningLabel(0L, isDebt = true, currency = "COP"))
        assertNull(balanceWarningLabel(0L, isDebt = false, currency = "COP"))
    }

    /**
     * La moneda viaja aparte de la del traspaso justamente para esto: la deuda de una tarjeta en
     * USD entra al patrimonio por su estimado en COP, y rotular pesos como dólares en la hoja del
     * botón rojo es el error que no se puede cometer.
     */
    @Test
    fun `la cifra se rotula con la moneda que le pasan, no con una fija`() {
        assertTrue("US\$400" in balanceWarningLabel(400L, isDebt = true, currency = "USD")!!)
    }

    /**
     * Y el aviso de los traspasos ya NO promete que esos movimientos vuelvan a contar en el mes:
     * desde la ola 15 no lo hacen, y mandar al dueño a revisar meses viejos que están intactos
     * sería peor que no avisar.
     */
    @Test
    fun `el aviso de traspasos no promete que el mes vuelva a moverse`() {
        val aviso = transferWarningLabel(1, 257_000_000L, "COP")
        assertTrue("suma a tus gastos ni a tus ingresos del mes" in aviso, aviso)
        assertTrue("vuelve a contar" !in aviso, "esa promesa ya no es cierta: $aviso")
        assertEquals(true, "\$257.000.000" in aviso)
        // Y la coma huérfana antes del punto, que estaba desde que el monto entró al aviso.
        assertTrue(",." !in aviso, "coma pegada al punto: $aviso")
    }
}
