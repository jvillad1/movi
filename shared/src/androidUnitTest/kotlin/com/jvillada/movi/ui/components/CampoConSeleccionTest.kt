package com.jvillada.movi.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.withKeyDown
import com.jvillada.movi.theme.MoviTheme
import kotlin.test.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * **El atajo de seleccionar todo en un campo de texto común**, que es donde estaba el resto del
 * defecto: el PR anterior lo arregló solo en `MoneyField`, y el mismo ⌘A seguía sin seleccionar
 * nada en NOMBRE, en la categoría, en la búsqueda y en las once hojas restantes.
 *
 * Esos campos guardan un `String`, así que ni siquiera podían responder al atajo —
 * `BasicTextField(value: String, …)` no expone la selección. [CampoConSeleccion] es lo que se
 * interpone; acá se ejerce **cableado a un campo de verdad**, no como funciones sueltas.
 *
 * **Lo que NO prueba, igual que [MoneyFieldAtajoTest]:** que Compose-**wasm** entregue el evento.
 * Ahí está el defecto de plataforma (Compose le hace `preventDefault` a ⌘A y no implementa el
 * suyo) y ningún test de este repo puede observarlo — se mide con una sonda en la página. Esto
 * cubre lo que es nuestro; lo otro se confirma a mano en el navegador.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h731dp-xhdpi")
class CampoConSeleccionTest {

    @get:Rule val composeRule = createComposeRule()

    private var ultimoTexto: String = ""

    /** Un campo cualquiera de la app: `String` afuera, [CampoConSeleccion] en el medio. */
    private fun montar(inicial: String, tope: Int = Int.MAX_VALUE) {
        ultimoTexto = inicial
        composeRule.setContent {
            var texto by remember { mutableStateOf(inicial) }
            MoviTheme {
                Box(Modifier.fillMaxSize()) {
                    val campo = rememberCampoConSeleccion(texto) {
                        texto = it.take(tope)
                        ultimoTexto = texto
                    }
                    BasicTextField(
                        value = campo.valor,
                        onValueChange = campo::alCambiar,
                        modifier = Modifier.onPreviewKeyEvent(campo.atajoDeSeleccionarTodo),
                    )
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun campoDeLaPantalla() = composeRule.onNode(hasSetTextAction())

    @Test
    fun seleccionar_todo_y_reescribir_reemplaza_el_texto() {
        montar("Bancolombia")

        val campo = campoDeLaPantalla()
        campo.performClick()
        campo.performKeyInput { withKeyDown(Key.CtrlLeft) { pressKey(Key.A) } }
        composeRule.waitForIdle()
        campo.performTextInput("Nequi")
        composeRule.waitForIdle()

        assertEquals("Nequi", ultimoTexto)
    }

    /** Sin el atajo, escribir al final concatena — o sea que la prueba de arriba mide algo. */
    @Test
    fun sin_el_atajo_escribir_al_final_concatena() {
        montar("Bancolombia")

        val campo = campoDeLaPantalla()
        campo.performClick()
        campo.performTextInput("Nequi")
        composeRule.waitForIdle()

        assertEquals("BancolombiaNequi", ultimoTexto)
    }

    // El campo vacío —que no se queda con la tecla— se prueba en `SeleccionarTodoTest`, sobre
    // [CampoConSeleccion.seleccionarTodo] y no acá: en este arnés `pressKey(Key.A)` con Ctrl
    // apretado además **escribe una «a»** en el campo, así que el resultado mediría el arnés y
    // no la decisión.

    /**
     * El recorte de la pantalla sigue mandando. Varias hojas filtran lo que escriben
     * (`it.take(MAX_CONCEPTO_LENGTH)`, `filterRateInput`): el campo avisa el texto, la pantalla
     * decide con cuánto se queda, y eso no cambió al meter el intermediario.
     */
    @Test
    fun lo_que_la_pantalla_recorta_se_sigue_recortando() {
        montar("Bancolombia", tope = 8)

        val campo = campoDeLaPantalla()
        campo.performClick()
        campo.performKeyInput { withKeyDown(Key.CtrlLeft) { pressKey(Key.A) } }
        composeRule.waitForIdle()
        campo.performTextInput("Davivienda")
        composeRule.waitForIdle()

        assertEquals("Davivien", ultimoTexto)
    }
}
