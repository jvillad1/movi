package com.jvillada.movi.ui.navegacion

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.jvillada.movi.platform.BackHandlerEffect
import com.jvillada.movi.theme.MoviTheme
import com.jvillada.movi.ui.LocalGoBack
import com.jvillada.movi.ui.LocalPilaDeHojas
import com.jvillada.movi.ui.PilaDeHojas
import com.jvillada.movi.ui.Screen
import com.jvillada.movi.ui.abrirLaGuiaAlArrancar
import com.jvillada.movi.ui.components.HeaderLeading
import com.jvillada.movi.ui.components.MarcoDeHoja
import com.jvillada.movi.ui.components.MinScreenHeader
import com.jvillada.movi.ui.components.ds.ROTULO_VOLVER
import com.jvillada.movi.ui.flechaDeVolver
import com.jvillada.movi.ui.hayAdondeVolver
import com.jvillada.movi.ui.volver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * # Vuelta 101 · La flecha ← no tumba la app
 *
 * ## Lo que se rompía
 *
 * El dueño: tocar la flecha ← de la barra de arriba cerraba la app; el atrás del sistema volvía bien.
 * La flecha hacía `backStack.removeLast()`. Compilado contra el SDK 35 eso es
 * `java.util.List.removeLast()` —el `SnapshotStateList` no lo declara—, un método que Android agregó
 * en la versión 15: en Android 14 o anterior, `NoSuchMethodError`. El atrás del sistema usaba
 * `removeAt(lastIndex)`. No dependía de la pantalla: cualquier flecha con historial detrás.
 *
 * Robolectric corre sobre la JVM 21 del computador, que sí tiene `removeLast()`, así que aquí la pila
 * es [PilaDeAndroid14]: la de la app, pero con `removeLast()`/`removeFirst()` lanzando lo mismo que en
 * el teléfono. Con el código de antes, tocar la flecha con historial lanzaba en estas pruebas.
 *
 * Se arma el cableado de `App.kt` —[flechaDeVolver] en `LocalGoBack`, `BackHandlerEffect` con
 * [hayAdondeVolver] y [volver]— con la flecha de verdad ([MinScreenHeader]) y el atrás de verdad (el
 * `OnBackPressedDispatcher` de la Activity).
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h731dp-xhdpi")
class LaFlechaNoTumbaLaAppTest {

    @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()

    /** La pila de la app como la ve un teléfono con Android 14: sin los métodos de lista de Java 21. */
    private class PilaDeAndroid14(private val real: SnapshotStateList<Screen>) : MutableList<Screen> by real {
        override fun removeLast(): Screen = throw NoSuchMethodError("java.util.List.removeLast (Android 15+)")
        override fun removeFirst(): Screen = throw NoSuchMethodError("java.util.List.removeFirst (Android 15+)")
        override fun toString(): String = real.toString()
    }

    private fun pilaDeAndroid14(vararg pantallas: Screen): MutableList<Screen> =
        PilaDeAndroid14(mutableStateListOf(*pantallas))

    /** El cableado de `App.kt`, con una pantalla con flecha y, si se pide, una hoja que nadie anotó. */
    private fun montar(pila: MutableList<Screen>, hojas: PilaDeHojas, conHoja: Boolean = false): () -> Boolean {
        var hojaAbierta by mutableStateOf(conHoja)
        composeRule.setContent {
            MoviTheme {
                BackHandlerEffect(enabled = hayAdondeVolver(hojas, pila)) { volver(hojas, pila) }
                CompositionLocalProvider(
                    LocalPilaDeHojas provides hojas,
                    LocalGoBack provides flechaDeVolver(hojas, pila),
                ) {
                    Box(Modifier.fillMaxSize()) {
                        Pantalla(pila)
                        if (hojaAbierta) {
                            MarcoDeHoja(onDismiss = { hojaAbierta = false }) { Text("Una hoja") }
                        }
                    }
                }
            }
        }
        return { hojaAbierta }
    }

