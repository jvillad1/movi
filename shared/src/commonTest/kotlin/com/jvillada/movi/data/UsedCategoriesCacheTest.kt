package com.jvillada.movi.data

import com.jvillada.movi.shared.model.CategoryPref
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

    // ── Ola A: cuántas veces se usó cada categoría en los últimos 60 días ─────────────

    @Test
    fun `recordFromServer guarda los usos recientes por nombre`() {
        UsedCategoriesCache.recordFromServer(
            listOf(
                UsedCategory("Carro", listOf(TransactionType.EXPENSE), usosRecientes = 5),
                UsedCategory("Nómina", listOf(TransactionType.INCOME), usosRecientes = 2),
            ),
        )

        assertEquals(5, UsedCategoriesCache.usosRecientes["Carro"])
        assertEquals(2, UsedCategoriesCache.usosRecientes["Nómina"])
    }

    @Test
    fun `los usos recientes se reemplazan enteros, no se acumulan`() {
        UsedCategoriesCache.recordFromServer(listOf(UsedCategory("Carro", usosRecientes = 5)))
        UsedCategoriesCache.recordFromServer(listOf(UsedCategory("Carro", usosRecientes = 1)))

        assertEquals(1, UsedCategoriesCache.usosRecientes["Carro"])
    }

    @Test
    fun `clear tambien borra los usos recientes`() {
        UsedCategoriesCache.recordFromServer(listOf(UsedCategory("Carro", usosRecientes = 5)))
        UsedCategoriesCache.clear()

        assertTrue(UsedCategoriesCache.usosRecientes.isEmpty())
    }

    // ── Ola 10: el espejo de lo que hace el server al renombrar/unificar ──────

    @Test
    fun `unificar una del catalogo la deja ESCONDIDA, no borrada`() {
        // Era el modo de falla que este mismo caché dice existir para evitar: tras unificar
        // «Otros ingresos» en «Otros», el server la escondía y la lista lo mostraba, pero acá se
        // borraba la preferencia — así que «Agregar → Ingreso» seguía sugiriendo «Otros
        // ingresos», a un toque de volver a partir en dos lo que se acababa de juntar.
        UsedCategoriesCache.record("Otros ingresos", TransactionType.INCOME)
        UsedCategoriesCache.applyRename("Otros ingresos", "Otros", escondeElOrigen = true)

        assertEquals(CategoryPref(hidden = true), UsedCategoriesCache.prefs["Otros ingresos"])
        assertFalse("Otros ingresos" in UsedCategoriesCache.categories)
        assertTrue("Otros" in UsedCategoriesCache.categories)
    }

    @Test
    fun `renombrar una propia no le deja ninguna preferencia fantasma`() {
        // Una categoría propia sin datos deja de existir sola: marcarla escondida la dejaría en
        // la lista para siempre. Solo las del catálogo necesitan el «escondida».
        UsedCategoriesCache.record("Trasnporte", TransactionType.EXPENSE)
        UsedCategoriesCache.applyRename("Trasnporte", "Transporte", escondeElOrigen = true)

        assertFalse("Trasnporte" in UsedCategoriesCache.prefs)
        assertFalse("Trasnporte" in UsedCategoriesCache.categories)
    }

    @Test
    fun `el destino hereda el tipo fijado del origen y nunca queda escondido`() {
        UsedCategoriesCache.recordFromServer(
            listOf(
                UsedCategory("Carro", listOf(TransactionType.EXPENSE), pinnedType = "BOTH"),
                UsedCategory("Auto", hidden = true),
            ),
        )
        UsedCategoriesCache.applyRename("Carro", "Auto")

        assertEquals(CategoryPref(hidden = false, pinnedType = "BOTH"), UsedCategoriesCache.prefs["Auto"])
        assertFalse("Carro" in UsedCategoriesCache.prefs)
    }

    // ── Ola B: ícono y color, el mismo espejo que ya lleva pinnedType ──────────

    @Test
    fun `recordFromServer guarda el icono y el color`() {
        UsedCategoriesCache.recordFromServer(
            listOf(UsedCategory("Mercado", icono = "restaurante", color = "naranja")),
        )

        assertEquals(
            CategoryPref(icono = "restaurante", color = "naranja"),
            UsedCategoriesCache.prefs["Mercado"],
        )
    }

    @Test
    fun `applyPref con solo icono o color no lo descarta como si fuera el default`() {
        // Antes de Ola B, una preferencia sin `hidden` y sin `pinnedType` se trataba como "no
        // dice nada distinto del default" y se borraba. Con ícono y color eso ya no es cierto:
        // guardar un ícono sin esconder ni fijar el tipo es exactamente el caso de uso nuevo.
        UsedCategoriesCache.applyPref("Mercado", CategoryPref(icono = "restaurante"))

        assertEquals(CategoryPref(icono = "restaurante"), UsedCategoriesCache.prefs["Mercado"])
    }

    @Test
    fun `el destino hereda el icono y el color del origen si no tiene los suyos`() {
        UsedCategoriesCache.recordFromServer(
            listOf(
                UsedCategory("Trasnporte", listOf(TransactionType.EXPENSE), icono = "bus", color = "verde"),
                UsedCategory("Transporte"),
            ),
        )
        UsedCategoriesCache.applyRename("Trasnporte", "Transporte")

        assertEquals(
            CategoryPref(hidden = false, icono = "bus", color = "verde"),
            UsedCategoriesCache.prefs["Transporte"],
        )
    }

    @Test
    fun `el destino conserva su propio icono y color en vez del origen`() {
        UsedCategoriesCache.recordFromServer(
            listOf(
                UsedCategory("Trasnporte", listOf(TransactionType.EXPENSE), icono = "bus", color = "verde"),
                UsedCategory("Transporte", icono = "carro", color = "azul"),
            ),
        )
        UsedCategoriesCache.applyRename("Trasnporte", "Transporte")

        assertEquals(
            CategoryPref(hidden = false, icono = "carro", color = "azul"),
            UsedCategoriesCache.prefs["Transporte"],
        )
    }
}
