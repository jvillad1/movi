package com.jvillada.movi.server.correo

import com.jvillada.movi.server.routes.parseSms
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Lo que se puede afirmar de una alerta por correo sin levantar nada: qué se le lee al payload de
 * cada proveedor, a quién se le atribuye, y que el parser ve **lo mismo** que vería en un SMS.
 */
class CorreoEntranteTest {

    private val bogota: ZoneId = ZoneId.of("America/Bogota")
    private val direccion = "9f3cab+1122334455667788@inbound.postmarkapp.com"

    @Test
    fun `lee el payload de Postmark`() {
        val correo = assertNotNull(leerCorreoEntrante(alertaPostmark(direccion)))
        assertEquals("alertasynotificaciones@notificacionesbancolombia.com", correo.remitente)
        assertEquals("Bancolombia", correo.nombreDelRemitente)
        assertEquals(ASUNTO_CUOTA_DE_MANEJO, correo.asunto)
        assertEquals(direccion, correo.destinatarios.first(), "el destinatario de sobre va primero")
        assertTrue(SMS_EQUIVALENTE_CUOTA_DE_MANEJO in correo.cuerpo)
        assertEquals("8d1c4f2a-postmark@bancolombia.com.co", correo.idDelMensaje)
    }

    @Test
    fun `lee el payload de Mailgun, como formulario y como JSON`() {
        for (payload in listOf(alertaMailgunFormulario(direccion), alertaMailgunJson(direccion))) {
            val correo = assertNotNull(leerCorreoEntrante(payload), "no se pudo leer: ${payload.take(40)}")
            assertEquals("alertasynotificaciones@notificacionesbancolombia.com", correo.remitente)
            assertEquals(ASUNTO_DEBITO_AUTOMATICO, correo.asunto)
            assertEquals(direccion, correo.destinatarios.first())
            assertTrue(SMS_EQUIVALENTE_DEBITO_AUTOMATICO in correo.cuerpo)
        }
    }

    @Test
    fun `un cuerpo que no es ni JSON ni formulario no se lee`() {
        assertNull(leerCorreoEntrante("esto no es nada"))
        assertNull(leerCorreoEntrante("{\"Subject\":\"\",\"TextBody\":\"\"}"))
    }

    /**
     * El corazón del asunto: el parser no se toca, así que una alerta por correo tiene que
     * producir **exactamente** el mismo `ParsedSms` que produciría el SMS equivalente. Si la
     * envoltura del correo —saludo, pie legal, firma— le corriera el monto, el comercio o la
     * categoría, se vería acá.
     */
    @Test
    fun `una alerta por correo se lee igual que el SMS equivalente`() {
        val casos = listOf(
            Triple(ASUNTO_CUOTA_DE_MANEJO, CUERPO_CUOTA_DE_MANEJO, SMS_EQUIVALENTE_CUOTA_DE_MANEJO),
            Triple(ASUNTO_DEBITO_AUTOMATICO, CUERPO_DEBITO_AUTOMATICO, SMS_EQUIVALENTE_DEBITO_AUTOMATICO),
        )
        for ((asunto, cuerpo, sms) in casos) {
            val texto = textoDelCorreo(asunto, limpiarCuerpoDelCorreo(cuerpo))
            assertEquals(
                assertNotNull(parseSms(sms), "el SMS de control no parseó: $sms"),
                assertNotNull(parseSms(texto), "el correo no parseó: $texto"),
                "el correo y el SMS tienen que leerse igual",
            )
        }
    }

    @Test
    fun `la cuota de manejo del cupo rotativo se lee, que es para lo que existe esto`() {
        val parsed = assertNotNull(
            parseSms(textoDelCorreo(ASUNTO_CUOTA_DE_MANEJO, limpiarCuerpoDelCorreo(CUERPO_CUOTA_DE_MANEJO))),
        )
        assertEquals(21_640.0, parsed.amount)
        assertEquals("COP", parsed.currency)
    }

    @Test
    fun `una alerta que no trae un movimiento no parsea, y eso no es un error`() {
        val texto = textoDelCorreo("Bancolombia te informa", "Bancolombia: tu clave fue actualizada.")
        assertNull(parseSms(texto), "no trae plata: la fila entra a la bandeja sin propuesta")
        assertTrue(texto.isNotBlank(), "pero el texto sí se guarda")
    }

    @Test
    fun `el token sale de la parte local de la direccion de destino`() {
        assertEquals("1122334455667788", tokenDelDestinatario(listOf(direccion)))
        assertEquals("abc", tokenDelDestinatario(listOf("alertas+abc@midominio.com")))
        assertEquals("abc", tokenDelDestinatario(listOf("alertas+viejo+abc@midominio.com")), "gana el último +")
        assertNull(tokenDelDestinatario(listOf("alertas@midominio.com")))
        assertNull(tokenDelDestinatario(emptyList()))
    }

    @Test
    fun `manda el destinatario de sobre, no el To que el reenvio conserva`() {
        val correo = assertNotNull(leerCorreoEntrante(alertaPostmark(direccion)))
        // El `To` del payload es el correo personal (lo que Gmail no reescribe al reenviar).
        assertTrue("juan@gmail.com" in correo.destinatarios)
        assertEquals("1122334455667788", tokenDelDestinatario(correo.destinatarios))
    }

    @Test
    fun `el token de un usuario es estable y distinto por usuario`() {
        assertEquals(tokenDeCorreoDe("user-1"), tokenDeCorreoDe("user-1"))
        assertTrue(tokenDeCorreoDe("user-1") != tokenDeCorreoDe("user-2"))
        assertEquals(16, tokenDeCorreoDe("user-1").length)
    }

