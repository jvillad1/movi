package com.jvillada.movi.ui.credits

import androidx.compose.ui.unit.dp
import com.jvillada.movi.ui.Screen
import com.jvillada.movi.ui.components.Disposicion
import com.jvillada.movi.ui.components.anchoDelPanelEnLaCascara
import com.jvillada.movi.ui.components.disposicionDe
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** # Créditos en dos columnas (Ola W3), en números: contra el panel real, ya sin el rail. */
class CreditosEnColumnasTest {

    @Test
    fun `Creditos es un tablero`() {
        assertEquals(Disposicion.Tablero, disposicionDe(Screen.Credits))
        assertEquals(1_064.dp, anchoDelPanelEnLaCascara(1_280.dp, Screen.Credits))
    }

    @Test
    fun `dos columnas en una laptop de 1280 y en 1440 y 1920`() {
        listOf(1_280, 1_440, 1_920).forEach { ventana ->
            assertTrue(creditosEnDosColumnas(anchoDelPanelEnLaCascara(ventana.dp, Screen.Credits)), "$ventana")
        }
    }

    @Test
    fun `una columna a 1024 y en toda ventana mediana`() {
        listOf(600, 768, 840, 999, 1_000, 1_024, 1_100).forEach { ventana ->
            assertFalse(creditosEnDosColumnas(anchoDelPanelEnLaCascara(ventana.dp, Screen.Credits)), "$ventana")
        }
    }

    @Test
    fun `el umbral es de 888 dp de panel`() {
        assertEquals(888.dp, UMBRAL_DE_CREDITOS_EN_DOS_COLUMNAS)
        assertTrue(creditosEnDosColumnas(888.dp))
        assertFalse(creditosEnDosColumnas(887.dp))
    }

    @Test
    fun `sin deudas o sin poder leerlas va en una columna`() {
        assertTrue(hayDeudasParaDosColumnas(cargando = true, sinDeudas = false, noSeLeyo = false), "cargando: el esqueleto")
        assertTrue(hayDeudasParaDosColumnas(cargando = false, sinDeudas = false, noSeLeyo = false))
        assertFalse(hayDeudasParaDosColumnas(cargando = false, sinDeudas = true, noSeLeyo = false), "el vacío que enseña")
        assertFalse(hayDeudasParaDosColumnas(cargando = false, sinDeudas = false, noSeLeyo = true), "no se pudo leer")
    }

    @Test
    fun `un solo grupo se reparte alternando en las dos columnas`() {
        assertEquals(listOf(1, 3, 5) to listOf(2, 4), repartirEnDos(listOf(1, 2, 3, 4, 5)))
        assertEquals(listOf(1) to emptyList<Int>(), repartirEnDos(listOf(1)))
    }
}
