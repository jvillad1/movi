package com.jvillada.movi.data

import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * **«Ocultar los saldos» se alterna, se recuerda en el aparato y se va con la sesión.**
 *
 * «Se recuerda» se prueba contra el almacén de mentira de [SaldosOcultosStore.almacenDePrueba]:
 * en Robolectric `Settings()` no guarda nada.
 */
@RunWith(RobolectricTestRunner::class)
class SaldosOcultosStoreTest {

    /** Lo que quedaría en el aparato. Ver [SaldosOcultosStore.almacenDePrueba]. */
    private val aparato = mutableMapOf<String, Boolean>()

    @Before
    fun enchufarElAparato() {
        SaldosOcultosStore.almacenDePrueba = aparato
    }

    @Test
    fun `arranca con las cifras a la vista`() {
        assertFalse(SaldosOcultosStore.ocultos)
        assertFalse(SaldosOcultosStore.leerGuardado())
    }

    @Test
    fun `alternar prende y apaga`() {
        SaldosOcultosStore.alternar()
        assertTrue(SaldosOcultosStore.ocultos)
        SaldosOcultosStore.alternar()
        assertFalse(SaldosOcultosStore.ocultos)
    }

    @Test
    fun `se recuerda en el aparato`() {
        SaldosOcultosStore.poner(true)
        assertTrue(SaldosOcultosStore.leerGuardado(), "lo que lee el próximo arranque")
        assertEquals(mapOf("saldos_ocultos" to true), aparato)
        SaldosOcultosStore.poner(false)
        assertFalse(SaldosOcultosStore.leerGuardado())
    }

    @Test
    fun `cerrar sesion vuelve a mostrar las cifras y lo olvida`() {
        SessionManager.save(token = "tok", userId = "u1", name = "Juan", email = "juan@ejemplo.com")
        SaldosOcultosStore.poner(true)
        SessionManager.clear()
        assertFalse(SaldosOcultosStore.ocultos)
        assertFalse(SaldosOcultosStore.leerGuardado(), "el siguiente en este aparato arranca viendo sus cifras")
    }

    @Test
    fun `avisa cuando cambia, y solo cuando cambia`() {
        var avisos = 0
        SaldosOcultosStore.alCambiar = { avisos++ }
        try {
            SaldosOcultosStore.poner(true)
            SaldosOcultosStore.poner(true)
            SaldosOcultosStore.alternar()
            SaldosOcultosStore.clear()
            assertEquals(2, avisos, "prender, apagar; poner lo mismo y limpiar lo ya limpio no repintan el widget")
        } finally {
            SaldosOcultosStore.alCambiar = null
        }
    }
}
