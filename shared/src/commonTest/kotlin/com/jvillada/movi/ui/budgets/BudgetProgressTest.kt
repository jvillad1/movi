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
        assertEquals(161, progreso(321_210, 200_000).pct, "160,6 % pasado el límite sube a 161")
        assertEquals(100, progreso(767_800_000, 767_800_000).pct)
    }

    /**
     * Comida con $1.008.737 de $1.000.000 decía «100% · Sobrepasado · $8.737»: el truncado bajaba
     * el 100,87 % a 100 y el porcentaje contradecía al rótulo. Pasado el límite se redondea hacia
     * arriba; hasta el límite, no.
     */
    @Test
    fun pasado_el_limite_el_porcentaje_nunca_dice_100() {
        assertEquals(101, progreso(1_008_737, 1_000_000).pct)
        assertEquals(101, progreso(1_000_001, 1_000_000).pct, "un peso de más ya es 101 %")
        assertEquals(101, progreso(767_800_001, 767_800_000).pct, "también con montos de cientos de millones")
        assertEquals(117, progreso(1_163_000, 1_000_000).pct, "116,3 % → 117")
        assertEquals(150, progreso(1_500_000, 1_000_000).pct, "exacto no se redondea")
    }

    @Test
    fun justo_en_el_limite_o_debajo_se_sigue_truncando() {
        assertEquals(100, progreso(1_000_000, 1_000_000).pct)
        assertEquals(99, progreso(999_999, 1_000_000).pct, "99,9999 % no se vende como 100")
        assertEquals(79, progreso(799_999, 1_000_000).pct)
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
