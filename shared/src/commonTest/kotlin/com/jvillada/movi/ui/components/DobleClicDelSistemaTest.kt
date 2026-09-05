package com.jvillada.movi.ui.components

import androidx.compose.ui.platform.ViewConfiguration
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Lo que se puede probar sin un navegador: **que solo se cambie el tiempo del doble clic**.
 *
 * Lo que NO cubre, y hay que decirlo: que Compose lea de acá su umbral de multi-clic. Eso se midió
 * en el navegador con un barrido de pausas —ver [ViewConfigurationConDobleClicDelSistema], que
 * trae la tabla— y se vuelve a medir igual si alguien duda: el borde tiene que moverse de 300 ms a
 * 500.
 */
class DobleClicDelSistemaTest {

    /** Todo lo que NO es el doble clic, con valores reconocibles para poder ver si se filtran. */
    private val original = object : ViewConfiguration {
        override val longPressTimeoutMillis: Long = 111L
        override val doubleTapTimeoutMillis: Long = 300L
        override val doubleTapMinTimeMillis: Long = 222L
        override val touchSlop: Float = 33.5f
        override val minimumTouchTargetSize: DpSize = DpSize(44.dp, 44.dp)
    }

    @Test
    fun el_doble_clic_pasa_a_ser_el_del_sistema() {
        val envuelto = ViewConfigurationConDobleClicDelSistema(original)
        assertEquals(500L, envuelto.doubleTapTimeoutMillis)
        assertEquals(500L, DOBLE_CLIC_DEL_SISTEMA_MS)
    }

    @Test
    fun no_se_toca_nada_mas() {
        // Un `by` que se coma el resto de la configuración cambiaría el long-press y el umbral de
        // arrastre de toda la app de un plumazo, y eso no se vería en ninguna pantalla hasta que
        // alguien no pudiera arrastrar algo.
        val envuelto = ViewConfigurationConDobleClicDelSistema(original)
        assertEquals(original.longPressTimeoutMillis, envuelto.longPressTimeoutMillis)
        assertEquals(original.doubleTapMinTimeMillis, envuelto.doubleTapMinTimeMillis)
        assertEquals(original.touchSlop, envuelto.touchSlop)
        assertEquals(original.minimumTouchTargetSize, envuelto.minimumTouchTargetSize)
    }

    @Test
    fun el_umbral_viejo_de_compose_queda_por_debajo_del_gesto_humano() {
        // El defecto en una línea: el gesto que el sistema operativo llama doble clic (hasta 500 ms
        // entre clics) no entraba en la ventana de Compose (300).
        assertEquals(300L, original.doubleTapTimeoutMillis)
        val envuelto = ViewConfigurationConDobleClicDelSistema(original)
        val gestoDeUnaPersonaSinApuro = 400L
        assertEquals(true, gestoDeUnaPersonaSinApuro > original.doubleTapTimeoutMillis)
        assertEquals(true, gestoDeUnaPersonaSinApuro <= envuelto.doubleTapTimeoutMillis)
    }
}
