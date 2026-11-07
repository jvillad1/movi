package com.jvillada.movi.ui.components

import com.jvillada.movi.shared.model.CategoryPref
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.ui.recurrentes.categoriaSugeridaPorNombre
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Vuelta 138 · las listas del cliente que leen el catálogo dicen el nombre que el dueño le puso.
 *
 * Con «Vivienda» renombrada a «Hogar» (lo que trae el Inicio: «Vivienda» escondida con su alias y
 * «Hogar» de gasto) y sin que el uso haya llegado todavía (un arranque en frío), Agregar ofrece «Hogar»
 * en el lugar de «Vivienda», escribir «vivi» la encuentra, y lo tecleado se guarda como «Hogar». Sin el
 * alias, «Vivienda» desaparecía (escondida) y «Hogar» no estaba hasta que el Inicio cargara.
 */
class LaCategoriaRenombradaEnLasListasTest {

    private val prefs = mapOf(
        "Vivienda" to CategoryPref(hidden = true, renombradaA = "Hogar"),
        "Hogar" to CategoryPref(pinnedType = TransactionType.EXPENSE.name),
    )

    @Test
    fun `la renombrada se ofrece con su nombre nuevo y no con el viejo`() {
        val gastos = suggestCategoryMatches("", TransactionType.EXPENSE, prefs = prefs)
        assertTrue("Hogar" in gastos, "$gastos")
        assertFalse("Vivienda" in gastos)
        // Una sola vez, aunque el uso también la traiga.
        val conUso = suggestCategoryMatches("", TransactionType.EXPENSE, mapOf("Hogar" to setOf(TransactionType.EXPENSE)), prefs)
        assertEquals(1, conUso.count { it == "Hogar" })
        // Y no del lado de los ingresos: el tipo de «Vivienda» viajó.
        assertFalse("Hogar" in suggestCategoryMatches("", TransactionType.INCOME, prefs = prefs))
    }

    @Test
    fun `escribir el nombre viejo encuentra el nuevo`() {
        assertEquals(listOf("Hogar"), suggestCategoryMatches("vivi", TransactionType.EXPENSE, prefs = prefs))
        assertEquals("Hogar", nombreCanonicoConocido("vivienda", prefs = prefs))
    }

    @Test
    fun `el valor inicial de Agregar es la primera del catalogo con su nombre`() {
        val comidaRenombrada = mapOf(
            "Comida" to CategoryPref(hidden = true, renombradaA = "Alimentación"),
            "Alimentación" to CategoryPref(pinnedType = TransactionType.EXPENSE.name),
        )
        assertEquals("Alimentación", categoriaPorDefectoPara(TransactionType.EXPENSE, prefs = comidaRenombrada))
    }

    @Test
    fun `el nombre de un pago que coincide con una del catalogo propone la renombrada`() {
        assertEquals("Hogar", categoriaSugeridaPorNombre("Vivienda", TransactionType.EXPENSE, prefs = prefs))
    }
}
