package com.jvillada.movi.data

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * El tema son tres opciones desde la Ola 1 · «Que Movi se sienta viva»: **Igual que el teléfono**,
 * Claro y Oscuro. Lo que hay que sostener:
 *
 * - una instalación nueva arranca en «Igual que el teléfono»;
 * - **lo que el dueño ya eligió con el interruptor viejo se respeta**: el booleano `tema_oscuro`
 *   pasa a Oscuro o Claro, nunca a «Igual que el teléfono» (quien tenía Movi oscura no puede abrirla
 *   un día y encontrarla clara porque su teléfono está en claro);
 * - y la elección funciona aunque no haya dónde guardarla.
 *
 * La persistencia no se prueba: `Settings()` no existe en una JVM pelada, y por eso mismo el store
 * envuelve cada lectura y escritura en `runCatching`. La migración se prueba en
 * [preferenciaGuardada], que es pura.
 */
class TemaStoreTest {

    @AfterTest
    fun limpiar() = TemaStore.clear()

    @Test
    fun `una instalacion nueva arranca igual que el telefono`() {
        assertEquals(PreferenciaDeTema.SISTEMA, preferenciaGuardada(nuevo = null, viejoOscuro = null))
        TemaStore.clear()
        assertEquals(PreferenciaDeTema.SISTEMA, TemaStore.preferencia)
    }

    @Test
    fun `el interruptor viejo se migra a Claro u Oscuro`() {
        assertEquals(PreferenciaDeTema.OSCURO, preferenciaGuardada(nuevo = null, viejoOscuro = true))
        assertEquals(PreferenciaDeTema.CLARO, preferenciaGuardada(nuevo = null, viejoOscuro = false))
    }

    @Test
    fun `lo elegido con la opcion nueva gana sobre el interruptor viejo`() {
        assertEquals(PreferenciaDeTema.SISTEMA, preferenciaGuardada(nuevo = "sistema", viejoOscuro = true))
        assertEquals(PreferenciaDeTema.CLARO, preferenciaGuardada(nuevo = "claro", viejoOscuro = true))
        assertEquals(PreferenciaDeTema.OSCURO, preferenciaGuardada(nuevo = "oscuro", viejoOscuro = false))
    }

    @Test
    fun `un valor nuevo que no se entiende cae a lo viejo o al default`() {
        assertEquals(PreferenciaDeTema.OSCURO, preferenciaGuardada(nuevo = "azul", viejoOscuro = true))
        assertEquals(PreferenciaDeTema.SISTEMA, preferenciaGuardada(nuevo = "", viejoOscuro = null))
    }

    @Test
    fun `igual que el telefono sigue al sistema y las otras dos no`() {
        assertTrue(PreferenciaDeTema.SISTEMA.esOscuro(sistemaOscuro = true))
        assertFalse(PreferenciaDeTema.SISTEMA.esOscuro(sistemaOscuro = false))
        assertTrue(PreferenciaDeTema.OSCURO.esOscuro(sistemaOscuro = false))
        assertFalse(PreferenciaDeTema.CLARO.esOscuro(sistemaOscuro = true))
    }

    @Test
    fun `elegir funciona aunque no haya donde guardar`() {
        TemaStore.clear()
        TemaStore.poner(PreferenciaDeTema.CLARO)
        assertEquals(PreferenciaDeTema.CLARO, TemaStore.preferencia, "Poner el claro tiene que verse en el acto")
        assertFalse(TemaStore.oscuroCon(sistemaOscuro = true))
        TemaStore.poner(PreferenciaDeTema.SISTEMA)
        assertTrue(TemaStore.oscuroCon(sistemaOscuro = true), "el widget resuelve «Igual que el teléfono» con el sistema")
        assertFalse(TemaStore.oscuroCon(sistemaOscuro = false))
    }
}
