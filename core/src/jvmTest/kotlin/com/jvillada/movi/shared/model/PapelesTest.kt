package com.jvillada.movi.shared.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Ola 2: cómo se reconoce en la bandeja la propuesta de un comprobante, y por qué la captura no la cuenta. */
class PapelesTest {

    private fun sms(id: String, time: String) = SmsMessage(id = id, time = time, bank = "85540", text = "", state = SMS_STATE_PENDING, det = "")

    @Test
    fun el_id_de_un_comprobante_lleva_su_documento() {
        val id = idDeComprobante("doc_123")
        assertEquals("cmp_doc_123", id)
        assertTrue(esIdDeComprobante(id))
        assertEquals("doc_123", documentoDelComprobante(id))
        assertNull(documentoDelComprobante("sms_rt_abc"))
        assertFalse(esIdDeComprobante("sms_abc"))
    }

    @Test
    fun la_captura_no_cuenta_los_comprobantes() {
        // Un comprobante compartido hoy no prueba que el teléfono siga capturando: si contara, la
        // alerta de «nunca llegó nada» se apagaría sola y el banco mudo no se vería nunca.
        val mensajes = listOf(sms("sms_1", "2026-09-01 10:00"), sms(idDeComprobante("doc_1"), "2026-10-01 10:00"))
        val captura = capturaDeSms(soloLoQueLlegoSolo(mensajes).map { it.time })
        assertEquals(1, captura.total)
        assertEquals("2026-09-01 10:00", captura.ultimo)
        assertTrue(capturaDeSms(soloLoQueLlegoSolo(listOf(sms(idDeComprobante("d"), "2026-10-01 10:00"))).map { it.time }).nuncaLlegoNada)
    }

    @Test
    fun el_rotulo_cabe_en_la_columna() {
        assertEquals("Comprobante · recibo.jpg", rotuloDeComprobante("recibo.jpg"))
        val largo = rotuloDeComprobante("x".repeat(300) + ".jpg")
        assertEquals(100, largo.length)
        assertTrue(largo.startsWith("Comprobante · "))
    }
}
