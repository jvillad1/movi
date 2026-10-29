package com.jvillada.movi.ui.components.ds

import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.foundation.background
import com.jvillada.movi.theme.Medida
import com.jvillada.movi.theme.Movi
import com.jvillada.movi.theme.MoviTheme
import com.jvillada.movi.ui.components.TAG_OJO_DE_LOS_SALDOS
import com.jvillada.movi.ui.dashboard.EncabezadoDelInicio
import com.jvillada.movi.ui.dashboard.ROTULO_DE_LA_CAMPANA
import com.jvillada.movi.ui.dashboard.TAG_AVATAR_DEL_INICIO
import com.jvillada.movi.ui.dashboard.TAG_ENCABEZADO_DEL_INICIO
import androidx.compose.ui.test.onNodeWithTag
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * # El foco del teclado se ve (vuelta 55, accesibilidad)
 *
 * Con Tab, la revisión del 28-oct encontró el foco casi invisible: un cuadrado gris tenue sobre el
 * amarillo del avatar y un gris claro en las filas. Cada componente enfocable de `ds/` dibuja ahora el
 * anillo de `Modifier.anilloDeFoco` —2 dp en la tinta, a 2 dp del borde— cuando el foco llegó por
 * teclado, y **no** cuando llegó por un toque (el `:focus-visible` de la web).
 *
 * El modo de entrada se fija con un `InputModeManager` propio: Robolectric no tiene teclado, y lo que
 * se prueba es la regla del componente, no cómo Android cambia de modo.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w390dp-h1600dp-xhdpi")
class ElFocoSeVeConElTecladoTest {

    @get:Rule val composeRule = createComposeRule()

    private class ModoFijo(override val inputMode: InputMode) : InputModeManager {
        override fun requestInputMode(inputMode: InputMode): Boolean = false
    }

    /**
     * Los componentes enfocables de `ds/`, cada uno con cómo encontrarlo: por lo que dice (el nodo que
     * se toca junta el texto de adentro) o, si no dice nada, por un tag.
     */
    private val componentes: Map<String, SemanticsMatcher> = mapOf(
        "Boton" to hasText("Guardar"),
        "BotonDeIcono" to hasContentDescription("Buscar"),
        "Fila" to hasText("Arriendo"),
        "PildoraDeFiltro" to hasText("Gastos"),
        "PildoraDeAccion" to hasText("¿Cómo voy?"),
        "AccionRapida" to hasContentDescription("Anotar un gasto"),
        "Casilla" to hasTestTag("casilla"),
        "Superficie" to hasTestTag("superficie"),
    )

