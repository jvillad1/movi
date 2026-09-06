package com.jvillada.movi.data

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * **Este test corre en una JVM donde `Settings()` no se puede construir**, igual que
 * `LastAccountStoreTest` (ver su KDoc): prueba que tocar este `object` no tumba nada además de
 * probar la lógica de plegado en sí.
 */
class DiasPlegadosStoreTest {

    @BeforeTest fun limpiarAntes() = DiasPlegadosStore.clear()
    @AfterTest fun limpiarDespues() = DiasPlegadosStore.clear()

    @Test
    fun `arranca sin dias plegados y no explota aunque no haya donde guardar`() {
        assertTrue(DiasPlegadosStore.plegados().isEmpty())
    }

    @Test
    fun `plegar un dia lo agrega al conjunto`() {
        val nuevo = DiasPlegadosStore.alternar("2026-09-06")
        assertEquals(setOf("2026-09-06"), nuevo)
        assertEquals(setOf("2026-09-06"), DiasPlegadosStore.plegados())
    }

    @Test
    fun `plegar dos veces el mismo dia lo despliega de nuevo`() {
        DiasPlegadosStore.alternar("2026-09-06")
        val nuevo = DiasPlegadosStore.alternar("2026-09-06")
        assertTrue(nuevo.isEmpty())
    }

    @Test
    fun `cerrar sesion borra los dias del usuario que se va`() {
        DiasPlegadosStore.alternar("2026-09-06")
        DiasPlegadosStore.clear()
        assertTrue(DiasPlegadosStore.plegados().isEmpty())
    }

    /** No son fechas reales, pero ordenan como texto igual que las ISO — que es lo único que usa el store. */
    private fun diaDeRelleno(i: Int): String = "2026-09-" + i.toString().padStart(3, '0')

    @Test
    fun `el dia recien plegado sobrevive al recorte aunque sea el mas viejo de todos`() {
        // 400 fechas ya plegadas, todas más recientes que la que se va a plegar ahora.
        (1..400).forEach { i -> DiasPlegadosStore.alternar(diaDeRelleno(i)) }
        val nuevo = DiasPlegadosStore.alternar("2020-01-01")

        assertEquals(400, nuevo.size)
        assertTrue("2020-01-01" in nuevo, "el día recién tocado no puede desaparecer sin que el tap se note")
    }

    @Test
    fun `el recorte descarta la mas vieja de las recientes, no la recien plegada`() {
        (1..400).forEach { i -> DiasPlegadosStore.alternar(diaDeRelleno(i)) }
        DiasPlegadosStore.alternar("2026-09-401")

        assertFalse(diaDeRelleno(1) in DiasPlegadosStore.plegados(), "debió salir la más vieja")
        assertTrue("2026-09-401" in DiasPlegadosStore.plegados())
    }
}
