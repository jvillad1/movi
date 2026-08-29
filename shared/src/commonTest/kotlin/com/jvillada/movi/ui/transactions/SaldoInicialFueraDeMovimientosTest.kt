package com.jvillada.movi.ui.transactions

import com.jvillada.movi.shared.model.EventDay
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.OPENING_CATEGORY
import com.jvillada.movi.shared.model.ReconciliationStatus
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.movementCount
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * **Ola 16 — el saldo inicial sale de la lista de Movimientos y se queda en el detalle de la
 * cuenta.**
 *
 * El caso real, leído de producción el 2026-08-29: el dueño abrió Movimientos y en el grupo de
 * AYER, entre sus gastos del día, vio «Deuda inicial · Saldo inicial · Libre inversión 9695» por
 * **$41.093.905** — la cifra más grande de la pantalla, encabezando su día. Y preguntó: «si no es
 * un desembolso, ¿para qué lo estamos contando como movimiento?».
 *
 * La respuesta medida es que **no se contaba: se mostraba**. Los otros 7 renglones de ese día
 * suman exactamente −$4.558.789, que es el «Flujo del día» que la pantalla ya decía; los $41M no
 * estaban adentro. El fixture [DIA_DE_PRODUCCION] de abajo es ese día, con esos montos, y los
 * tests lo usan para fijar las dos mitades del cambio: **la fila se va** y **ninguna cifra se
 * mueve**.
 */
class SaldoInicialFueraDeMovimientosTest {

    private fun gasto(
        id: String,
        description: String,
        category: String,
        amount: Long,
    ) = FinancialEvent(
        id = id,
        accountId = "acc_ahorros",
        type = TransactionType.EXPENSE,
        amount = amount,
        category = category,
        description = description,
        timestamp = 0L,
        reconciliationStatus = ReconciliationStatus.RECONCILED,
        // Cuenta de activo: el server la deriva en `true` (ver `isCashFlow`). Va explícito
        // porque el default de [FinancialEvent] no distingue, y todo este test se trata de
        // cuál de las dos banderas manda sobre cuál cifra.
        countsAsCashFlow = true,
    )

    /**
     * La deuda inicial del crédito, tal cual está en producción: EXPENSE (sube la deuda) sobre una
     * cuenta LOAN, categoría reservada, y `countsAsCashFlow = false` — que es lo que el server
     * deriva y lo que hace que NO entre en el «Flujo del día».
     */
    private fun deudaInicial() = FinancialEvent(
        id = "ev_opening",
        accountId = "acc_libre_inversion",
        type = TransactionType.EXPENSE,
        amount = 41_093_905,
        category = OPENING_CATEGORY,
        description = "Deuda inicial",
        timestamp = 0L,
        reconciliationStatus = ReconciliationStatus.RECONCILED,
        countsAsCashFlow = false,
    )

    /** El 2026-08-28 de producción: 8 filas, una de ellas la apertura del crédito. */
    private val DIA_DE_PRODUCCION = EventDay(
        date = "2026-08-28",
        total = -4_558_789,
        items = listOf(
            gasto("e1", "Señor Gol", "Fútbol", 46_489),
            gasto("e2", "Las Doce", "Comida", 23_000),
            gasto("e3", "Tienda", "Hija", 50_000),
            gasto("e4", "AV Villas", "Crédito", 100_000),
            deudaInicial(),
            gasto("e5", "Crédito Papá", "Crédito", 4_280_000),
            gasto("e6", "McDonald's", "Comida", 45_800),
            gasto("e7", "Bebida", "Fútbol", 13_500),
        ),
    )

    // ── La fila se va ─────────────────────────────────────────────────────────────

    @Test
    fun `el saldo inicial no se lista cuando no hay busqueda`() {
        assertFalse(showsInMovements(deudaInicial(), ""))
        assertFalse(showsInMovements(deudaInicial(), "   "))
    }

    @Test
    fun `un movimiento de verdad se lista siempre`() {
        val almuerzo = gasto("e2", "Las Doce", "Comida", 23_000)
        assertTrue(showsInMovements(almuerzo, ""))
        assertTrue(showsInMovements(almuerzo, "doce"))
    }

    @Test
    fun `el dia de produccion pasa de 8 renglones a 7`() {
        val antes = DIA_DE_PRODUCCION.items.size
        val despues = diasVisibles(listOf(DIA_DE_PRODUCCION), CHIP_TODO, "").single().items.size
        assertEquals(8, antes)
        assertEquals(7, despues)
    }

    @Test
    fun `y el que se va es exactamente la apertura, no otro`() {
        val visibles = diasVisibles(listOf(DIA_DE_PRODUCCION), CHIP_TODO, "").single().items
        assertTrue(visibles.none { isOpeningBalance(it) })
        // Los $4.280.000 de «Crédito Papá» son un gasto real y grande: el filtro no puede
        // llevárselo de arrastre por ser de categoría «Crédito» ni por el monto.
        assertTrue(visibles.any { it.id == "e5" })
    }

