package com.jvillada.movi.ui.categorias

import com.jvillada.movi.shared.model.CATEGORY_TYPE_BOTH
import com.jvillada.movi.shared.model.CategoryScope
import com.jvillada.movi.shared.model.CategoryUsage
import com.jvillada.movi.shared.model.TransactionType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Las reglas de la pantalla «Categorías», sin Compose. Lo que se fija acá es sobre todo que el
 * **tipo sea un filtro** y no la identidad de la categoría: es la decisión de diseño de toda la
 * ola, y es la que se rompe sola si alguien vuelve a leer `Category.type` directo.
 */
class CategoriasLogicTest {

    private fun cat(
        name: String,
        scope: CategoryScope = CategoryScope.CUSTOM,
        usedTypes: List<TransactionType> = emptyList(),
        pinnedType: String? = null,
        hidden: Boolean = false,
        reserved: Boolean = false,
        movements: Int = 0,
        total: Long = 0,
        monthMovements: Int = 0,
        monthTotal: Long = 0,
        budgets: Int = 0,
        recurringRules: Int = 0,
        otherCurrencyMovements: Int = 0,
    ) = CategoryUsage(
        name = name, scope = scope, reserved = reserved, usedTypes = usedTypes,
        pinnedType = pinnedType, hidden = hidden, movements = movements, total = total,
        monthMovements = monthMovements, monthTotal = monthTotal,
        otherCurrencyMovements = otherCurrencyMovements,
        budgets = budgets, recurringRules = recurringRules,
    )

    // ── El filtro por tipo ────────────────────────────────────────────────────

    @Test
    fun `Todas muestra tambien las escondidas`() {
        // Un filtro llamado «Todas» que esconde cosas sería mentir — y además es donde el dueño
        // va a buscar la que escondió por error.
        val lista = listOf(cat("Ropa", hidden = true), cat("Comida"))
        assertEquals(listOf("Ropa", "Comida"), filtrarCategorias(lista, CategoryFilter.TODAS).map { it.name })
    }

    @Test
    fun `Gastos deja fuera las escondidas`() {
        val lista = listOf(cat("Ropa", usedTypes = listOf(TransactionType.EXPENSE), hidden = true))
        assertTrue(filtrarCategorias(lista, CategoryFilter.GASTOS).isEmpty())
    }

    @Test
    fun `una categoria en Ambos aparece en Gastos Y en Ingresos`() {
        // El corazón del cambio: «Otros» fijada en «Ambos» sirve para las dos cosas.
        val lista = listOf(cat("Otros", scope = CategoryScope.PREDEFINED, pinnedType = CATEGORY_TYPE_BOTH))
        assertEquals(listOf("Otros"), filtrarCategorias(lista, CategoryFilter.GASTOS).map { it.name })
        assertEquals(listOf("Otros"), filtrarCategorias(lista, CategoryFilter.INGRESOS).map { it.name })
    }

    @Test
    fun `el tipo fijado le gana al catalogo tambien en el filtro`() {
        // «Comida» es EXPENSE en el catálogo; fijada en INCOME sale de Gastos y entra a Ingresos.
        val lista = listOf(cat("Comida", scope = CategoryScope.PREDEFINED, pinnedType = "INCOME"))
        assertTrue(filtrarCategorias(lista, CategoryFilter.GASTOS).isEmpty())
        assertEquals(1, filtrarCategorias(lista, CategoryFilter.INGRESOS).size)
    }

    @Test
    fun `una categoria propia sin uso conocido aparece en los dos filtros`() {
        // Ante la duda, mostrar: esconder por falta de datos es peor que sugerir de más.
        val lista = listOf(cat("Colegio"))
        assertEquals(1, filtrarCategorias(lista, CategoryFilter.GASTOS).size)
        assertEquals(1, filtrarCategorias(lista, CategoryFilter.INGRESOS).size)
    }

