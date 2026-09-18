package com.jvillada.movi.sensor

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Leer «¿está concedido el acceso a las notificaciones?» sin equivocarse.
 *
 * El valor de `Settings.Secure` es una lista de `ComponentName` aplanados separada por `:`, y la
 * tentación es resolverlo con un `contains`. Un `contains` diría «Concedido» porque otra app se
 * llame parecido, y esta pantalla ya cometió una vez la mentira de pintar la captura como activa
 * cuando no lo estaba: de ahí sale esta prueba.
 */
class AccesoANotificacionesTest {

    private val yo = "com.jvillada.movi"

    @Test
    fun `el paquete propio en la lista cuenta como concedido`() {
        assertTrue(
            elPaqueteEscuchaNotificaciones(
                "com.jvillada.movi/com.jvillada.movi.notificaciones.EscuchaDeNotificaciones",
                yo,
            )
        )
    }

    @Test
    fun `entre varios oyentes se encuentra el propio`() {
        val valor = "com.otra.app/com.otra.app.Servicio:" +
            "com.jvillada.movi/com.jvillada.movi.notificaciones.EscuchaDeNotificaciones:" +
            "com.tercera/com.tercera.S"
        assertTrue(elPaqueteEscuchaNotificaciones(valor, yo))
    }

    @Test
    fun `sin ningún oyente no está concedido`() {
        assertFalse(elPaqueteEscuchaNotificaciones(null, yo))
        assertFalse(elPaqueteEscuchaNotificaciones("", yo))
    }

    @Test
    fun `otra app con un nombre parecido no concede el acceso`() {
        assertFalse(elPaqueteEscuchaNotificaciones("com.jvillada.movi.falsa/com.falsa.S", yo))
        assertFalse(elPaqueteEscuchaNotificaciones("com.otro.com.jvillada.movi/com.otro.S", yo))
    }

    @Test
    fun `una app que no es Movi no concede el acceso`() {
        assertFalse(elPaqueteEscuchaNotificaciones("com.whatsapp/com.whatsapp.S", yo))
    }
}
