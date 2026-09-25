package com.jvillada.movi.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.jvillada.movi.theme.MoviTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals

/**
 * Ola D, Task 1: el componente en sí, sin ninguna pantalla alrededor — Movimientos y el hero de
 * Hoy tienen sus propias pruebas de integración (`EsqueletoDeMovimientosTest`,
 * `EsqueletosDelInicioTest`); acá solo se prueba el contrato del componente.
 */
@RunWith(RobolectricTestRunner::class)
class VacioQueEnsenaTest {

    @get:Rule val composeRule = createComposeRule()

    @Test
    fun `muestra titulo, detalle y el boton, y tocarlo dispara onAccion`() {
        var tocado = 0
        composeRule.setContent {
            MoviTheme {
                Box(Modifier.fillMaxSize()) {
                    VacioQueEnsena(
                        titulo = "Un título",
                        detalle = "Un detalle que explica qué va a aparecer acá.",
                        accion = "Hacer algo",
                        onAccion = { tocado++ },
                    )
                }
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(TAG_VACIO_QUE_ENSENA).assertIsDisplayed()
        composeRule.onNodeWithText("Un título").assertIsDisplayed()
        composeRule.onNodeWithText("Un detalle que explica qué va a aparecer acá.").assertIsDisplayed()
        composeRule.onNodeWithText("Hacer algo").performClick()
        assertEquals(1, tocado)
    }

    /** [com.jvillada.movi.ui.transactions.VacioDeMovimientos] («Sin movimientos aún») no trae
     * detalle: no se inventa uno acá. */
    @Test
    fun `sin detalle, no dibuja ningun texto de mas`() {
        composeRule.setContent {
            MoviTheme {
                Box(Modifier.fillMaxSize()) {
                    VacioQueEnsena(titulo = "Sin movimientos aún", detalle = null)
                }
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Sin movimientos aún").assertIsDisplayed()
    }

    /** Un vacío sin acción (un chip que filtró todo) no ofrece ningún botón para tocar. */
    @Test
    fun `sin accion, no hay nada clickeable`() {
        composeRule.setContent {
            MoviTheme {
                Box(Modifier.fillMaxSize()) {
                    VacioQueEnsena(titulo = "Sin gastos", detalle = "Hay movimientos, pero ninguno es un gasto.")
                }
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(TAG_VACIO_QUE_ENSENA).assertIsDisplayed()
        assertEquals(0, composeRule.onAllNodes(hasClickAction()).fetchSemanticsNodes().size)
    }
}
