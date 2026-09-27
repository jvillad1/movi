package com.jvillada.movi.ui.accounts

import androidx.compose.ui.unit.dp
import com.jvillada.movi.ui.Screen
import com.jvillada.movi.ui.components.Disposicion
import com.jvillada.movi.ui.components.anchoDelPanelEnLaCascara
import com.jvillada.movi.ui.components.disposicionDe
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * # Patrimonio en dos columnas (Ola W3), en números
 *
 * El umbral se prueba contra el ancho REAL del panel dentro de la cáscara
 * ([anchoDelPanelEnLaCascara]: ventana − rail, topado a 1.280), no contra el de la ventana: la Ola
 * W2 se equivocó justo ahí.
 */
class PatrimonioEnColumnasTest {

    @Test
    fun `Patrimonio es un tablero`() {
        assertEquals(Disposicion.Tablero, disposicionDe(Screen.Accounts))
    }

    @Test
    fun `el panel de Patrimonio en cada ventana, ya sin el rail`() {
        assertEquals(808.dp, anchoDelPanelEnLaCascara(1_024.dp, Screen.Accounts))
        assertEquals(1_064.dp, anchoDelPanelEnLaCascara(1_280.dp, Screen.Accounts))
        assertEquals(1_224.dp, anchoDelPanelEnLaCascara(1_440.dp, Screen.Accounts))
        assertEquals(1_280.dp, anchoDelPanelEnLaCascara(1_920.dp, Screen.Accounts))
        // Mediano: la ventana menos el rail compacto de 80, topado a 840.
        assertEquals(688.dp, anchoDelPanelEnLaCascara(768.dp, Screen.Accounts))
        assertEquals(840.dp, anchoDelPanelEnLaCascara(999.dp, Screen.Accounts))
    }

    @Test
    fun `dos columnas en una laptop de 1280 y en 1440 y 1920`() {
        listOf(1_280, 1_440, 1_920).forEach { ventana ->
            assertTrue(patrimonioEnDosColumnas(anchoDelPanelEnLaCascara(ventana.dp, Screen.Accounts)), "$ventana")
        }
    }

    @Test
    fun `una columna a 1024 y en toda ventana mediana`() {
        listOf(600, 768, 840, 999, 1_000, 1_024, 1_100).forEach { ventana ->
            assertFalse(patrimonioEnDosColumnas(anchoDelPanelEnLaCascara(ventana.dp, Screen.Accounts)), "$ventana")
        }
    }

    @Test
    fun `el umbral es de 888 dp de panel`() {
        assertEquals(888.dp, UMBRAL_DE_PATRIMONIO_EN_DOS_COLUMNAS)
        assertTrue(patrimonioEnDosColumnas(888.dp))
        assertFalse(patrimonioEnDosColumnas(887.dp))
    }
}
