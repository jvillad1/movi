package com.jvillada.movi.server.push

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * El `endpoint` de una suscripción push no es un nombre: es la URL de capacidad del dispositivo.
 * Quien la tenga puede escribirle a esa pantalla de bloqueo. Los tres logs de [WebPushSender] la
 * escribían entera, y un log se copia, rota y lo lee cualquiera con acceso al panel del hosting.
 */
class HuellaDeSuscripcionTest {

    /** Uno realista: el de Chrome incluye el identificador del dispositivo en la ruta. */
    private val endpoint =
        "https://fcm.googleapis.com/fcm/send/fXy9_ZQkAbc:APA91bH-secreto-larguisimo-del-dispositivo-123"

    @Test
    fun `la huella no deja ver ningun pedazo del endpoint`() {
        val huella = WebPushSender.huella(endpoint)
        assertFalse(huella.contains("fcm.googleapis.com"), huella)
        assertFalse(huella.contains("fXy9"), huella)
        assertFalse(huella.contains("APA91bH"), huella)
        // Y nada del final tampoco: un "últimos 8 caracteres" habría pasado los asserts de
        // arriba y seguiría entregando parte del secreto.
        assertFalse(huella.contains(endpoint.takeLast(6)), huella)
    }

    @Test
    fun `la misma suscripcion da siempre la misma huella`() {
        assertEquals(WebPushSender.huella(endpoint), WebPushSender.huella(endpoint))
    }

    @Test
    fun `dos suscripciones distintas dan huellas distintas`() {
        assertTrue(WebPushSender.huella(endpoint) != WebPushSender.huella(endpoint + "x"))
    }
}
