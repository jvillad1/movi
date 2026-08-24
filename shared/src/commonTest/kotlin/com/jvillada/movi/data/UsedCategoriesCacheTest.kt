package com.jvillada.movi.data

import com.jvillada.movi.shared.model.TRANSFER_CATEGORY
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * El caché de sugerencias no puede ofrecer la categoría reservada.
 *
 * Movimientos y Presupuestos alimentan este caché con **todas** las categorías que ven, incluidas
 * las de las patas de un traspaso. Sin este filtro, «Traspaso» aparecía como sugerencia en el
 * campo de categoría de Agregar, en Cambiar categoría y en Presupuestos — o sea, la app le
 * ofrecía al dueño escribir la categoría que después iba a rechazar, y en el camino le hacía
 * desaparecer un gasto real del mes.
 */
class UsedCategoriesCacheTest {

    @Test
    fun `la categoria reservada nunca entra al caché de sugerencias`() {
        UsedCategoriesCache.record(listOf("Mercado", TRANSFER_CATEGORY, "Transporte"))

        assertFalse(TRANSFER_CATEGORY in UsedCategoriesCache.categories)
        assertTrue("Mercado" in UsedCategoriesCache.categories)
        assertTrue("Transporte" in UsedCategoriesCache.categories)
    }

    @Test
    fun `una lista que solo trae la categoria reservada no agrega nada`() {
        UsedCategoriesCache.record(listOf(TRANSFER_CATEGORY))

        assertFalse(TRANSFER_CATEGORY in UsedCategoriesCache.categories)
    }

    /** Con espacios alrededor tampoco: se recorta antes de comparar, igual que el resto. */
    @Test
    fun `la categoria reservada con espacios tampoco entra`() {
        UsedCategoriesCache.record(listOf("  $TRANSFER_CATEGORY  "))

        assertFalse(TRANSFER_CATEGORY in UsedCategoriesCache.categories)
    }
}
