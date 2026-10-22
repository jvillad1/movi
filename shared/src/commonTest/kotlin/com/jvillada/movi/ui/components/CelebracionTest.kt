package com.jvillada.movi.ui.components

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Ola 1 · «Que Movi se sienta viva»: **un solo momento de celebración**, y solo en la transición.
 * Abrir el checklist con todo ya pagado (o «Por revisar» ya vacío) no celebra; completarlo sí, una
 * vez.
 */
class CelebracionTest {

    @Test
    fun `abrir con todo ya pagado no celebra`() {
        val celebracion = Celebracion()
        assertFalse(celebracion.observar(completo = true, huboAccion = false))
        assertFalse(celebracion.observar(completo = true, huboAccion = true), "seguir completo tampoco")
    }

    @Test
    fun `pasar de falta uno a todo pagado celebra una sola vez`() {
        val celebracion = Celebracion()
        assertFalse(celebracion.observar(completo = false, huboAccion = false), "lo que había al abrir")
        assertTrue(celebracion.observar(completo = true, huboAccion = true))
        assertFalse(celebracion.observar(completo = true, huboAccion = true), "no se repite")
    }

    @Test
    fun `mientras recarga no se pierde lo ultimo que se supo`() {
        val celebracion = Celebracion()
        celebracion.observar(completo = false, huboAccion = false)
        assertFalse(celebracion.observar(completo = null, huboAccion = true), "cargando no celebra")
        assertTrue(celebracion.observar(completo = true, huboAccion = true), "falta 1 → cargando → todo pagado")
    }

    @Test
    fun `sin una accion del duenio no se celebra`() {
        val celebracion = Celebracion()
        celebracion.observar(completo = false, huboAccion = false)
        assertFalse(celebracion.observar(completo = true, huboAccion = false), "se completó solo, en otro lado")
    }

    @Test
    fun `volver a la pantalla sigue desde lo ultimo que se supo`() {
        // Por revisar → Reconciliar → Por revisar: la pantalla guarda lo último que vio.
        val alIrse = Celebracion().apply { observar(completo = false, huboAccion = false) }
        val alVolver = Celebracion(alIrse.ultimoConocido)
        assertTrue(alVolver.observar(completo = true, huboAccion = true))
    }

    @Test
    fun `si vuelve a faltar algo, la proxima vez que se complete celebra de nuevo`() {
        val celebracion = Celebracion()
        celebracion.observar(completo = false, huboAccion = false)
        assertTrue(celebracion.observar(completo = true, huboAccion = true))
        celebracion.observar(completo = false, huboAccion = true)
        assertTrue(celebracion.observar(completo = true, huboAccion = true))
    }
}
