package com.jvillada.movi.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.pressKey
import com.jvillada.movi.ConClaseDeAncho
import com.jvillada.movi.EsqueletoDeLaCascara
import com.jvillada.movi.ui.Screen
import com.jvillada.movi.ui.components.ds.Campo
import com.jvillada.movi.ui.components.ds.TAG_CAMPO
import com.jvillada.movi.ui.quickadd.QuickAddScreen
import com.jvillada.movi.ui.quickadd.TAG_CAMPO_DE_NOTA
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * # Escape en la hoja desde abajo (vuelta de diseño 17)
 *
 * En la web a 390 px, Patrimonio → «Nueva cuenta» no se cerraba con Escape: la hoja del teléfono no
 * lo atendía y solo la X la cerraba. Ahora lo atiende como la centrada, en el *preview*: un campo
 * enfocado no se lo traga. Y en «Agregar» el primer Escape sigue volviendo del sub-editor al
 * formulario, no cerrando la hoja.
 *
 * Todo en la cáscara real, a 390 dp: la hoja se dibuja en el [AnfitrionDeHojas], como en la app.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w390dp-h844dp-xhdpi")
class EscapeEnLaHojaAngostaTest {

    @get:Rule val composeRule = createComposeRule()

    private var cerrada = false

    private fun enLaCascara(tecladoFisico: Boolean = false, contenido: @androidx.compose.runtime.Composable () -> Unit) {
        composeRule.setContent {
            CompositionLocalProvider(LocalHayTecladoFisico provides tecladoFisico) {
                ConClaseDeAncho {
                    EsqueletoDeLaCascara(
                        pantalla = Screen.Accounts,
                        activeTab = NavTab.PATRIMONIO,
                        conNavegacion = true,
                        onTabSelected = {},
                        relevoDeScroll = remember { RelevoDeScroll() },
                    ) {
                        Box(Modifier.fillMaxSize())
                        contenido()
                    }
                }
            }
        }
        composeRule.waitForIdle()
    }

    @Test
    fun `con un Campo enfocado Escape cierra la hoja`() {
        enLaCascara {
            var abierta by remember { mutableStateOf(true) }
            if (abierta) {
                MarcoDeHoja(onDismiss = { cerrada = true; abierta = false }) {
                    var nombre by remember { mutableStateOf("Bancolombia Ahorros") }
                    Campo(nombre, { nombre = it }, rotulo = "Nombre")
                }
            }
        }
        composeRule.onNodeWithTag(TAG_CAMPO).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(TAG_CAMPO).assertIsFocused()

        composeRule.onNodeWithTag(TAG_CAMPO).performKeyInput { pressKey(Key.Escape) }
        composeRule.waitForIdle()

        assertTrue(cerrada, "Escape con el cursor en «Nombre» tenía que cerrar la hoja")
        composeRule.onNodeWithTag(TAG_PANEL_DE_HOJA, useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun `Escape respeta que la hoja no se pueda cerrar mientras guarda`() {
        enLaCascara {
            MarcoDeHoja(onDismiss = { cerrada = true }, dismissEnabled = false) {
                var nombre by remember { mutableStateOf("Nu") }
                Campo(nombre, { nombre = it }, rotulo = "Nombre")
            }
        }
        composeRule.onNodeWithTag(TAG_CAMPO).performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(TAG_CAMPO).performKeyInput { pressKey(Key.Escape) }
        composeRule.waitForIdle()

        assertFalse(cerrada)
        composeRule.onNodeWithTag(TAG_PANEL_DE_HOJA, useUnmergedTree = true).assertIsDisplayed()
    }

    /**
     * Con teclado físico (la web), Escape le llega a la hoja aunque nadie haya tocado un campo: el
     * velo toma el foco al abrir.
     */
    @Test
    fun `con teclado fisico Escape cierra la hoja sin tocar ningun campo`() {
        enLaCascara(tecladoFisico = true) {
            var abierta by remember { mutableStateOf(true) }
            if (abierta) {
                MarcoDeHoja(onDismiss = { cerrada = true; abierta = false }) { Text("Nueva cuenta") }
            }
        }

        composeRule.onRoot().performKeyInput { pressKey(Key.Escape) }
        composeRule.waitForIdle()

        assertTrue(cerrada)
    }

    /** El sub-editor de «Agregar» (vuelta 13): el primer Escape vuelve al formulario. */
    @Test
    fun `en Agregar Escape en la Nota vuelve al formulario y el segundo cierra la hoja`() {
        enLaCascara(tecladoFisico = true) {
            if (!cerrada) QuickAddScreen(onDismiss = { cerrada = true })
        }
        composeRule.onNodeWithText("Agregar nota…").performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(TAG_CAMPO_DE_NOTA).assertIsFocused()

        composeRule.onNodeWithTag(TAG_CAMPO_DE_NOTA).performKeyInput { pressKey(Key.Escape) }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(TAG_CAMPO_DE_NOTA).assertDoesNotExist()
        composeRule.onNodeWithTag(TAG_PANEL_DE_HOJA).assertIsDisplayed()
        assertFalse(cerrada, "Escape en la Nota no puede tirar el movimiento entero")

        composeRule.onRoot().performKeyInput { pressKey(Key.Escape) }
        composeRule.waitForIdle()

        assertTrue(cerrada, "sin sub-editor abierto, Escape cierra la hoja")
    }
}
