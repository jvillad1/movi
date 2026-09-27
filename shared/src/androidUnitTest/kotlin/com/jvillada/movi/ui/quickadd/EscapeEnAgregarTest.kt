package com.jvillada.movi.ui.quickadd

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.pressKey
import com.jvillada.movi.ConClaseDeAncho
import com.jvillada.movi.EsqueletoDeLaCascara
import com.jvillada.movi.ui.Screen
import com.jvillada.movi.ui.components.NavTab
import com.jvillada.movi.ui.components.RelevoDeScroll
import com.jvillada.movi.ui.components.TAG_PANEL_DE_HOJA
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * # Escape en «Agregar», en la web (fix de la Ola W1)
 *
 * En escritorio Escape es «salir de esto». Con la Nota abierta y el cursor en su campo, «esto» es la
 * Nota: cerrar la hoja entera —lo que hacía la primera versión de W1— tiraba el monto, la categoría
 * y todo lo anotado. Se monta la hoja real en la cáscara real a 1.280 dp.
 *
 * `QuickAddScreen` pide las cuentas al abrir; acá la llamada falla y la hoja lo absorbe (ver
 * `HojaAgregarGeometriaTest`). No importa: lo que se prueba es a quién le llega Escape.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w1280dp-h900dp-mdpi")
class EscapeEnAgregarTest {

    @get:Rule val composeRule = createComposeRule()

    private var cerrada = false

    private fun montar() {
        composeRule.setContent {
            ConClaseDeAncho {
                EsqueletoDeLaCascara(
                    pantalla = Screen.Dashboard,
                    activeTab = NavTab.HOY,
                    conNavegacion = true,
                    onTabSelected = {},
                    relevoDeScroll = remember { RelevoDeScroll() },
                ) {
                    Box(Modifier.fillMaxSize())
                    if (!cerrada) QuickAddScreen(onDismiss = { cerrada = true })
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun tocar(texto: String) {
        composeRule.onNodeWithText(texto).performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
    }

    @Test
    fun `Escape con el foco en la Nota cierra la Nota y no la hoja, y el segundo cierra la hoja`() {
        montar()
        tocar("Agregar nota…")
        composeRule.onNodeWithTag(TAG_CAMPO_DE_NOTA).assertIsFocused()

        composeRule.onNodeWithTag(TAG_CAMPO_DE_NOTA).performKeyInput { pressKey(Key.Escape) }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(TAG_CAMPO_DE_NOTA).assertDoesNotExist()
        composeRule.onNodeWithTag(TAG_PANEL_DE_HOJA).assertIsDisplayed()
        composeRule.onNodeWithText("9").assertIsDisplayed()
        assertFalse(cerrada, "Escape en la Nota no puede tirar el movimiento entero")

        composeRule.onRoot().performKeyInput { pressKey(Key.Escape) }
        composeRule.waitForIdle()

        assertTrue(cerrada, "sin sub-editor abierto, Escape cierra la hoja")
    }

    @Test
    fun `Escape con el sub-picker de Categoria abierto lo cierra y deja la hoja`() {
        montar()
        tocar("Categoría")
        composeRule.onNodeWithText("9").assertDoesNotExist()

        composeRule.onRoot().performKeyInput { pressKey(Key.Escape) }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("9").assertIsDisplayed()
        assertFalse(cerrada)
    }

    @Test
    fun `Escape sin sub-editor cierra la hoja`() {
        montar()

        composeRule.onRoot().performKeyInput { pressKey(Key.Escape) }
        composeRule.waitForIdle()

        assertTrue(cerrada)
    }
}