    // ── Y ninguna cifra se mueve ──────────────────────────────────────────────────

    /**
     * **La medición que contesta «¿qué se rompe?».** El «Flujo del día» es el mismo antes y
     * después, porque `countsAsCashFlow` ya dejaba la apertura fuera de la suma: sacarla de la
     * lista no le quita nada a un total que nunca la tuvo.
     */
    @Test
    fun `el flujo del dia no cambia al sacar la apertura`() {
        // El total como se calculaba con la apertura todavía en la lista — el mismo criterio de
        // `EventRoutes /by-day` y de `LocalRepository`, aplicado a las 8 filas.
        val conApertura = DIA_DE_PRODUCCION.items
            .filter { it.countsAsCashFlow }
            .sumOf { if (it.type == TransactionType.EXPENSE) -it.amount else it.amount }
        val sinApertura = diasVisibles(listOf(DIA_DE_PRODUCCION), CHIP_TODO, "").single().total

        assertEquals(-4_558_789, conApertura, "el fixture tiene que ser el día real de producción")
        assertEquals(conApertura, sinApertura)
        // Y es el mismo número que el dueño ya veía en pantalla como «Flujo del día».
        assertEquals(DIA_DE_PRODUCCION.total, sinApertura)
    }

    /**
     * **La contradicción que este cambio cierra.** [movementCount] —la función de `:core` que el
     * server manda como `FinanceSummary.eventCount`, o sea el contador del Inicio y el que apaga
     * la guía de primeros pasos— nunca contó las aperturas. Con los datos de producción decía 7
     * para ese día mientras la lista mostraba 8 renglones. Ahora dicen lo mismo.
     */
    @Test
    fun `la lista y el contador de movimientos dicen el mismo numero`() {
        val visibles = diasVisibles(listOf(DIA_DE_PRODUCCION), CHIP_TODO, "").single().items
        assertEquals(movementCount(DIA_DE_PRODUCCION.items), visibles.size)
        assertEquals(7, visibles.size)
    }

    /**
     * El caso extremo del mismo argumento: un dueño que solo creó cuentas con saldo. El contador
     * ya decía 0 y la guía le seguía diciendo «Registra un movimiento» mientras Movimientos le
     * mostraba renglones. Ahora ve el estado vacío, que es lo que el resto de la app afirmaba.
     */
    @Test
    fun `un dia que solo tiene aperturas desaparece entero, encabezado incluido`() {
        val soloAperturas = EventDay(
            date = "2026-08-28",
            total = 0L,
            items = listOf(deudaInicial()),
        )
        assertTrue(diasVisibles(listOf(soloAperturas), CHIP_TODO, "").isEmpty())
        assertEquals(0, movementCount(soloAperturas.items))
    }

    // ── La búsqueda sí la encuentra ───────────────────────────────────────────────

    /**
     * Buscar es pedirlo. Una app que no encuentra algo que sí existe es peor que una que lo lista
     * de más — y quien escribe «deuda inicial» en la lupa es justo el que se está preguntando de
     * dónde salieron esos $41 millones.
     */
    @Test
    fun `buscando Deuda inicial la fila vuelve a aparecer`() {
        val visibles = diasVisibles(listOf(DIA_DE_PRODUCCION), CHIP_TODO, "deuda inicial").single().items
        assertEquals(1, visibles.size)
        assertTrue(isOpeningBalance(visibles.single()))
    }

    /**
     * La regla se cuelga de «hay consulta», no de las palabras exactas: el dueño no tiene por qué
     * adivinar cómo se llama la fila. Buscar «saldo» —la categoría— llega igual.
     */
    @Test
    fun `tambien la encuentra quien busca por la categoria y no por la descripcion`() {
        val visibles = diasVisibles(listOf(DIA_DE_PRODUCCION), CHIP_TODO, "saldo").single().items
        assertEquals(1, visibles.size)
        assertTrue(isOpeningBalance(visibles.single()))
    }

    /**
     * Pero la búsqueda no la mete donde ya estaba prohibida: los chips «Gastos» e «Ingresos» la
     * siguen dejando fuera (Ola 8 · V6), porque ahí la contradicción era otra —una fila con «+» y
     * en verde bajo un filtro llamado Ingresos, excluida de todos los totales de ingresos— y esa
     * sigue en pie con o sin lupa.
     */
    @Test
    fun `buscarla no la devuelve al chip Gastos`() {
        assertTrue(diasVisibles(listOf(DIA_DE_PRODUCCION), CHIP_GASTOS, "deuda inicial").isEmpty())
    }
}
