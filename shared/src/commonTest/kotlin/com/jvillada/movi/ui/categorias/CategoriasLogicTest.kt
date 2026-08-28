package com.jvillada.movi.ui.categorias

import com.jvillada.movi.shared.model.CATEGORY_TYPE_BOTH
import com.jvillada.movi.shared.model.CategoryScope
import com.jvillada.movi.shared.model.CategoryUsage
import com.jvillada.movi.shared.model.TransactionType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
        incomeTotal: Long = 0,
        monthMovements: Int = 0,
        monthTotal: Long = 0,
        monthIncomeTotal: Long = 0,
        budgets: Int = 0,
        recurringRules: Int = 0,
        otherCurrencyMovements: Int = 0,
    ) = CategoryUsage(
        name = name, scope = scope, reserved = reserved, usedTypes = usedTypes,
        pinnedType = pinnedType, hidden = hidden, movements = movements, total = total,
        incomeTotal = incomeTotal,
        monthMovements = monthMovements, monthTotal = monthTotal,
        monthIncomeTotal = monthIncomeTotal,
        otherCurrencyMovements = otherCurrencyMovements,
        budgets = budgets, recurringRules = recurringRules,
    )

    // ── El filtro por tipo ────────────────────────────────────────────────────

    @Test
    fun `Todas muestra tambien las escondidas`() {
        // Un filtro llamado «Todas» que esconde cosas sería mentir — y además es donde el dueño
        // va a buscar la que escondió por error.
        val lista = listOf(cat("Ropa", hidden = true), cat("Comida"))
        // (Salen alfabéticas, no en el orden en que llegaron — ver los tests de orden más abajo.)
        assertEquals(listOf("Comida", "Ropa"), filtrarCategorias(lista, CategoryFilter.TODAS).map { it.name })
    }

    // ── El orden de la lista ──────────────────────────────────────────────────
    // «No me gusta que las categorías no estén tipo orden alfabético sino en cualquier orden.»
    // El «cualquier orden» era el del server: lo más usado primero. Ese orden tenía su razón
    // escrita —reconocer lo que sobra por contraste con lo que se usa— y no se pierde: cada
    // renglón sigue diciendo su uso. Lo que se gana es poder encontrar una por su nombre.

    @Test
    fun `la lista sale en orden alfabetico, no por uso`() {
        val lista = listOf(
            cat("Vivienda", movements = 90),
            cat("Comida", movements = 300),
            cat("Ñoquis", movements = 1),
            cat("Educación", movements = 40),
            cat("Ácido fólico", movements = 0),
        )
        assertEquals(
            listOf("Ácido fólico", "Comida", "Educación", "Ñoquis", "Vivienda"),
            filtrarCategorias(lista, CategoryFilter.TODAS).map { it.name },
        )
    }

    @Test
    fun `las reservadas quedan al final aunque el alfabeto las pusiera antes`() {
        // No se pueden tocar: intercaladas serían renglones muertos en medio de la lista.
        val lista = listOf(cat("Vivienda"), cat("Cuenta eliminada", reserved = true), cat("Comida"))
        assertEquals(
            listOf("Comida", "Vivienda", "Cuenta eliminada"),
            filtrarCategorias(lista, CategoryFilter.TODAS).map { it.name },
        )
    }

    @Test
    fun `buscando, lo que empieza con lo escrito va antes que lo que apenas lo contiene`() {
        val lista = listOf(cat("Bancolombia"), cat("Comida"))
        assertEquals(
            listOf("Comida", "Bancolombia"),
            filtrarCategorias(lista, CategoryFilter.TODAS, "co").map { it.name },
        )
    }

    @Test
    fun `el orden dentro de cada pastilla de filtro tambien es alfabetico`() {
        val lista = listOf(
            cat("Vivienda", usedTypes = listOf(TransactionType.EXPENSE), hidden = true),
            cat("Comida", usedTypes = listOf(TransactionType.EXPENSE), hidden = true),
            cat("Salud", usedTypes = listOf(TransactionType.EXPENSE)),
            cat("Arriendo", usedTypes = listOf(TransactionType.EXPENSE)),
        )
        assertEquals(
            listOf("Arriendo", "Salud"),
            filtrarCategorias(lista, CategoryFilter.GASTOS).map { it.name },
        )
        assertEquals(
            listOf("Comida", "Vivienda"),
            filtrarCategorias(lista, CategoryFilter.ESCONDIDAS).map { it.name },
        )
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
    fun `los gastos y los ingresos NO se suman en un solo numero`() {
        // Los importes se guardan en positivo y el signo lo lleva el tipo: un solo total daba
        // «$130.000» para 100k de gasto y 30k de ingreso — un número sin significado, justo en
        // una categoría de tipo «Ambos», que es el caso que esta ola habilita.
        val resumen = resumenDeUso(
            cat("Otros", movements = 4, total = 100_000, incomeTotal = 30_000, pinnedType = CATEGORY_TYPE_BOTH),
        )
        assertTrue(resumen.contains("100.000"), resumen)
        assertTrue(resumen.contains("30.000"), resumen)
        assertFalse(resumen.contains("130.000"), resumen)
    }

    @Test
    fun `el resumen del mes tambien separa gasto de ingreso`() {
        val resumen = resumenDelMes(cat("Otros", monthMovements = 2, monthTotal = 10_000, monthIncomeTotal = 5_000))!!
        assertTrue(resumen.contains("10.000"), resumen)
        assertTrue(resumen.contains("5.000"), resumen)
        assertFalse(resumen.contains("15.000"), resumen)
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
        val aviso = avisoDeUnificacion(
            cat("Trasnporte", movements = 2, budgets = 1, recurringRules = 1),
            cat("Transporte", scope = CategoryScope.PREDEFINED),
        )
        assertTrue(aviso.contains("2 movimientos"), aviso)
        assertTrue(aviso.contains("su presupuesto"), aviso)
        assertTrue(aviso.contains("1 recurrente"), aviso)
        assertTrue(aviso.contains("Transporte"), aviso)
        assertTrue(aviso.contains("No se borra nada"), aviso)
    }

    @Test
    fun `si las dos tienen presupuesto, el aviso lo dice ANTES y avisa que no se deshace`() {
        // La suma de los dos límites es lo único de la operación que no es «el mismo dato con
        // otro nombre»: le cambia un número que el dueño puso a propósito, y es irreversible.
        val aviso = avisoDeUnificacion(
            cat("Trasnporte", movements = 2, budgets = 1),
            cat("Transporte", scope = CategoryScope.PREDEFINED, budgets = 1),
        )
        assertTrue(aviso.contains("presupuesto"), aviso)
        assertTrue(aviso.contains("se suman"), aviso)
        assertTrue(aviso.contains("no se puede deshacer"), aviso)
    }

    @Test
    fun `si solo una tiene presupuesto no se habla de sumar nada`() {
        val aviso = avisoDeUnificacion(
            cat("Trasnporte", movements = 2, budgets = 1),
            cat("Transporte", scope = CategoryScope.PREDEFINED),
        )
        assertFalse(aviso.contains("se suman"), aviso)
    }

    @Test
    fun `unificar una categoria vacia lo dice en vez de prometer un cambio`() {
        val aviso = avisoDeUnificacion(
            cat("Otros ingresos", scope = CategoryScope.PREDEFINED),
            cat("Otros", scope = CategoryScope.PREDEFINED),
        )
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
