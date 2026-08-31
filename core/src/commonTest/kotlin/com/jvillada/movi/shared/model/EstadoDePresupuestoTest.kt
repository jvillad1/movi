package com.jvillada.movi.shared.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * El semáforo de un presupuesto, y la regla que **dos pantallas comparten**.
 *
 * Vive en `:core` porque ya se separó una vez: el Inicio tenía su propia comparación con un
 * comentario que decía «misma regla que Presupuestos», y cuando esa pantalla cambió, el dueño
 * empezó a ver «Presupuesto de Mercado superado» en una y «Sin margen» en la otra.
 */
class EstadoDePresupuestoTest {

    @Test
    fun gastar_exactamente_el_limite_NO_es_sobrepasarse() {
        // El caso que reportó el dueño, con sus números reales: $2.000.000 de $2.000.000. El
        // rótulo viejo decía «Sobrepasado · $0», que se delataba solo.
        assertEquals(EstadoDePresupuesto.AL_LIMITE, estadoDePresupuesto(2_000_000, 2_000_000))
        assertFalse(estadoDePresupuesto(2_000_000, 2_000_000).estaSuperado)
    }

    @Test
    fun un_peso_mas_ya_es_pasarse_pero_poco() {
        assertEquals(EstadoDePresupuesto.EXCEDIDO_POCO, estadoDePresupuesto(2_000_001, 2_000_000))
        assertTrue(estadoDePresupuesto(2_000_001, 2_000_000).estaSuperado)
    }

    @Test
    fun pasarse_mas_del_veinte_por_ciento_es_pasarse_mucho() {
        // «Amarillo si superé un poco y rojo si superé mucho». En un presupuesto de $2.000.000 el
        // corte está en $2.400.000: una compra grande cabe en amarillo, dos ya no.
        assertEquals(EstadoDePresupuesto.EXCEDIDO_POCO, estadoDePresupuesto(2_400_000, 2_000_000))
        assertEquals(EstadoDePresupuesto.EXCEDIDO_MUCHO, estadoDePresupuesto(2_400_001, 2_000_000))
    }

    @Test
    fun el_ochenta_por_ciento_avisa_sin_ser_una_falla() {
        assertEquals(EstadoDePresupuesto.DENTRO, estadoDePresupuesto(799_999, 1_000_000))
        assertEquals(EstadoDePresupuesto.CERCA, estadoDePresupuesto(800_000, 1_000_000))
        // «Cerca» sigue siendo verde para el dueño: está por debajo del límite.
        assertFalse(estadoDePresupuesto(800_000, 1_000_000).estaSuperado)
    }

    @Test
    fun con_montos_grandes_la_comparacion_sigue_siendo_exacta() {
        // `Float` tiene 24 bits de mantisa: a partir de 16.777.216 deja de representar todos los
        // enteros. La versión vieja comparaba `gastado.toFloat() / limite.toFloat() >= 1f`, y con
        // los montos de este dueño esos tres casos daban exactamente 1.0f siendo tres estados
        // distintos.
        assertEquals(EstadoDePresupuesto.AL_LIMITE, estadoDePresupuesto(767_800_000, 767_800_000))
        assertEquals(EstadoDePresupuesto.EXCEDIDO_POCO, estadoDePresupuesto(767_800_001, 767_800_000))
        assertEquals(EstadoDePresupuesto.CERCA, estadoDePresupuesto(767_799_999, 767_800_000))
        assertEquals(EstadoDePresupuesto.EXCEDIDO_POCO, estadoDePresupuesto(16_777_217, 16_777_216))
    }

    @Test
    fun un_limite_en_cero_no_esta_superado() {
        // Es un presupuesto sin configurar, no uno excedido — y dividir por él no tiene sentido.
        assertEquals(EstadoDePresupuesto.DENTRO, estadoDePresupuesto(50_000, 0))
        assertFalse(estadoDePresupuesto(50_000, 0).estaSuperado)
    }

    @Test
    fun sin_gasto_no_hay_nada_que_avisar() {
        assertEquals(EstadoDePresupuesto.DENTRO, estadoDePresupuesto(0, 2_000_000))
    }

    @Test
    fun solo_los_excedidos_cuentan_como_superados() {
        // Es lo que decide la alerta del Inicio. Si `AL_LIMITE` o `CERCA` entraran acá, volvería
        // la contradicción entre las dos pantallas.
        val superados = EstadoDePresupuesto.entries.filter { it.estaSuperado }
        assertEquals(
            listOf(EstadoDePresupuesto.EXCEDIDO_POCO, EstadoDePresupuesto.EXCEDIDO_MUCHO),
            superados,
        )
    }
}
