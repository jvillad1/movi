package com.jvillada.movi.ui.components

import com.jvillada.movi.shared.model.CARD_PAYMENT_CATEGORY
import com.jvillada.movi.shared.model.CATEGORY_NAME_ORDER
import com.jvillada.movi.shared.model.CategoryPref
import com.jvillada.movi.shared.model.TransactionType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * **Ola L — una sola lista de pastillas para las pantallas que eligen categoría.**
 *
 * El detalle de un SMS armaba la suya en orden alfabético con `take(10)`: «Hija», «Fútbol» y
 * «Mercado extra» —las que el dueño usa— quedaban fuera del corte detrás de diez del catálogo.
 * [categoriasParaPastillas] es la misma regla que ya usaban los chips de «Agregar»
 * ([categoriasFrecuentes]), con lo que la pantalla quiera poner primero (la propuesta de Movi, la
 * categoría actual) y todas las propias detrás.
 */
class PastillasDeCategoriaTest {

    private val gasto = TransactionType.EXPENSE

    private val propias = mapOf(
        "Hija" to setOf(gasto),
        "Fútbol" to setOf(gasto),
        "Mercado extra" to setOf(gasto),
        "Gimnasio" to setOf(gasto), // sin usos en 60 días: igual es suya
        "Gardenera" to setOf(TransactionType.INCOME), // del otro lado
    )
    private val usos = mapOf("Hija" to 12, "Fútbol" to 7, "Mercado extra" to 3, "Comida" to 9)

    @Test
    fun `lo que la pantalla pone primero va primero, y una sola vez`() {
        val r = categoriasParaPastillas(listOf("Fútbol"), gasto, propias, emptyMap(), usos)
        assertEquals("Fútbol", r.first())
        assertEquals(r.distinctBy { it.lowercase() }, r, "no se repite: $r")
    }

    @Test
    fun `sigue el orden de uso reciente, no el alfabetico`() {
        val r = categoriasParaPastillas(listOf("Otros"), gasto, propias, emptyMap(), usos)
        assertEquals(listOf("Otros", "Hija", "Comida", "Fútbol", "Mercado extra"), r.take(5))
    }

    @Test
    fun `las propias que no se usaron hace poco tambien estan, antes que el catalogo`() {
        val r = categoriasParaPastillas(emptyList(), gasto, propias, emptyMap(), usos, cuantas = 50)
        val gimnasio = r.indexOf("Gimnasio")
        assertTrue(gimnasio >= 0, "«Gimnasio» falta: $r")
        // «Educación» es del catálogo y va detrás de las propias sin uso reciente.
        assertTrue(gimnasio < r.indexOf("Educación"), "las propias van antes que el catálogo: $r")
    }

    @Test
    fun `hija entra en el corte aunque el catalogo sea largo`() {
        // El defecto del SMS: con orden alfabético y take(10), «Hija» (H) quedaba fuera.
        val r = categoriasParaPastillas(listOf("Otros"), gasto, propias, emptyMap(), usos, cuantas = 6)
        assertTrue("Hija" in r && "Fútbol" in r, "faltan sus categorías: $r")
    }

    @Test
    fun `respeta el tipo, las escondidas y las reservadas`() {
        val prefs = mapOf("Fútbol" to CategoryPref(hidden = true))
        val r = categoriasParaPastillas(
            emptyList(), gasto, propias, prefs, usos + (CARD_PAYMENT_CATEGORY to 40), cuantas = 50,
        )
        assertFalse("Gardenera" in r, "es de ingreso: $r")
        assertFalse("Fútbol" in r, "la escondió: $r")
        assertFalse(CARD_PAYMENT_CATEGORY in r, "es reservada: $r")
    }

    @Test
    fun `una propuesta que no esta en ningun catalogo igual se ve`() {
        val r = categoriasParaPastillas(listOf("Cosa importada"), gasto, propias, emptyMap(), usos)
        assertEquals("Cosa importada", r.first())
    }

    @Test
    fun `sin tipo conocido igual hay de donde elegir`() {
        val r = categoriasParaPastillas(listOf(null, " "), null, propias, emptyMap(), usos)
        assertTrue(r.isNotEmpty())
        assertTrue(r.all { it.isNotBlank() })
    }

    @Test
    fun `sin usos cae en propias y catalogo, cada grupo alfabetico`() {
        val r = categoriasParaPastillas(emptyList(), gasto, propias, emptyMap(), emptyMap(), cuantas = 50)
        // Las propias del lado del gasto primero, alfabéticas: «Gardenera» es de ingreso.
        assertEquals(listOf("Fútbol", "Gimnasio", "Hija", "Mercado extra"), r.take(4))
        // Después el catálogo, alfabético.
        val catalogo = r.drop(4)
        assertEquals(catalogo.sortedWith(CATEGORY_NAME_ORDER), catalogo, "el catálogo no va alfabético: $catalogo")
        assertTrue("Comida" in catalogo && "Educación" in catalogo)
    }

    @Test
    fun `respeta el tope`() {
        val r = categoriasParaPastillas(listOf("Otros"), gasto, propias, emptyMap(), usos, cuantas = 3)
        assertEquals(3, r.size)
    }
}
