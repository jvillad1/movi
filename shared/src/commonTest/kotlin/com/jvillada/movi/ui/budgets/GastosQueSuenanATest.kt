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

        assertEquals(listOf("ev1"), encontrados.map { it.evento.id })
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

        assertEquals(listOf("ev1"), gastosQueSuenanA("MERCADO", d, ventana).map { it.evento.id })
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

// ── La mención como palabra completa ───────────────────────────────────────────────
//
// El dueño, al ver la versión de igualdad exacta: «o buscar por nombre de gasto o buscar por
// nombre de categoría y las que sean muy cercanas preguntar para asociarlas». «Muy cercanas»
// se acotó a mención-como-palabra a propósito: acá se mueve plata entre categorías, y un falso
// positivo no es ruido sino una cifra equivocada. Ver el KDoc de [gastosQueSuenanA].

class MencionComoPalabraTest {

    private val ventana = 0L..Long.MAX_VALUE

    private fun gasto(id: String, desc: String, cat: String) = FinancialEvent(
        id = id,
        accountId = "acc1",
        type = TransactionType.EXPENSE,
        amount = 100_000,
        description = desc,
        category = cat,
        timestamp = 100L,
        currency = "COP",
    )

    private fun dias(vararg e: FinancialEvent) = listOf(EventDay("2026-08-27", 0L, e.toList()))

    @Test
    fun encuentra_mercado_exito_y_lo_marca_como_mencion() {
        val d = dias(gasto("ev1", "Mercado Éxito", "Comida"))

        val r = gastosQueSuenanA("Mercado", d, ventana)

        assertEquals(listOf("ev1"), r.map { it.evento.id })
        assertEquals(Coincidencia.MENCION, r.single().coincidencia)
    }

    @Test
    fun la_tilde_no_decide_nada() {
        // La pone o no la pone el teclado, no quien escribe.
        val d = dias(gasto("ev1", "cafeteria", "Comida"))

        assertEquals(listOf("ev1"), gastosQueSuenanA("Cafetería", d, ventana).map { it.evento.id })
    }

    @Test
    fun supermercado_NO_es_una_mencion_de_mercado() {
        // El caso que justifica la regla de palabra completa: un `contains` pelado lo habría
        // tomado por bueno y habría movido plata a la categoría equivocada.
        val d = dias(gasto("ev1", "Supermercado", "Comida"))

        assertTrue(gastosQueSuenanA("Mercado", d, ventana).isEmpty())
    }

    @Test
    fun las_exactas_van_primero() {
        // Son las que el dueño reconoce sin pensar; la mención pide una mirada más.
        val d = dias(
            gasto("mencion", "Mercado del mes", "Comida"),
            gasto("exacta", "Mercado", "Comida"),
        )

        assertEquals(listOf("exacta", "mencion"), gastosQueSuenanA("Mercado", d, ventana).map { it.evento.id })
    }

    @Test
    fun una_categoria_de_dos_palabras_se_busca_entera() {
        val d = dias(
            gasto("ev1", "Cuota carro agosto", "Transporte"),
            gasto("ev2", "Cuota moto", "Transporte"),
        )

        assertEquals(listOf("ev1"), gastosQueSuenanA("Cuota carro", d, ventana).map { it.evento.id })
    }
}