    @Test
    fun `Escondidas muestra solo las escondidas`() {
        val lista = listOf(cat("Ropa", hidden = true), cat("Comida"))
        assertEquals(listOf("Ropa"), filtrarCategorias(lista, CategoryFilter.ESCONDIDAS).map { it.name })
    }

    @Test
    fun `la busqueda ignora tildes y mayusculas`() {
        val lista = listOf(cat("Educación"), cat("Comida"))
        assertEquals(listOf("Educación"), filtrarCategorias(lista, CategoryFilter.TODAS, "EDUCACION").map { it.name })
    }

    // ── Las etiquetas ─────────────────────────────────────────────────────────

    @Test
    fun `la etiqueta de tipo dice lo fijado, no lo del catalogo`() {
        assertEquals("Ambos", etiquetaDeTipo(cat("Otros", scope = CategoryScope.PREDEFINED, pinnedType = CATEGORY_TYPE_BOTH)))
        assertEquals("Gasto", etiquetaDeTipo(cat("Comida", scope = CategoryScope.PREDEFINED)))
        assertEquals("Ingreso", etiquetaDeTipo(cat("Salario", scope = CategoryScope.PREDEFINED)))
        assertEquals("Sin usar", etiquetaDeTipo(cat("Colegio")))
    }

    @Test
    fun `el resumen de uso dice movimientos y plata`() {
        val resumen = resumenDeUso(cat("Comida", movements = 12, total = 450_000))
        assertTrue(resumen.contains("12 movimientos"), resumen)
        assertTrue(resumen.contains("450"), resumen)
    }

    @Test
    fun `una categoria sin nada detras lo dice`() {
        assertEquals("Sin movimientos", resumenDeUso(cat("Freelance", scope = CategoryScope.PREDEFINED)))
    }

    @Test
    fun `los movimientos en otra moneda se cuentan aparte y no se suman`() {
        // Sumar dólares y pesos en un solo número sería mentir.
        val resumen = resumenDeUso(cat("Tecnología", movements = 2, total = 100_000, otherCurrencyMovements = 3))
        assertTrue(resumen.contains("3 en otra moneda"), resumen)
        assertTrue(resumen.contains("2 movimientos"), resumen)
    }

    @Test
    fun `el resumen del mes es nulo si no la uso este mes`() {
        assertEquals(null, resumenDelMes(cat("Comida", movements = 12, total = 450_000)))
        assertTrue(resumenDelMes(cat("Comida", monthMovements = 3, monthTotal = 80_000))!!.contains("3 movimientos"))
    }

    // ── El aviso antes de unificar ────────────────────────────────────────────

    @Test
    fun `el aviso de unificar dice cuantos movimientos cambian de nombre`() {
        val aviso = avisoDeUnificacion(cat("Trasnporte", movements = 2, budgets = 1, recurringRules = 1), "Transporte")
        assertTrue(aviso.contains("2 movimientos"), aviso)
        assertTrue(aviso.contains("su presupuesto"), aviso)
        assertTrue(aviso.contains("1 recurrente"), aviso)
        assertTrue(aviso.contains("Transporte"), aviso)
        assertTrue(aviso.contains("No se borra nada"), aviso)
    }

    @Test
    fun `unificar una categoria vacia lo dice en vez de prometer un cambio`() {
        val aviso = avisoDeUnificacion(cat("Otros ingresos", scope = CategoryScope.PREDEFINED), "Otros")
        assertTrue(aviso.contains("Nada cambia de nombre"), aviso)
    }

    @Test
    fun `la etiqueta del selector de tipo cubre las cuatro opciones`() {
        assertEquals("Automático", etiquetaDeTipoFijado(null))
        assertEquals("Gasto", etiquetaDeTipoFijado("EXPENSE"))
        assertEquals("Ingreso", etiquetaDeTipoFijado("INCOME"))
        assertEquals("Ambos", etiquetaDeTipoFijado(CATEGORY_TYPE_BOTH))
    }
}
