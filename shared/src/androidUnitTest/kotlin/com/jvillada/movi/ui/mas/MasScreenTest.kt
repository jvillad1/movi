package com.jvillada.movi.ui.mas

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.jvillada.movi.theme.MoviTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * PR 2 del rediseño de Recurrentes (2026-09): «Recurrentes» dejó de tener entrada en Más — la
 * misma razón que le sacó el destino propio al rail, ver `MinNavRailTest`. Se monta la pantalla
 * completa (y no solo la lista privada `items`, que no se puede leer desde afuera) para probar
 * lo que de verdad importa: qué ficha ve el dueño.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h731dp-xhdpi")
class MasScreenTest {

    @get:Rule val composeRule = createComposeRule()

    @Test
    fun `Mas ya no ofrece Recurrentes, y el resto de las fichas sigue ahi`() {
        composeRule.setContent {
            MoviTheme { MasScreen(onNavigate = {}) }
        }

        composeRule.onNodeWithText("Recurrentes", useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNodeWithText("Cuentas", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("Presupuestos", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("Créditos", useUnmergedTree = true).assertIsDisplayed()
    }
}
