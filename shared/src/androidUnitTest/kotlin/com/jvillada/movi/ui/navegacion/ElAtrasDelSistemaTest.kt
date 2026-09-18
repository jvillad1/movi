package com.jvillada.movi.ui.navegacion

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.jvillada.movi.platform.BackHandlerEffect
import com.jvillada.movi.theme.MoviTheme
import com.jvillada.movi.ui.AtrasCierraEstaHoja
import com.jvillada.movi.ui.LocalPilaDeHojas
import com.jvillada.movi.ui.PilaDeHojas
import com.jvillada.movi.ui.Screen
import com.jvillada.movi.ui.atras
import com.jvillada.movi.ui.hayAdondeVolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * # El botón «atrás» del teléfono, con una hoja abierta
 *
 * ## Lo que se rompía
 *
 * El «atrás» conocía exactamente DOS hojas, las que viven en `App.kt`. Las de Perfil —«Cambiar
 * contraseña», el color del avatar, el día de corte, los días de aviso— son overlays que esa
 * pantalla dibuja por su cuenta, así que para el «atrás» no existían: apretarlo **sacaba el tope
 * de la pila**. El dueño escribía las dos contraseñas, apretaba atrás para cerrar la hoja y
 * aterrizaba en «Más», con todo lo tipeado perdido y sin una palabra de explicación.
 *
 * Acá se aprieta el «atrás» **de verdad** —el `OnBackPressedDispatcher` de la Activity, el mismo
 * que usa el sistema— sobre el mismo cableado que arma `App.kt`: `BackHandlerEffect` prendido por
 * [hayAdondeVolver] y atendido por [atras], con las hojas anotándose solas con
 * [AtrasCierraEstaHoja].
 *
 * Lo que NO puede cubrir: el gesto de deslizar desde el borde, y que la hoja de Perfil (que edita
 * otro agente) llame a [AtrasCierraEstaHoja]. Eso último es una línea por hoja, y el mecanismo es
 * opt-in a propósito.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h731dp-xhdpi")
class ElAtrasDelSistemaTest {

    @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()

    /** El cableado de `App.kt`, sin las 25 pantallas. */
    @Composable
    private fun CascaraDeLaApp(
        pila: SnapshotStateList<Screen>,
        hojas: PilaDeHojas,
        contenido: @Composable () -> Unit,
    ) {
        MoviTheme {
            BackHandlerEffect(
                enabled = hayAdondeVolver(hojas, pila),
                onBack = { atras(hojas, pila) },
            )
            CompositionLocalProvider(LocalPilaDeHojas provides hojas) {
                Box { contenido() }
            }
        }
    }

    private fun apretarAtras() {
        composeRule.runOnUiThread {
            composeRule.activity.onBackPressedDispatcher.onBackPressed()
        }
        composeRule.waitForIdle()
    }

    @Test
    fun `con una hoja abierta, atras cierra la hoja y no toca la pila`() {
        val pila = mutableStateListOf<Screen>(Screen.Dashboard, Screen.Mas, Screen.Profile)
        val hojas = PilaDeHojas()

        composeRule.setContent {
            var abierta by remember { mutableStateOf(true) }
            CascaraDeLaApp(pila, hojas) {
                if (abierta) {
                    AtrasCierraEstaHoja { abierta = false }
                    Text("Cambiar contraseña")
                }
            }
        }

        composeRule.onNodeWithText("Cambiar contraseña").assertIsDisplayed()
        apretarAtras()

        composeRule.onNodeWithText("Cambiar contraseña").assertDoesNotExist()
        assertEquals(
            "la pila no se puede mover: cerrar una hoja no es navegar",
            listOf(Screen.Dashboard, Screen.Mas, Screen.Profile),
            pila.toList(),
        )
        assertFalse(hojas.hayHojaAbierta)
    }

    @Test
    fun `dos hojas encimadas - atras cierra la de arriba, no las dos`() {
        val pila = mutableStateListOf<Screen>(Screen.Dashboard)
        val hojas = PilaDeHojas()

        composeRule.setContent {
            var abajo by remember { mutableStateOf(true) }
            var arriba by remember { mutableStateOf(true) }
            CascaraDeLaApp(pila, hojas) {
                if (abajo) {
                    AtrasCierraEstaHoja { abajo = false }
                    Text("Agregar")
                }
                // Se compone después, así que es la que se pinta encima.
                if (arriba) {
                    AtrasCierraEstaHoja { arriba = false }
                    Text("Se repite todos los meses")
                }
            }
        }

        assertEquals(2, hojas.cuantas)
        apretarAtras()

        composeRule.onNodeWithText("Se repite todos los meses").assertDoesNotExist()
        composeRule.onNodeWithText("Agregar").assertIsDisplayed()

        apretarAtras()
        composeRule.onNodeWithText("Agregar").assertDoesNotExist()
        assertEquals(listOf(Screen.Dashboard), pila.toList())
    }

    @Test
    fun `sin hojas abiertas, atras saca el tope de la pila`() {
        val pila = mutableStateListOf<Screen>(Screen.Dashboard, Screen.Mas, Screen.Profile)
        val hojas = PilaDeHojas()

        composeRule.setContent { CascaraDeLaApp(pila, hojas) { Text("Perfil") } }
        apretarAtras()

        assertEquals(listOf(Screen.Dashboard, Screen.Mas), pila.toList())
    }

    @Test
    fun `en el Inicio y sin hojas, el atras no lo atiende Movi`() {
        // Con el handler apagado, el «atrás» es del sistema: en Android, salir de la app. Si Movi
        // lo atendiera igual, el teléfono se quedaría sin forma de salir desde el Inicio.
        val pila = mutableStateListOf<Screen>(Screen.Dashboard)
        val hojas = PilaDeHojas()

        composeRule.setContent { CascaraDeLaApp(pila, hojas) { Text("Inicio") } }

        assertFalse(hayAdondeVolver(hojas, pila))
        assertTrue(pila.size == 1)
    }
}
