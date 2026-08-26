package com.jvillada.movi.data

import com.jvillada.movi.shared.model.OPENING_CATEGORY
import com.jvillada.movi.shared.model.ORPHANED_LEG_CATEGORY
import com.jvillada.movi.shared.model.TRANSFER_CATEGORY
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.UsedCategory
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
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

    // El caché es un objeto de proceso: sin esto un test arrastraría lo que anotó el anterior.
    @BeforeTest fun limpiarAntes() = UsedCategoriesCache.clear()
    @AfterTest fun limpiarDespues() = UsedCategoriesCache.clear()

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

    /**
     * Ola 9 · M4: la lista completa que ahora manda el Inicio trae también las categorías que
     * Movi escribe sola. Ofrecerlas como sugerencia era invitar al dueño a escribir en un gasto
     * suyo una categoría reservada — «Saldo inicial» ni siquiera cuenta como flujo de caja.
     */
    @Test
    fun `las categorias que Movi escribe sola tampoco se sugieren`() {
        UsedCategoriesCache.record(listOf(ORPHANED_LEG_CATEGORY, OPENING_CATEGORY, "Mercado"))

        assertFalse(ORPHANED_LEG_CATEGORY in UsedCategoriesCache.categories)
        assertFalse(OPENING_CATEGORY in UsedCategoriesCache.categories)
        assertTrue("Mercado" in UsedCategoriesCache.categories)
    }

    // ── Ola 9 · A3: el tipo con el que se usó cada categoría ──────────────────────────

    @Test
    fun `recuerda con que tipo se uso cada categoria`() {
        UsedCategoriesCache.record("Carro", TransactionType.EXPENSE)

        assertEquals(setOf(TransactionType.EXPENSE), UsedCategoriesCache.used["Carro"])
    }

    @Test
    fun `una categoria usada de los dos lados guarda los dos tipos`() {
        UsedCategoriesCache.record("Carro", TransactionType.EXPENSE)
        UsedCategoriesCache.record("Carro", TransactionType.INCOME)

        assertEquals(
            setOf(TransactionType.EXPENSE, TransactionType.INCOME),
            UsedCategoriesCache.used["Carro"],
        )
    }

    /** Verla sin tipo no borra lo que ya sabíamos: «no se sabe» no es evidencia de nada. */
    @Test
    fun `un registro sin tipo no pisa el tipo ya conocido`() {
        UsedCategoriesCache.record("Carro", TransactionType.EXPENSE)
        UsedCategoriesCache.record(listOf("Carro"))

        assertEquals(setOf(TransactionType.EXPENSE), UsedCategoriesCache.used["Carro"])
    }

    @Test
    fun `una categoria vista sin tipo queda conocida pero sin tipos`() {
        UsedCategoriesCache.record(listOf("Colegio"))

        assertTrue("Colegio" in UsedCategoriesCache.categories)
        assertEquals(emptySet<TransactionType>(), UsedCategoriesCache.used["Colegio"])
    }

    @Test
    fun `lo que manda el Inicio entra con sus tipos`() {
        UsedCategoriesCache.recordFromServer(
            listOf(
                UsedCategory("Carro", listOf(TransactionType.EXPENSE)),
                UsedCategory("Nómina", listOf(TransactionType.INCOME)),
                UsedCategory("Sin tipo", emptyList()),
                UsedCategory(TRANSFER_CATEGORY, listOf(TransactionType.EXPENSE)),
            ),
        )

        assertEquals(setOf(TransactionType.EXPENSE), UsedCategoriesCache.used["Carro"])
        assertEquals(setOf(TransactionType.INCOME), UsedCategoriesCache.used["Nómina"])
        assertEquals(emptySet<TransactionType>(), UsedCategoriesCache.used["Sin tipo"])
        // La categoría reservada tampoco entra por esta puerta.
        assertFalse(TRANSFER_CATEGORY in UsedCategoriesCache.categories)
    }
}
