package com.jvillada.movi.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.withKeyDown
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.performClick
import com.jvillada.movi.theme.MoviTheme
import kotlin.test.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * **El atajo de seleccionar todo, ejercido sobre el campo de verdad.**
 *
 * `SeleccionarTodoTest` prueba las dos funciones puras; esto prueba que estén **cableadas**: que el
 * `onPreviewKeyEvent` del campo reciba Ctrl/⌘+A y deje la selección entera, para que lo que se
 * escriba encima reemplace en vez de concatenarse.
 *
 * **Lo que NO prueba, y es la mitad que importa:** que Compose-**wasm** entregue ese evento. Ahí
 * está el defecto original (Compose le hace `preventDefault` a ⌘A y no implementa el suyo), y ni
 * Robolectric ni ningún test de este repo pueden observarlo — se midió con una sonda en la página
 * y un teclado físico. Esto cubre lo que es nuestro; lo otro se confirma a mano en el navegador.
 *
 * **Corre en SDK 34 y no en el 24 de siempre, a propósito.** Estas pruebas tocan el campo y miran
 * dónde cae el cursor, o sea que dependen de la posición horizontal de cada glifo. Robolectric en
 * SDK 24 con gráficos legacy mide en cero el ancho de todo texto con spans, y un `lineHeight` (el
 * de `Movi.textos.monto`) hace que Compose le ponga un `LineHeightStyleSpan`: el toque caía siempre
 * en el offset 0 y lo tipeado iba adelante. En SDK 34 —legacy o nativo, con o sin interlineado— el
 * cursor queda al final, igual que en un teléfono y en la web (medido 2026-09-21). Antes de esto el
 * campo le sacaba el interlineado a su estilo para complacer a esta prueba.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h731dp-xhdpi", sdk = [34])
class MoneyFieldAtajoTest {

    @get:Rule val composeRule = createComposeRule()

    private var ultimoValor: Long? = null

    private fun montar(inicial: Long?) {
        ultimoValor = inicial
        composeRule.setContent {
            var monto by remember { mutableStateOf(inicial) }
            MoviTheme {
                Box(Modifier.fillMaxSize()) {
                    MoneyField(value = monto, onValueChange = { monto = it; ultimoValor = it })
                }
            }
        }
        composeRule.waitForIdle()
    }

    /**
     * **El defecto que abrió esto.** Sobre $18.000.009, ⌘A + «7000» dejaba $180.000.097.000 en la
     * web. Con el atajo manejado por la app, reemplaza.
     */
    @Test
    fun seleccionar_todo_y_reescribir_reemplaza_el_monto() {
        montar(18_000_009L)

        val campo = composeRule.onNode(hasSetTextAction())
        campo.performClick()
        campo.performKeyInput { withKeyDown(Key.CtrlLeft) { pressKey(Key.A) } }
        composeRule.waitForIdle()
        campo.performTextInput("7000")
        composeRule.waitForIdle()

        assertEquals(7_000L, ultimoValor)
    }

    /** Sin el atajo, escribir encima concatena — o sea que la prueba de arriba mide algo. */
    @Test
    fun sin_el_atajo_escribir_al_final_concatena() {
        montar(18_000_009L)

        val campo = composeRule.onNode(hasSetTextAction())
        campo.performClick()
        campo.performTextInput("7000")
        composeRule.waitForIdle()

        assertEquals(180_000_097_000L, ultimoValor)
    }
}
