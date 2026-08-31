package com.jvillada.movi.ui.budgets

import com.jvillada.movi.shared.model.Budget
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Cuándo un presupuesto está sobrepasado, y cuándo solo se quedó sin margen.
 *
 * El dueño, mirando su presupuesto de Mercado en $2.000.000 de $2.000.000: *«marca sobrepasado
 * Mercado pero está al 100%, eso es un error, ¿no?»*. Lo era — y el propio rótulo lo delataba,
 * porque decía «Sobrepasado · $0».
 */
class EstadoDelPresupuestoTest {

    private fun progreso(gastado: Long, limite: Long) =
        BudgetProgress(Budget("Mercado", limite), gastado)

    @Test
    fun gastar_exactamente_el_limite_NO_es_sobrepasarse() {
        // El caso reportado, con sus números reales.
        val p = progreso(2_000_000, 2_000_000)

        assertEquals(AlertState.AL_LIMITE, p.state)
        assertEquals(0L, p.remaining, "y por eso el rótulo viejo decía «Sobrepasado · \$0»")
        assertEquals(100, p.pct)
    }

    @Test
    fun un_peso_mas_si_lo_es() {
        assertEquals(AlertState.OVER, progreso(2_000_001, 2_000_000).state)
    }

    @Test
    fun un_peso_menos_es_solo_aviso() {
        assertEquals(AlertState.WARN, progreso(1_999_999, 2_000_000).state)
    }

    @Test
    fun con_montos_grandes_la_comparacion_sigue_siendo_exacta() {
        // `Float` tiene 24 bits de mantisa: a partir de 16.777.216 deja de representar todos los
        // enteros. La versión vieja comparaba `gastado.toFloat() / limite.toFloat() >= 1f`, y con
        // los montos de este dueño —hipotecas de cientos de millones— eso da exactamente 1.0f en
        // los tres casos de abajo, que son estados DISTINTOS.
        assertEquals(AlertState.AL_LIMITE, progreso(767_800_000, 767_800_000).state)
        assertEquals(AlertState.OVER, progreso(767_800_001, 767_800_000).state)
        assertEquals(AlertState.WARN, progreso(767_799_999, 767_800_000).state)
        // Y justo en el borde donde Float empieza a fallar.
        assertEquals(AlertState.OVER, progreso(16_777_217, 16_777_216).state)
    }

    @Test
    fun el_ochenta_por_ciento_abre_el_aviso() {
        assertEquals(AlertState.OK, progreso(799_999, 1_000_000).state)
        assertEquals(AlertState.WARN, progreso(800_000, 1_000_000).state)
    }

    @Test
    fun un_limite_en_cero_no_esta_sobrepasado() {
        // Es un presupuesto sin configurar, no uno excedido — y dividir por él no tiene sentido.
        assertEquals(AlertState.OK, progreso(50_000, 0).state)
        assertEquals(0, progreso(50_000, 0).pct)
    }

    @Test
    fun sin_gasto_no_hay_nada_que_avisar() {
        assertEquals(AlertState.OK, progreso(0, 2_000_000).state)
        assertEquals(0, progreso(0, 2_000_000).pct)
    }

    @Test
    fun el_porcentaje_tambien_se_calcula_con_enteros() {
        // Mismo motivo que el estado: con montos grandes, un porcentaje sacado de un Float puede
        // estar mal por más de un punto.
        assertEquals(60, progreso(121_210, 200_000).pct)
        assertEquals(160, progreso(321_210, 200_000).pct)
        assertEquals(100, progreso(767_800_000, 767_800_000).pct)
    }
}
