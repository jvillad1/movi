package com.jvillada.movi.data

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * **Cerrar sesión suelta del espejo local lo que el server ya tiene de quien se va** (Ola 0 · Confianza).
 *
 * Lo que borra las filas lo prueba `OlvidarDatosLocalesTest` en `:core`, contra SQLite de verdad.
 * Acá se prueba el cableado: que [SessionManager.clear] le pida el borrado al repositorio **con el
 * id de quien se va** —antes de soltarlo— y que, si la base todavía no está abierta (un Worker que
 * levantó el proceso solo), el pedido quede anotado en vez de abrirla o perderse.
 */
@RunWith(RobolectricTestRunner::class)
class ElLogoutBorraLaBaseLocalTest {

    private class Grabador : RepositorioDePrueba() {
        val olvidados = mutableListOf<String>()
        override fun olvidarDatosLocales(userId: String) { olvidados += userId }
    }

    @After
    fun limpiar() {
        Repositories.sustitutoDePrueba = null
        SessionManager.borradoLocalPendiente = null
    }

    @Test
    fun `el logout le pide al repositorio borrar lo de quien se va`() {
        val grabador = Grabador()
        Repositories.sustitutoDePrueba = grabador
        SessionManager.save(token = "t", userId = "usr-que-se-va", name = "N", email = "n@movi.test")

        SessionManager.clear()

        assertEquals(listOf("usr-que-se-va"), grabador.olvidados)
        assertNull(SessionManager.userId)
    }

    @Test
    fun `sin la base abierta el borrado queda pendiente para cuando se abra`() {
        SessionManager.save(token = "t", userId = "usr-del-worker", name = "N", email = "n@movi.test")

        SessionManager.clear()

        assertEquals("usr-del-worker", SessionManager.borradoLocalPendiente)
    }

    @Test
    fun `un logout sin sesion no anota nada`() {
        SessionManager.clear()
        assertNull(SessionManager.borradoLocalPendiente)
    }
}
