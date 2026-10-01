package com.jvillada.movi.ui.quickadd

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import com.jvillada.movi.theme.MoviTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

/**
 * # Agregar no ofrece un escáner de recibos que no existe
 *
 * El botón de cámara abría una pantalla con un recibo INVENTADO («ÉXITO COUNTRY», $312.400) cuyo
 * «Guardar movimiento» solo volvía al Inicio: la única pantalla de Movi con datos falsos
 * (revisión del 29-sep). Se quitó con su ruta; el escáner real llega con «Compartir con Movi».
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h731dp-xhdpi")
class SinEscanerInventadoTest {

    @get:Rule val composeRule = createComposeRule()

    @Test
    fun la_hoja_de_agregar_no_tiene_boton_de_camara() {
        composeRule.setContent {
            MoviTheme { Box(Modifier.fillMaxSize()) { QuickAddScreen(onDismiss = {}) } }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Guardar movimiento").assertIsDisplayed()
        assertEquals(
            0,
            composeRule.onAllNodesWithContentDescription("Escanear recibo", useUnmergedTree = true)
                .fetchSemanticsNodes().size,
        )
    }
}
