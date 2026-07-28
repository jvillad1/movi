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
}
