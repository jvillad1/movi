package com.jvillada.movi.ui.budgets

import com.jvillada.movi.shared.model.EventDay
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.ui.dashboard.spentByCategoryForPeriod
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Lo que esta suite protege no es el filtro en sí: es que la lista y la barra del presupuesto
 * hablen de la MISMA plata. Por eso el test central compara contra
 * [spentByCategoryForPeriod] —la función que ya calcula el gastado— y no contra un número
 * escrito a mano, que envejecería sin avisar el día que esa función cambie de criterio.
 */
class GastosDelPresupuestoTest {

    private val ventana = 100L..900L

    private fun gasto(
        id: String,
        cat: String,
        monto: Long,
        ts: Long = 500L,
        flujo: Boolean = true,
        tipo: TransactionType = TransactionType.EXPENSE,
        moneda: String = "COP",
    ) = FinancialEvent(
        id = id,
        accountId = "acc1",
        type = tipo,
        amount = monto,
        description = "d-$id",
        category = cat,
        timestamp = ts,
        currency = moneda,
        countsAsCashFlow = flujo,
    )

    private val dias = listOf(
        EventDay(
            "2026-08-27",
            0L,
            listOf(
                gasto("a", "Mercado", 300_000, ts = 200L),
                gasto("b", "Mercado", 700_000, ts = 800L),
                gasto("c", "Comida", 50_000),
                // Fuera de la ventana del período.
                gasto("d", "Mercado", 999_000, ts = 50L),
                // Un pago de tarjeta: NO cuenta como flujo, así que tampoco entra en la barra.
                gasto("e", "Mercado", 400_000, flujo = false),
                // Un ingreso y una compra en dólares: ninguno es gasto del presupuesto.
                gasto("f", "Mercado", 100_000, tipo = TransactionType.INCOME),
                gasto("g", "Mercado", 20L, moneda = "USD"),
            ),
        ),
    )

    @Test
    fun la_suma_de_la_lista_es_exactamente_el_gastado_de_la_barra() {
        val lista = gastosDelPresupuesto("Mercado", dias, ventana)
        val gastado = spentByCategoryForPeriod(dias, ventana)["Mercado"]

        assertEquals(gastado, lista.sumOf { it.amount })
        assertEquals(1_000_000L, lista.sumOf { it.amount })
    }

    @Test
    fun deja_afuera_lo_que_la_barra_deja_afuera() {
        val ids = gastosDelPresupuesto("Mercado", dias, ventana).map { it.id }

        assertEquals(listOf("b", "a"), ids)  // y en ese orden: lo más reciente primero
    }

    @Test
    fun una_categoria_sin_gastos_devuelve_lista_vacia_no_los_de_otra() {
        assertTrue(gastosDelPresupuesto("Ropa", dias, ventana).isEmpty())
    }

    @Test
    fun categoria_vacia_no_devuelve_todo() {
        // Un `filter` mal escrito acá vaciaría el nombre y traería la lista entera.
        assertTrue(gastosDelPresupuesto("   ", dias, ventana).isEmpty())
    }
}
