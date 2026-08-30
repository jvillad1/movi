package com.jvillada.movi.ui.budgets

import com.jvillada.movi.shared.model.EventDay
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.OPENING_CATEGORY
import com.jvillada.movi.shared.model.TransactionType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GastosQueSuenanATest {

    private val ventana = 1_000L..2_000L

    private fun gasto(
        id: String,
        descripcion: String,
        categoria: String,
        ts: Long = 1_500L,
        moneda: String = "COP",
        tipo: TransactionType = TransactionType.EXPENSE,
    ) = FinancialEvent(
        id = id,
        accountId = "acc",
        type = tipo,
        amount = 2_000_000L,
        category = categoria,
        description = descripcion,
        timestamp = ts,
        currency = moneda,
    )

    private fun dias(vararg e: FinancialEvent) = listOf(EventDay("2026-08-27", 0L, e.toList()))

    /** El caso real del dueño: el gasto se llama «Mercado» y está archivado en «Comida». */
    @Test
    fun encuentra_el_gasto_que_se_llama_como_la_categoria() {
        val d = dias(gasto("ev1", "Mercado", "Comida"), gasto("ev2", "Tostao", "Comida"))

        val encontrados = gastosQueSuenanA("Mercado", d, ventana)

        assertEquals(listOf("ev1"), encontrados.map { it.id })
    }

    /** Lo que ya está en la categoría no se propone: no hay nada que mover. */
    @Test
    fun ignora_lo_que_ya_esta_en_esa_categoria() {
        val d = dias(gasto("ev1", "Mercado", "Mercado"))

        assertTrue(gastosQueSuenanA("Mercado", d, ventana).isEmpty())
    }

    /**
     * Mover un «Saldo inicial» a una categoría normal lo convertiría en gasto del mes. Movi
     * gobierna esas filas; no se ofrecen.
     */
    @Test
    fun no_ofrece_mover_una_categoria_reservada() {
        val d = dias(gasto("ev1", "Mercado", OPENING_CATEGORY))

        assertTrue(gastosQueSuenanA("Mercado", d, ventana).isEmpty())
    }

    /** Fuera del período no cuenta: el presupuesto vigila una ventana, no toda la historia. */
    @Test
    fun ignora_lo_de_otro_periodo() {
        val d = dias(gasto("ev1", "Mercado", "Comida", ts = 9_999L))

        assertTrue(gastosQueSuenanA("Mercado", d, ventana).isEmpty())
    }

    /** Un presupuesto es de gastos: un ingreso que se llame igual no se mueve. */
    @Test
    fun ignora_los_ingresos() {
        val d = dias(gasto("ev1", "Mercado", "Comida", tipo = TransactionType.INCOME))

        assertTrue(gastosQueSuenanA("Mercado", d, ventana).isEmpty())
    }

    /** Los presupuestos son en pesos: un gasto en dólares no entra a la cuenta. */
    @Test
    fun ignora_otras_monedas() {
        val d = dias(gasto("ev1", "Mercado", "Comida", moneda = "USD"))

        assertTrue(gastosQueSuenanA("Mercado", d, ventana).isEmpty())
    }

    /** Mayúsculas y espacios no pueden esconder la coincidencia. */
    @Test
    fun no_distingue_mayusculas_ni_espacios() {
        val d = dias(gasto("ev1", "  mercado ", "Comida"))

        assertEquals(listOf("ev1"), gastosQueSuenanA("MERCADO", d, ventana).map { it.id })
    }

    /**
     * **No se propone por monto**, aunque en el caso del dueño coincidiera exactamente con el
     * límite. Un gasto que casualmente vale lo mismo no tiene nada que ver con el presupuesto:
     * acertaría una vez y sería ruido siempre.
     */
    @Test
    fun no_propone_por_coincidencia_de_monto() {
        val d = dias(gasto("ev1", "Cualquier cosa", "Comida"))

        assertTrue(gastosQueSuenanA("Mercado", d, ventana).isEmpty())
    }
}