    @Test
    fun `la direccion de reenvio se arma sobre la que dio el proveedor`() {
        assertEquals(
            "9f3cab+abc@inbound.postmarkapp.com",
            direccionDeReenvio("9f3cab@inbound.postmarkapp.com", "abc"),
        )
        assertEquals(
            "alertas+abc@midominio.com",
            direccionDeReenvio("alertas+viejo@midominio.com", "abc"),
            "reconfigurar no acumula sub-direcciones",
        )
        assertNull(direccionDeReenvio("no-es-una-direccion", "abc"))
    }

    @Test
    fun `se quitan las citas y la firma, pero no el bloque de reenvio de Gmail`() {
        val cuerpo = """
            Bancolombia: Compra por ${'$'}25.000 en UBER TRIP.

            > esto es una cita
            --
            firma que no interesa
        """.trimIndent()
        val limpio = limpiarCuerpoDelCorreo(cuerpo)
        assertTrue("UBER TRIP" in limpio)
        assertTrue("cita" !in limpio)
        assertTrue("firma" !in limpio)

        val reenviado = """
            ---------- Forwarded message ---------
            De: Bancolombia <alertas@banco.com>

            Bancolombia: Compra por ${'$'}25.000 en UBER TRIP.
        """.trimIndent()
        assertTrue(
            "UBER TRIP" in limpiarCuerpoDelCorreo(reenviado),
            "cortar en el separador de Gmail borraría justo el aviso",
        )
    }

    @Test
    fun `el HTML se usa solo si no hay texto, y nunca se interpreta`() {
        val payload = """
            {"Subject":"Alerta","HtmlBody":"<style>p{color:red}</style><p>Compra por ${'$'}25.000 en UBER TRIP.</p><script>alert(1)</script>",
             "OriginalRecipient":"$direccion"}
        """.trimIndent()
        val correo = assertNotNull(leerCorreoEntrante(payload))
        assertTrue("UBER TRIP" in correo.cuerpo)
        assertTrue("<" !in correo.cuerpo, "no queda una sola etiqueta")
        assertTrue("alert(1)" !in correo.cuerpo, "el script se tira entero")
    }

    @Test
    fun `el asunto no se repite cuando el cuerpo ya empieza con el`() {
        assertEquals("Bancolombia: Compra por 100", textoDelCorreo("Bancolombia", "Bancolombia: Compra por 100"))
        assertEquals("Alerta: Compra por 100", textoDelCorreo("Alerta", "Compra por 100"))
        assertEquals("Alerta", textoDelCorreo("Alerta", ""))
    }

    @Test
    fun `el texto guardado tiene tope`() {
        val largo = "x".repeat(5_000)
        assertEquals(MAX_TEXTO_DE_CORREO, textoDelCorreo("Alerta", largo).length)
    }

    @Test
    fun `la fecha se lee en la zona de la app, no en UTC`() {
        // 03:12 -0500 es 08:12 UTC: en Bogotá tiene que seguir diciendo 03:12.
        assertEquals(
            "2026-09-16 03:12",
            momentoDelCorreo("Wed, 16 Sep 2026 03:12:41 -0500", ahora = 0L, zone = bogota),
        )
        // El epoch en segundos de Mailgun: el mismo instante.
        assertEquals(
            "2026-09-16 03:12",
            momentoDelCorreo("1789546361", ahora = 0L, zone = bogota),
        )
    }

    @Test
    fun `una fecha ilegible cae a ahora`() {
        val ahora = 1_789_546_361_000L
        assertEquals(
            momentoDelCorreo(null, ahora, bogota),
            momentoDelCorreo("no es una fecha", ahora, bogota),
        )
    }

    @Test
    fun `el id se repite cuando el proveedor reintenta el mismo mensaje`() {
        assertEquals(idDeCorreo("<abc@x>", "t1", "2026-09-16 03:12"), idDeCorreo("<abc@x>", "otro", "2026-01-01 00:00"))
        assertTrue(idDeCorreo(null, "t1", "2026-09-16 03:12") != idDeCorreo(null, "t2", "2026-09-16 03:12"))
        assertTrue(idDeCorreo(null, "t", "x").startsWith("correo_"), "prefijo propio: no dispara la push de sms_rt_")
    }

    @Test
    fun `la marca de origen dice que vino por correo`() {
        assertEquals("Correo · Bancolombia", marcaDeOrigenDelCorreo("Bancolombia", "alertas@banco.com"))
        assertEquals("Correo · banco.com", marcaDeOrigenDelCorreo("", "alertas@banco.com"))
        assertTrue(marcaDeOrigenDelCorreo("x".repeat(300), "a@b.com").length <= MAX_MARCA_DE_ORIGEN_DE_CORREO)
    }

    @Test
    fun `el secreto se lee del Basic o de la cabecera propia, nunca de la URL`() {
        val basic = "Basic " + java.util.Base64.getEncoder().encodeToString("movi:s3creto".toByteArray())
        assertEquals("s3creto", secretoPresentado(basic, null))
        assertEquals("otro", secretoPresentado(basic, "otro"), "la cabecera propia manda")
        assertNull(secretoPresentado(null, null))
        assertNull(secretoPresentado("Bearer algo", null))
        assertNull(secretoPresentado("Basic no-es-base64-valido!!!", null))
    }

    @Test
    fun `comparar el secreto no distingue por longitud de prefijo`() {
        assertTrue(esElMismoSecreto("abc", "abc"))
        assertTrue(!esElMismoSecreto("abc", "abd"))
        assertTrue(!esElMismoSecreto("ab", "abc"))
    }
}
