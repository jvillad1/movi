package com.jvillada.movi.ui.components

import com.jvillada.movi.shared.model.TransactionType
import kotlin.test.Test
import kotlin.test.assertEquals

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
            usedCategories = listOf("Mascotas"),
        )
        assertEquals("Mascotas", result.last())
        assert(result.indexOf("Comida") < result.indexOf("Mascotas"))
    }

    @Test
    fun `una usada que ya es predefinida no se duplica`() {
        val result = suggestCategoryMatches(
            query = "",
            type = TransactionType.EXPENSE,
            usedCategories = listOf("comida", "Salud"), // minúscula a propósito
        )
        assertEquals(1, result.count { it.equals("Comida", ignoreCase = true) })
    }

    @Test
    fun `usadas en blanco o repetidas se ignoran`() {
        val result = suggestCategoryMatches(
            query = "masc",
            type = null,
            usedCategories = listOf("Mascotas", "  ", "Mascotas", ""),
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
}
