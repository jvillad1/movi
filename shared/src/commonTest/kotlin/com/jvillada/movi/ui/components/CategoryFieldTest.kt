package com.jvillada.movi.ui.components

import com.jvillada.movi.shared.model.TransactionType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * F35: [suggestCategoryMatches] es el filtro puro detrás de `CategoryField` — texto libre con
 * sugerencias, compartido por QuickAdd, Presupuestos (crear) y Recurrentes (crear/editar).
 */
class CategoryFieldTest {

    @Test
    fun `sin texto sugiere las predefinidas del tipo, en su orden del catalogo`() {
        val result = suggestCategoryMatches(query = "", type = TransactionType.INCOME)
        assertEquals(listOf("Salario", "Freelance", "Arriendo recibido", "Inversiones", "Otros ingresos"), result)
    }

    @Test
    fun `filtra por tipo y BOTH, sin mezclar categorias del otro tipo`() {
        val result = suggestCategoryMatches(query = "", type = TransactionType.EXPENSE)
        assert(result.none { it == "Salario" || it == "Freelance" })
        assert("Comida" in result)
    }

    @Test
    fun `contiene ignora mayusculas`() {
        val result = suggestCategoryMatches(query = "COMI", type = TransactionType.EXPENSE)
        assertEquals(listOf("Comida"), result)
    }

    @Test
    fun `contiene ignora tildes tanto en la consulta como en el nombre`() {
        // "tecnologia" sin tilde debe encontrar "Tecnología" (con tilde).
        val result = suggestCategoryMatches(query = "tecnologia", type = TransactionType.EXPENSE)
        assertEquals(listOf("Tecnología"), result)
    }

    @Test
    fun `contiene ignora tildes escritas por quien pregunta`() {
        // Al revés: si alguien escribe "á" y el nombre no la lleva, también matchea normalizado.
        val result = suggestCategoryMatches(query = "educación", type = TransactionType.EXPENSE)
        assertEquals(listOf("Educación"), result)
    }

    @Test
    fun `las usadas van despues de las predefinidas`() {
        val result = suggestCategoryMatches(
            query = "",
            type = TransactionType.EXPENSE,
            usedCategories = mapOf("Mascotas" to setOf(TransactionType.EXPENSE)),
        )
        assertEquals("Mascotas", result.last())
        assert(result.indexOf("Comida") < result.indexOf("Mascotas"))
    }

    @Test
    fun `una usada que ya es predefinida no se duplica`() {
        val result = suggestCategoryMatches(
            query = "",
            type = TransactionType.EXPENSE,
            usedCategories = mapOf("comida" to emptySet(), "Salud" to emptySet()), // minúscula a propósito
        )
        assertEquals(1, result.count { it.equals("Comida", ignoreCase = true) })
    }

    @Test
    fun `usadas en blanco o repetidas se ignoran`() {
        val result = suggestCategoryMatches(
            query = "masc",
            type = null,
            usedCategories = mapOf("Mascotas" to emptySet(), "  " to emptySet(), "" to emptySet()),
        )
        assertEquals(listOf("Mascotas"), result)
    }

    @Test
    fun `sin tipo no filtra el catalogo por lado`() {
        val result = suggestCategoryMatches(query = "otro", type = null)
        assert("Otros" in result)
        assert("Otros ingresos" in result)
    }

    @Test
    fun `una consulta sin coincidencias no sugiere nada`() {
        val result = suggestCategoryMatches(query = "xyzxyz", type = TransactionType.EXPENSE)
        assertEquals(emptyList(), result)
    }

    // ── Ola 9 · A3: las categorías propias se ofrecen según cómo se usaron ─────────────

    @Test
    fun `una categoria propia usada solo en gastos no se ofrece al anotar un ingreso`() {
        val used = mapOf("Carro" to setOf(TransactionType.EXPENSE))
        assert("Carro" in suggestCategoryMatches("", TransactionType.EXPENSE, used))
        assert("Carro" !in suggestCategoryMatches("", TransactionType.INCOME, used))
    }

    @Test
    fun `una categoria propia usada de los dos lados se ofrece en los dos`() {
        val used = mapOf("Carro" to setOf(TransactionType.EXPENSE, TransactionType.INCOME))
        assert("Carro" in suggestCategoryMatches("", TransactionType.EXPENSE, used))
        assert("Carro" in suggestCategoryMatches("", TransactionType.INCOME, used))
    }

    @Test
    fun `ante la duda se muestra - sin tipos conocidos se ofrece en los dos lados`() {
        // Arranque en frío: la conocemos (vino de un presupuesto, de una regla vieja) pero no
        // sabemos de qué lado. Esconderla por falta de datos sería peor que sugerirla de más.
        val used = mapOf("Colegio" to emptySet<TransactionType>())
        assert("Colegio" in suggestCategoryMatches("", TransactionType.EXPENSE, used))
        assert("Colegio" in suggestCategoryMatches("", TransactionType.INCOME, used))
    }

    @Test
    fun `sin tipo se ofrecen todas las propias, del lado que sean`() {
        val used = mapOf("Carro" to setOf(TransactionType.EXPENSE))
        assert("Carro" in suggestCategoryMatches("", type = null, usedCategories = used))
    }

    // ── Ola 9 · A1: la opción de crear ────────────────────────────────────────────────

    @Test
    fun `ofrece crear cuando lo escrito no coincide con ninguna sugerencia`() {
        val matches = suggestCategoryMatches("Carro", TransactionType.EXPENSE)
        assertEquals(emptyList(), matches)
        assertTrue(shouldOfferCreateCategory("Carro", matches))
    }

    @Test
    fun `coincidencia parcial - se ve la sugerencia Y la opcion de crear`() {
        // "Sal" con "Salario" en el catálogo: las dos cosas, sin que una tape a la otra.
        val matches = suggestCategoryMatches("Sal", TransactionType.INCOME)
        assertEquals(listOf("Salario"), matches)
        assertTrue(shouldOfferCreateCategory("Sal", matches))
    }

    @Test
    fun `no ofrece crear lo que ya existe, ni con otras mayusculas o tildes`() {
        val matches = suggestCategoryMatches("educacion", TransactionType.EXPENSE)
        assertEquals(listOf("Educación"), matches)
        assertFalse(shouldOfferCreateCategory("educacion", matches))
    }

    /**
     * Ola 9 · B4: «Carro» existe (como gasto) aunque el filtro por tipo la esconda al anotar un
     * ingreso. Ofrecer «Crear "Carro"» ahí prometía algo nuevo que no crea nada.
     */
    @Test
    fun `no ofrece crear algo que ya existe aunque el tipo lo esconda`() {
        val used = mapOf("Carro" to setOf(TransactionType.EXPENSE))
        val matches = suggestCategoryMatches("Carro", TransactionType.INCOME, used)

        assertEquals(emptyList(), matches, "el filtro por tipo la esconde")
        assertFalse(shouldOfferCreateCategory("Carro", matches, conocidas = used.keys))
        // Pero una que de verdad no existe sí se ofrece.
        assertTrue(shouldOfferCreateCategory("Moto", matches, conocidas = used.keys))
    }

    @Test
    fun `no ofrece crear con el campo vacio o en blanco`() {
        assertFalse(shouldOfferCreateCategory("", emptyList()))
        assertFalse(shouldOfferCreateCategory("   ", emptyList()))
    }
}