    @Composable
    private fun Pantalla(pila: List<Screen>) {
        val arriba = pila.lastOrNull()
        Column {
            if (arriba != null && arriba != Screen.Dashboard && arriba != Screen.Tu) {
                MinScreenHeader(title = "Pantalla", leading = HeaderLeading.Back(fallback = Screen.Tu))
            }
            Text("Arriba: $arriba")
        }
    }

    private fun tocarLaFlecha() {
        composeRule.onNodeWithContentDescription(ROTULO_VOLVER).performClick()
        composeRule.waitForIdle()
    }

    private fun apretarAtras() {
        composeRule.runOnUiThread { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
        composeRule.waitForIdle()
    }

    @Test
    fun `con historial la flecha vuelve un paso sin lanzar en Android 14`() {
        val pila = pilaDeAndroid14(Screen.Dashboard, Screen.Tu, Screen.Accounts, Screen.Categorias)
        montar(pila, PilaDeHojas())
        tocarLaFlecha()
        assertEquals(listOf(Screen.Dashboard, Screen.Tu, Screen.Accounts), pila.toList())
        tocarLaFlecha()
        assertEquals(listOf(Screen.Dashboard, Screen.Tu), pila.toList())
        composeRule.onNodeWithText("Arriba: Tu").assertExists()
    }

    @Test
    fun `la flecha y el atras del sistema hacen lo mismo`() {
        val pila = pilaDeAndroid14(Screen.Dashboard, Screen.Tu, Screen.Accounts, Screen.Categorias)
        montar(pila, PilaDeHojas())
        apretarAtras()
        assertEquals("el atrás vuelve un paso", listOf(Screen.Dashboard, Screen.Tu, Screen.Accounts), pila.toList())
        tocarLaFlecha()
        assertEquals("la flecha vuelve un paso", listOf(Screen.Dashboard, Screen.Tu), pila.toList())
    }

    @Test
    fun `una pantalla sola, abierta desde afuera, la flecha va a su reserva con el Inicio debajo`() {
        // Un aviso o un enlace que dejó una sola pantalla (o una recarga de la web).
        val pila = pilaDeAndroid14(Screen.CapturaDelBanco)
        montar(pila, PilaDeHojas())
        tocarLaFlecha()
        assertEquals(listOf(Screen.Dashboard, Screen.Tu), pila.toList())
    }

    @Test
    fun `lo que se abre desde la guia al arrancar vuelve a la guia`() {
        val pila = pilaDeAndroid14(Screen.Dashboard)
        abrirLaGuiaAlArrancar(pila)
        pila.add(Screen.CapturaDelBanco)
        montar(pila, PilaDeHojas())
        tocarLaFlecha()
        assertEquals(listOf<Screen>(Screen.PrimerUso()), pila.toList())
    }

    @Test
    fun `con una hoja que nadie anoto, el atras la cierra como la X y no saca la pantalla`() {
        val pila = pilaDeAndroid14(Screen.Dashboard, Screen.Tu, Screen.Accounts)
        val hojas = PilaDeHojas()
        val abierta = montar(pila, hojas, conHoja = true)
        composeRule.onNodeWithText("Una hoja").assertExists()
        assertTrue("la hoja se anota sola (de reserva)", hojas.hayHojaAbierta)

        apretarAtras()

        assertFalse("el atrás cerró la hoja", abierta())
        composeRule.onNodeWithText("Una hoja").assertDoesNotExist()
        assertEquals(
            "cerrar la hoja no es navegar",
            listOf(Screen.Dashboard, Screen.Tu, Screen.Accounts),
            pila.toList(),
        )
        assertFalse(hojas.hayHojaAbierta)
    }

    @Test
    fun `la X de la hoja tampoco toca la pila`() {
        val pila = pilaDeAndroid14(Screen.Dashboard, Screen.Tu, Screen.Accounts)
        val hojas = PilaDeHojas()
        val abierta = montar(pila, hojas, conHoja = true)
        composeRule.onNodeWithContentDescription("Cerrar").performClick()
        composeRule.waitForIdle()
        assertFalse(abierta())
        assertEquals(listOf(Screen.Dashboard, Screen.Tu, Screen.Accounts), pila.toList())
        assertFalse(hojas.hayHojaAbierta)
    }
}
