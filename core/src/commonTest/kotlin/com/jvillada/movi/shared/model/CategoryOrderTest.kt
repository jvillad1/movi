package com.jvillada.movi.shared.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * El orden alfabético de las categorías ([CATEGORY_NAME_ORDER]), que es lo que el dueño pidió con
 * «no me gusta que las categorías no estén tipo orden alfabético sino en cualquier orden».
 *
 * Lo que estos tests protegen no es «que esté ordenado» —eso lo haría cualquier `sortedBy`— sino
 * las tres cosas que un `sortedBy { it.name }` a secas hace MAL en español: la ñ al final de todo,
 * las tildes ordenando como si fueran otra letra, y las mayúsculas mandando sobre el alfabeto.
 */
class CategoryOrderTest {

    /**
     * La lista de prueba lleva tildes, diéresis, Ñ, y el mismo nombre con dos mayúsculas
     * distintas: es el orden de diccionario español, escrito a mano.
     */
    @Test
    fun `orden de diccionario espanol - tildes, enie, dieresis y mayusculas`() {
        val desordenadas = listOf(
            "Zeta", "Ñu", "Educación", "Oso", "otros", "Pingüino", "Nube",
            "Único", "Arriendo recibido", "educacion", "Ñoquis", "Vivienda",
            "Salud", "Ácido", "Otros",
        )
        assertEquals(
            listOf(
                "Ácido",              // la tilde no la manda al final
                "Arriendo recibido",
                "Educación",          // «Educación» ordena como «Educacion»…
                "educacion",          // …y la mayúscula solo desempata
                "Nube",
                "Ñoquis",             // la ñ va DESPUÉS de la n…
                "Ñu",
                "Oso",                // …y ANTES de la o. Nunca detrás de «Zeta».
                "Otros",
                "otros",
                "Pingüino",           // la diéresis ordena como una u pelada
                "Salud",
                "Único",
                "Vivienda",
                "Zeta",
            ),
            desordenadas.sortedWith(CATEGORY_NAME_ORDER),
        )
    }

    @Test
    fun `la enie no cae despues de la zeta - que es lo que hace comparar el nombre crudo`() {
        // El defecto exacto que motivó la clave de orden: comparar `String`s compara UTF-16, y ahí
        // la ñ (0x00F1) está arriba de toda la a–z.
        assertEquals(listOf("Ñu", "Zeta"), listOf("Zeta", "Ñu").sortedWith(CATEGORY_NAME_ORDER))
        assertEquals(listOf("Zeta", "Ñu"), listOf("Zeta", "Ñu").sortedBy { it })
    }

    @Test
    fun `dos nombres que normalizan igual tienen un desempate estable`() {
        // «Otros» y «otros» pueden convivir (el nombre es texto libre). Vengan como vengan, el
        // orden entre ellas es siempre el mismo: sin esto cambiaría entre dos recargas.
        assertEquals(listOf("Otros", "otros"), listOf("Otros", "otros").sortedWith(CATEGORY_NAME_ORDER))
        assertEquals(listOf("Otros", "otros"), listOf("otros", "Otros").sortedWith(CATEGORY_NAME_ORDER))
    }

    @Test
    fun `la clave ignora espacios de sobra en las puntas`() {
        assertEquals(categorySortKey("Comida"), categorySortKey("  Comida  "))
    }

    @Test
    fun `el catalogo entero se ordena sin sorpresas`() {
        val ordenado = PREDEFINED_CATEGORIES.map { it.name }.sortedWith(CATEGORY_NAME_ORDER)
        assertEquals("Arriendo recibido", ordenado.first())
        assertEquals("Vivienda", ordenado.last())
        assertTrue(ordenado.indexOf("Otros") < ordenado.indexOf("Otros ingresos"))
        assertTrue(ordenado.indexOf("Educación") < ordenado.indexOf("Entretenimiento"))
    }
}