    private fun montar(modo: InputMode) {
        composeRule.setContent {
            CompositionLocalProvider(LocalInputModeManager provides ModoFijo(modo)) {
                MoviTheme {
                    Column {
                        Boton("Guardar", onClick = {})
                        BotonDeIcono(Icons.Rounded.Search, "Buscar", onClick = {})
                        Fila("Arriendo", onClick = {})
                        PildoraDeFiltro("Gastos", seleccionada = false, onClick = {})
                        PildoraDeAccion("¿Cómo voy?", onClick = {})
                        AccionRapida("Gasto", Icons.Rounded.Add, onClick = {}, descripcion = "Anotar un gasto")
                        Casilla(marcada = false, onCambiar = {}, modifier = Modifier.testTag("casilla"))
                        Superficie(onClick = {}, modifier = Modifier.testTag("superficie")) { Text("Una tarjeta") }
                    }
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun nodo(nombre: String): SemanticsNode = composeRule.onNode(componentes.getValue(nombre)).fetchSemanticsNode()

    private fun anillo(nombre: String): Boolean? = nodo(nombre).config.getOrNull(AnilloDeFocoVisibleKey)

    private fun enfocar(nombre: String) {
        composeRule.onNode(componentes.getValue(nombre)).performSemanticsAction(SemanticsActions.RequestFocus)
        composeRule.waitForIdle()
    }

    @Test
    fun `con el teclado, el componente enfocado lleva el anillo y los demas no`() {
        montar(InputMode.Keyboard)
        for (tag in componentes.keys) {
            enfocar(tag)
            assertEquals(true, anillo(tag), "«$tag» enfocado con el teclado no dibuja el anillo")
            val otros = componentes.keys.filter { it != tag && anillo(it) == true }
            assertTrue(otros.isEmpty(), "con el foco en «$tag» también llevan anillo: $otros")
        }
    }

    @Test
    fun `con el dedo no hay anillo aunque haya foco`() {
        montar(InputMode.Touch)
        // En modo táctil un `clickable` no toma el foco; la casilla y la fila tampoco. Si alguno lo
        // tomara, igual no tiene que dibujar el anillo.
        for (tag in componentes.keys) {
            runCatching { enfocar(tag) }
            assertFalse(anillo(tag) == true, "«$tag» dibuja el anillo con el dedo")
        }
    }

    /**
     * **Revisión de la vuelta 55: el encabezado del Inicio.** El avatar es el primer destino del Tab y
     * mostraba el cuadrado gris tenue de siempre sobre el amarillo; el ojo y la campana, lo mismo. Los
     * tres llevan el anillo, y el anillo (4 dp afuera del círculo) **entra entero** en el encabezado: nada
     * lo recorta.
     */
    @Test
    fun `en el encabezado del Inicio el avatar, el ojo y la campana llevan el anillo entero`() {
        composeRule.setContent {
            CompositionLocalProvider(LocalInputModeManager provides ModoFijo(InputMode.Keyboard)) {
                MoviTheme {
                    EncabezadoDelInicio(
                        saludo = "Hola, Demo",
                        subtitulo = "Octubre 2026",
                        iniciales = "DM",
                        conPunto = true,
                        onAvatar = {},
                        onCampana = {},
                        modifier = Modifier.background(Movi.colores.marca),
                    )
                }
            }
        }
        composeRule.waitForIdle()
        val encabezado = composeRule.onNodeWithTag(TAG_ENCABEZADO_DEL_INICIO, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val afuera = with(composeRule.density) { (Medida.separacionDelAnilloDeFoco + Medida.trazoDelAnilloDeFoco).toPx() }
        val delEncabezado = mapOf(
            "avatar" to hasTestTag(TAG_AVATAR_DEL_INICIO),
            "ojo" to hasTestTag(TAG_OJO_DE_LOS_SALDOS),
            "campana" to hasContentDescription(ROTULO_DE_LA_CAMPANA),
        )
        for ((nombre, matcher) in delEncabezado) {
            composeRule.onNode(matcher).performSemanticsAction(SemanticsActions.RequestFocus)
            composeRule.waitForIdle()
            val nodo = composeRule.onNode(matcher).fetchSemanticsNode()
            assertEquals(true, nodo.config.getOrNull(AnilloDeFocoVisibleKey), "el $nombre enfocado con el teclado no dibuja el anillo")
            val b = nodo.boundsInRoot
            assertTrue(
                b.top - afuera >= encabezado.top - 0.5f && b.bottom + afuera <= encabezado.bottom + 0.5f &&
                    b.left - afuera >= encabezado.left - 0.5f && b.right + afuera <= encabezado.right + 0.5f,
                "el anillo del $nombre se sale del encabezado: $b con $afuera px afuera, encabezado $encabezado",
            )
        }
    }

    @Test
    fun `la regla es la del focus-visible`() {
        assertTrue(seVeElAnilloDeFoco(enfocado = true, modo = InputMode.Keyboard))
        assertFalse(seVeElAnilloDeFoco(enfocado = true, modo = InputMode.Touch))
        assertFalse(seVeElAnilloDeFoco(enfocado = false, modo = InputMode.Keyboard))
    }

    @Test
    fun `cada componente enfocable esta en la prueba`() {
        montar(InputMode.Keyboard)
        // Que ninguno de la lista se haya quedado sin nodo: si un componente deja de ser enfocable,
        // `RequestFocus` no existe y la prueba de arriba no lo vería.
        val sinFoco = componentes.keys.filter { nodo(it).config.getOrNull(SemanticsActions.RequestFocus) == null }
        assertTrue(sinFoco.isEmpty(), "no se pueden enfocar: $sinFoco")
        assertTrue(
            composeRule.onAllNodes(SemanticsMatcher.keyIsDefined(AnilloDeFocoVisibleKey)).fetchSemanticsNodes().size >= componentes.size,
        )
    }
}
