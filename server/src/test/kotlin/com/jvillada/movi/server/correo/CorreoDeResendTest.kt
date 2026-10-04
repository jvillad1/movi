package com.jvillada.movi.server.correo

import com.jvillada.movi.server.routes.parseSms
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Las dos lecturas de Resend (el evento y el contenido) y que terminan en el MISMO [CorreoEntrante]. */
class CorreoDeResendTest {

    private val deMovi = "alertas+1122334455667788@abc123.resend.app"

    @Test
    fun `el evento email received da su id y sus destinatarios`() {
        val evento = assertNotNull(leerEventoDeResend(eventoDeResend(recibidoPara = listOf(deMovi))))
        assertEquals("email.received", evento.tipo)
        assertEquals("56761188-7520-42d8-8898-ff6fc54ce618", evento.idDelCorreo)
        assertEquals(listOf(deMovi, "juan@gmail.com"), evento.destinatarios, "received_for va primero")
    }

    @Test
    fun `un evento ajeno se lee, un id con forma rara no, y lo que no es JSON es null`() {
        assertEquals("email.sent", leerEventoDeResend(eventoDeResend(tipo = "email.sent"))?.tipo)
        assertNull(leerEventoDeResend(eventoDeResend(idDelCorreo = "../../api-keys"))?.idDelCorreo)
        assertNull(leerEventoDeResend("esto no es un evento"))
        assertNull(leerEventoDeResend("""{"data":{}}"""), "sin type")
    }

    @Test
    fun `el contenido da los mismos campos que arma Postmark`() {
        val evento = leerEventoDeResend(eventoDeResend(recibidoPara = listOf(deMovi)))!!
        val correo = assertNotNull(correoDeResend(contenidoDeResend(recibidoPara = listOf(deMovi)), evento))
        assertEquals("alertasynotificaciones@notificacionesbancolombia.com", correo.remitente)
        assertEquals("Bancolombia", correo.nombreDelRemitente)
        assertEquals(ASUNTO_CUOTA_DE_MANEJO, correo.asunto)
        assertTrue(SMS_EQUIVALENTE_CUOTA_DE_MANEJO in correo.cuerpo)
        assertTrue("Vigilado" !in correo.cuerpo, "la firma se corta igual que en Postmark")
        assertEquals("<8d1c4f2a-resend@bancolombia.com.co>", correo.idDelMensaje)
        assertEquals("Wed, 16 Sep 2026 03:12:41 -0500", correo.fecha, "la fecha del banco, no la de Resend")
        assertEquals("Correo · Bancolombia", marcaDeOrigenDelCorreo(correo.nombreDelRemitente, correo.remitente))

        val parsed = assertNotNull(parseSms(textoDelCorreo(correo.asunto, correo.cuerpo)))
        assertEquals(21_640.0, parsed.amount)
    }

    /**
     * **El caso que decide si esto sirve**: un reenvío automático de Gmail conserva el `To:` del
     * banco (el Gmail del dueño). La dirección de Movi tiene que salir de `received_for` (el sobre)
     * o de `X-Forwarded-To`, que Gmail agrega al reenviar.
     */
    @Test
    fun `con un reenvio de Gmail el token sale del sobre o de X-Forwarded-To, no del To`() {
        val evento = leerEventoDeResend(eventoDeResend())!!

        val porSobre = correoDeResend(contenidoDeResend(recibidoPara = listOf(deMovi)), evento)!!
        assertEquals(deMovi, porSobre.destinatarios.first(), "el sobre va primero")
        assertEquals("1122334455667788", tokenDelDestinatario(porSobre.destinatarios))

        val porCabecera = correoDeResend(contenidoDeResend(reenviadoA = deMovi), evento)!!
        assertEquals("1122334455667788", tokenDelDestinatario(porCabecera.destinatarios))

        val sinNada = correoDeResend(contenidoDeResend(), evento)!!
        assertEquals(emptyList(), tokensDeLosDestinatarios(sinNada.destinatarios), "el To solo trae el Gmail")
    }

    @Test
    fun `un Gmail con su propio mas no tapa la direccion de Movi`() {
        val evento = leerEventoDeResend(eventoDeResend())!!
        val correo = correoDeResend(contenidoDeResend(recibidoPara = listOf("juan+bancos@gmail.com", deMovi)), evento)!!
        assertEquals(listOf("bancos", "1122334455667788"), tokensDeLosDestinatarios(correo.destinatarios))
    }

    @Test
    fun `sin texto usa el HTML sin interpretarlo, y sin nada de texto es null`() {
        val evento = leerEventoDeResend(eventoDeResend())!!
        val conHtml = correoDeResend(
            contenidoDeResend(cuerpo = null, html = "<html><body><p>$SMS_EQUIVALENTE_CUOTA_DE_MANEJO</p><script>x()</script></body></html>"),
            evento,
        )!!
        assertTrue(SMS_EQUIVALENTE_CUOTA_DE_MANEJO in conHtml.cuerpo)
        assertTrue("x()" !in conHtml.cuerpo)

        assertNull(correoDeResend(contenidoDeResend(asunto = "", cuerpo = null), evento))
        assertNull(correoDeResend("no es JSON", evento))
    }

    @Test
    fun `una fecha que no se entiende cae a cuando la recibio Resend`() {
        val evento = leerEventoDeResend(eventoDeResend())!!
        val correo = correoDeResend(contenidoDeResend(fecha = "ayer por la tarde"), evento)!!
        assertEquals("2026-09-16T08:12:42.894Z", correo.fecha)
        assertEquals("2026-09-16 03:12", momentoDelCorreo(correo.fecha, ahora = 0L, zone = ZoneId.of("America/Bogota")))
    }

    @Test
    fun `una fecha RFC 1123 con comentario al final se entiende`() {
        assertEquals(
            "2026-09-16 03:12",
            momentoDelCorreo("Wed, 16 Sep 2026 08:12:41 +0000 (UTC)", ahora = 0L, zone = ZoneId.of("America/Bogota")),
        )
    }
}
