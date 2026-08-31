package com.jvillada.movi.ui.budgets

import com.jvillada.movi.shared.model.Budget
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Las cifras que la tarjeta de un presupuesto muestra: el porcentaje y lo que queda.
 *
 * Existe porque esta cobertura **se cayó** al mudar la regla del estado a `:core`: los tests que
 * probaban `pct` y `remaining` se fueron con el archivo viejo, y nadie los reemplazó. Lo encontró
 * la revisión, y no es teórico — `pct` tiene su propia guarda de división por cero y su propia
 * decisión de usar enteros.
 */
class BudgetProgressTest {

    private fun progreso(gastado: Long, limite: Long) =
        BudgetProgress(Budget("Mercado", limite), gastado)

    @Test
    fun el_porcentaje_se_calcula_con_enteros() {
        // Mismo motivo que el estado: con montos grandes, un porcentaje sacado de un `Float`
        // —24 bits de mantisa— puede estar mal por más de un punto. Los de este dueño son de
        // cientos de millones.
        assertEquals(60, progreso(121_210, 200_000).pct)
        assertEquals(160, progreso(321_210, 200_000).pct)
        assertEquals(100, progreso(767_800_000, 767_800_000).pct)
        assertEquals(100, progreso(767_800_001, 767_800_000).pct, "101 pesos de más no llegan a 101 %")
    }

    @Test
    fun un_limite_en_cero_no_divide_por_cero() {
        // La guarda que evita el crash. Un presupuesto sin configurar existe: se crea la categoría
        // y el monto se pone después.
        assertEquals(0, progreso(50_000, 0).pct)
        assertEquals(0f, progreso(50_000, 0).pctRaw)
    }

    @Test
    fun lo_que_queda_puede_ser_negativo() {
        // Es lo que alimenta «Sobrepasado · $X»: el rótulo lo muestra con el signo cambiado.
        assertEquals(78_790L, progreso(121_210, 200_000).remaining)
        assertEquals(0L, progreso(2_000_000, 2_000_000).remaining, "y por eso el rótulo viejo decía «Sobrepasado · \$0»")
        assertEquals(-121_210L, progreso(321_210, 200_000).remaining)
    }

    @Test
    fun sin_gasto_el_limite_entero_esta_disponible() {
        assertEquals(0, progreso(0, 2_000_000).pct)
        assertEquals(2_000_000L, progreso(0, 2_000_000).remaining)
    }
}
