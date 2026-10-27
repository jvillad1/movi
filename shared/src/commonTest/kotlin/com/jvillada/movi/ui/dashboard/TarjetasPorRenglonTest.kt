package com.jvillada.movi.ui.dashboard

import androidx.compose.ui.unit.dp
import com.jvillada.movi.ui.Screen
import com.jvillada.movi.ui.components.ANCHO_DE_LECTURA
import com.jvillada.movi.ui.components.WindowWidthClass
import com.jvillada.movi.ui.components.anchoDelPanelEnLaCascara
import com.jvillada.movi.ui.components.claseDeAncho
import com.jvillada.movi.ui.components.ANCHO_PARA_DOS_COLUMNAS
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * # ¿Carrusel o grilla? (vuelta de diseño 9)
 *
 * «Tus cuentas» va en carrusel en el teléfono y en grilla fuera de él, con los umbrales de siempre:
 * la clase de ancho y la columna de lectura. Ningún breakpoint nuevo.
 */
class TarjetasPorRenglonTest {

    @Test
    fun `en el telefono siempre el carrusel`() {
        assertEquals(0, tarjetasPorRenglon(WindowWidthClass.Compact, 390.dp, 5))
        assertEquals(0, tarjetasPorRenglon(WindowWidthClass.Compact, 599.dp, 5))
    }

    @Test
    fun `fuera del telefono dos por renglon, y tres en la columna de lectura`() {
        assertEquals(2, tarjetasPorRenglon(WindowWidthClass.Medium, 688.dp, 5))
        assertEquals(2, tarjetasPorRenglon(WindowWidthClass.Expanded, 532.dp, 5))
        assertEquals(3, tarjetasPorRenglon(WindowWidthClass.Medium, ANCHO_DE_LECTURA, 5))
        assertEquals(3, tarjetasPorRenglon(WindowWidthClass.Expanded, ANCHO_DE_LECTURA, 5))
    }

    @Test
    fun `una sola cuenta va a todo el ancho y con pocas no quedan huecos`() {
        assertEquals(0, tarjetasPorRenglon(WindowWidthClass.Expanded, 532.dp, 1))
        assertEquals(2, tarjetasPorRenglon(WindowWidthClass.Expanded, ANCHO_DE_LECTURA, 2))
    }

    /** La tabla del KDoc, desde la ventana: el rail, el tope del tablero y las columnas del Inicio. */
    @Test
    fun `la tabla de anchos reales`() {
        fun porRenglon(ventana: Int): Int {
            val panel = anchoDelPanelEnLaCascara(ventana.dp, Screen.Dashboard)
            val columna = if (panel >= ANCHO_PARA_DOS_COLUMNAS) panel / 2 else minOf(panel, ANCHO_DE_LECTURA)
            return tarjetasPorRenglon(claseDeAncho(ventana.dp), columna, 6)
        }
        assertEquals(0, porRenglon(390))
        assertEquals(2, porRenglon(768))
        assertEquals(3, porRenglon(999))
        assertEquals(3, porRenglon(1_024))
        assertEquals(2, porRenglon(1_280))
        assertEquals(2, porRenglon(1_920))
    }
}
