package com.jvillada.movi.server.correo

import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * La verificación de la firma Svix, contra el ejemplo publicado en
 * https://docs.svix.com/receiving/verifying-payloads/how-manual («Example signatures»): si esta
 * prueba pasa, la cuenta es la misma que hacen las bibliotecas oficiales.
 */
class FirmaDeSvixTest {

    private val secreto = "whsec_plJ3nmyCDGBKInavdOK15jsl"
    private val cuerpo = """{"event_type":"ping","data":{"success":true}}""".toByteArray()
    private val id = "msg_loFOjxBNrRLzqYUf"
    private val timestamp = "1731705121"
    private val firma = "v1,rAvfW3dJ/X/qxhsaXPOyyCGmRKsaKWcsNccKXlIktD0="
    private val ahora = 1_731_705_121L

    private fun valida(
        secreto: String = this.secreto,
        id: String? = this.id,
        timestamp: String? = this.timestamp,
        firmas: String? = firma,
        cuerpo: ByteArray = this.cuerpo,
        ahora: Long = this.ahora,
    ) = FirmaDeSvix.esValida(secreto, id, timestamp, firmas, cuerpo, ahora)

    @Test
    fun `el ejemplo de la documentacion de Svix verifica`() {
        assertTrue(valida())
    }

    @Test
    fun `vale si cualquiera de las firmas de la lista coincide`() {
        assertTrue(valida(firmas = "v1,bm9ldHUjKzFob2VudXRob2VodWUzMjRvdWVvdW9ldQo= $firma"))
        // Una v2 con el mismo valor no cuenta: solo se conoce el esquema v1.
        assertFalse(valida(firmas = firma.replace("v1,", "v2,")))
    }

    @Test
    fun `un byte distinto en el cuerpo la rompe`() {
        assertFalse(valida(cuerpo = """{"event_type":"ping","data":{"success":false}}""".toByteArray()))
        // Volver a serializar el JSON (otros espacios) también la rompe: se firma el cuerpo crudo.
        assertFalse(valida(cuerpo = """{"event_type": "ping", "data": {"success": true}}""".toByteArray()))
    }

    @Test
    fun `otro id u otro secreto la rompen`() {
        assertFalse(valida(id = "msg_otro"))
        assertFalse(valida(secreto = "whsec_" + Base64.getEncoder().encodeToString("otro-secreto".toByteArray())))
    }

    @Test
    fun `un timestamp fuera de los cinco minutos no vale, aunque la firma sea buena`() {
        assertTrue(valida(ahora = ahora + FirmaDeSvix.TOLERANCIA_SEGUNDOS))
        assertFalse(valida(ahora = ahora + FirmaDeSvix.TOLERANCIA_SEGUNDOS + 1), "un reenvío viejo")
        assertFalse(valida(ahora = ahora - FirmaDeSvix.TOLERANCIA_SEGUNDOS - 1), "uno del futuro")
    }

    @Test
    fun `sin cabeceras, o con basura, no vale y no lanza`() {
        assertFalse(valida(id = null))
        assertFalse(valida(timestamp = null))
        assertFalse(valida(firmas = null))
        assertFalse(valida(firmas = ""))
        assertFalse(valida(timestamp = "ayer"))
        assertFalse(valida(firmas = "v1,esto no es base64!!"))
        assertFalse(valida(firmas = "sin-coma"))
        assertFalse(valida(secreto = "whsec_"))
        assertFalse(valida(secreto = "whsec_no es base64!!"))
    }

    @Test
    fun `una firma hecha con la misma receta verifica, para cualquier cuerpo`() {
        val cuerpoUtf8 = """{"type":"email.received","data":{"subject":"Compraste en Panadería"}}""".toByteArray()
        assertTrue(valida(cuerpo = cuerpoUtf8, firmas = firmarComoSvix(secreto, id, timestamp, cuerpoUtf8)))
    }
}

/** La receta de Svix, para que las pruebas de la ruta firmen sus propios webhooks. */
fun firmarComoSvix(secreto: String, id: String, timestamp: String, cuerpo: ByteArray): String {
    val mac = Mac.getInstance("HmacSHA256")
    mac.init(SecretKeySpec(Base64.getDecoder().decode(secreto.removePrefix("whsec_")), "HmacSHA256"))
    mac.update("$id.$timestamp.".toByteArray())
    return "v1," + Base64.getEncoder().encodeToString(mac.doFinal(cuerpo))
}
