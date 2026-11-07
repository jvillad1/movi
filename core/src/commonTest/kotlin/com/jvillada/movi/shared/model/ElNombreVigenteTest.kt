package com.jvillada.movi.shared.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Vuelta 138 · [nombreVigente]: lo que Movi propone con un nombre del catálogo sale con el que el dueño
 * le puso. La función no existía: sin ella, «Vivienda» renombrada a «Hogar» volvía por el parser.
 */
class ElNombreVigenteTest {

    private val alias = mapOf("Vivienda" to "Hogar")

    @Test
    fun `una del catalogo renombrada sale con el nombre nuevo`() {
        assertEquals("Hogar", nombreVigente("Vivienda", alias))
        // El modelo de un extracto puede escribirla en minúscula.
        assertEquals("Hogar", nombreVigente("vivienda", alias))
    }

    @Test
    fun `lo que no tiene alias queda igual`() {
        assertEquals("Comida", nombreVigente("Comida", alias))
        assertEquals("Hogar", nombreVigente("Hogar", alias))
        assertEquals("Vivienda", nombreVigente("Vivienda", emptyMap()))
    }

    /** «Educacion» escrita a mano y «Educación» del catálogo son dos categorías: no se aplastan. */
    @Test
    fun `distingue tildes`() {
        assertEquals("Educacion", nombreVigente("Educacion", mapOf("Educación" to "Colegio")))
        assertEquals("Colegio", nombreVigente("Educación", mapOf("Educación" to "Colegio")))
    }

    @Test
    fun `sigue la cadena y no se cuelga con un ciclo`() {
        assertEquals("Casa", nombreVigente("Vivienda", mapOf("Vivienda" to "Hogar", "Hogar" to "Casa")))
        val ciclo = mapOf("Vivienda" to "Hogar", "Hogar" to "Vivienda")
        assertEquals("Vivienda", nombreVigente("Vivienda", ciclo))
    }

    @Test
    fun `el alias del cliente sale de las preferencias que trae el Inicio`() {
        val prefs = mapOf(
            "Vivienda" to CategoryPref(hidden = true, renombradaA = "Hogar"),
            "Ropa" to CategoryPref(hidden = true),
            "Mercado" to CategoryPref(icono = "carrito"),
        )
        assertEquals(mapOf("Vivienda" to "Hogar"), aliasDeLasPreferencias(prefs))
    }

    @Test
    fun `el tipo que viaja es el del catalogo`() {
        assertEquals("EXPENSE", tipoDelCatalogo("Vivienda"))
        assertEquals("INCOME", tipoDelCatalogo("Freelance"))
        assertNull(tipoDelCatalogo("Hogar"))
    }
}
