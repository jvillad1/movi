package com.jvillada.movi.sms

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BankSenderFilterTest {

    @Test
    fun `short codes match regardless of body`() {
        assertTrue(BankSenderFilter.matches("85540", "Compra por 50.000 en EXITO"))
        assertTrue(BankSenderFilter.matches("891333", "cualquier cosa"))
        assertTrue(BankSenderFilter.matches("+5787400", "aviso"))   // contiene 87400
    }

    @Test
    fun `keyword Bancolombia in body matches any sender`() {
        assertTrue(BankSenderFilter.matches("InfoSMS", "Bancolombia: Retiro por 200.000"))
        assertTrue(BankSenderFilter.matches(null, "bancolombia le informa"))
    }

    @Test
    fun `personal messages never match`() {
        assertFalse(BankSenderFilter.matches("+573001234567", "hola, nos vemos a las 7"))
        assertFalse(BankSenderFilter.matches("Claro", "Tu factura llegó"))
        assertFalse(BankSenderFilter.matches(null, ""))
    }

    @Test
    fun `realtime id is deterministic and prefixed`() {
        val a = smsRealtimeId("85540", 1_700_000_000_000, "Compra por 50.000")
        val b = smsRealtimeId("85540", 1_700_000_000_000, "Compra por 50.000")
        val c = smsRealtimeId("85540", 1_700_000_000_001, "Compra por 50.000")
        assertEquals(a, b)
        assertTrue(a != c)
        assertTrue(a.startsWith("sms_rt_"))
        assertEquals("sms_rt_".length + 16, a.length)
    }

    @Test
    fun `remote config can add senders and keywords without reinstalling`() {
        val remote = FilterConfig(senderCodes = listOf("85540", "890123"), bodyKeywords = listOf("bancolombia", "nequi"))
        assertTrue(BankSenderFilter.matches("890123", "cualquier cosa", remote))
        assertTrue(BankSenderFilter.matches("Info", "Nequi: pago recibido", remote))
        assertFalse(BankSenderFilter.matches("890123", "cualquier cosa"))   // defaults no lo conocen
    }

    /**
     * **Vuelta 99 · el papá del dueño.** Sus SMS de Davivienda llegan de un código corto 871… que Movi
     * no conoce: los deja pasar la palabra «DAVIVIENDA» del cuerpo, con la config del server y con el
     * piso compilado. Los tres textos son los reales de su teléfono.
     */
    @Test
    fun `un SMS de Davivienda desde un 871 entra por el nombre del banco`() {
        val reales = listOf(
            "DAVIVIENDA Descuento en internet, \$64,900,Cta de Ahorros *0136,Hora 16:49,Lugar PSE MERCADOPAGO COLOMBIA.",
            "DAVIVIENDA Abono Transferencia ACH, \$419,745,Cta de Ahorros *0136,Hora 17:18,Lugar 0000000000000000000000000.",
            "DAVIVIENDA . Retiro . \$320,000,Cta de Ahorros *0136,Hora 12:04,Lugar OVIEDO.",
        )
        val delServer = FilterConfig(senderCodes = listOf("85540", "891333", "87400"), bodyKeywords = listOf("bancolombia", "nubank", "davivienda"))
        for (texto in reales) {
            for (remitente in listOf("87100", "871", "+57871")) {
                assertTrue(BankSenderFilter.matches(remitente, texto), "el piso no deja pasar: $texto")
                assertTrue(BankSenderFilter.matches(remitente, texto, SmsFilterConfigStore.withDefaults(delServer)))
            }
        }
        // Un APK 1.87 (piso de antes: solo Bancolombia) lo deja pasar con lo que baja del server.
        val pisoDeAntes = FilterConfig(listOf("85540", "891333", "87400"), listOf("bancolombia"))
        assertFalse(BankSenderFilter.matches("87100", reales.first(), pisoDeAntes))
        assertTrue(BankSenderFilter.matches("87100", reales.first(), FilterConfig(pisoDeAntes.senderCodes, delServer.bodyKeywords)))
    }

    /** Los bancos nuevos no abren la puerta a lo personal: un SMS que no nombra ningún banco sigue afuera. */
    @Test
    fun `con los bancos nuevos un SMS personal sigue afuera`() {
        assertFalse(BankSenderFilter.matches("87100", "hola papá, ya llegué a la casa"))
        assertFalse(BankSenderFilter.matches("+573001234567", "tu numero de pedido continua en el nuevo local, nunca antes visto"))
        assertFalse(BankSenderFilter.matches("Claro", "Tu factura de Claro está disponible por \$63.900"))
    }
}
