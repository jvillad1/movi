package com.jvillada.movi.ui.components

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.KeyInjectionScope
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.withKeyDown
import androidx.compose.ui.unit.dp
import com.jvillada.movi.ConClaseDeAncho
import com.jvillada.movi.EsqueletoDeLaCascara
import com.jvillada.movi.theme.LocalMovimientoReducido
import com.jvillada.movi.theme.Medida
import com.jvillada.movi.ui.Screen
import com.jvillada.movi.ui.components.ds.Campo
import com.jvillada.movi.ui.components.ds.TAG_CAMPO
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * # Bajar con el teclado (vuelta 118)
 *
 * Claudia quiso bajar en la web con las flechas, Av Pág y el espacio, y no pasaba nada. Todo en la
 * cáscara real ([EsqueletoDeLaCascara]) con teclado físico, como en la web: nadie toca nada antes de
 * la tecla (la raíz de la cáscara es la que la recibe). Ver [TecladoQueDesplaza].
 *
 * Cada prueba que dice «no se mueve» tiene al lado una que se mueve con las mismas teclas: así no
 * pasa por vacía (una tecla que no le llega a nadie también «no mueve»).
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w1280dp-h800dp-xhdpi")
class DesplazarConElTecladoTest {

    @get:Rule val composeRule = createComposeRule()

    private var paso = 0f

    private fun enLaCascara(
        tecladoFisico: Boolean = true,
        reducido: Boolean = false,
        contenido: @Composable () -> Unit,
    ) {
        composeRule.setContent {
            CompositionLocalProvider(LocalHayTecladoFisico provides tecladoFisico, LocalMovimientoReducido provides reducido) {
                ConClaseDeAncho {
                    paso = with(LocalDensity.current) { Medida.pasoDelTeclado.toPx() }
                    EsqueletoDeLaCascara(
                        pantalla = Screen.Accounts,
                        activeTab = NavTab.TU,
                        conNavegacion = true,
                        onTabSelected = {},
                        relevoDeScroll = remember { RelevoDeScroll() },
                    ) {
                        contenido()
                    }
                }
            }
        }
        composeRule.waitForIdle()
    }

    /** Una pantalla larga: 60 renglones de 80 dp con un `verticalScroll` anotado. */
    @Composable
    private fun PantallaLarga(scroll: ScrollState, arriba: Boolean = true, conCampo: Boolean = false) {
        CapaDeLaPantalla(arriba = arriba) {
            Column(Modifier.fillMaxSize()) {
                if (conCampo) {
                    var texto by remember { mutableStateOf("Mercado") }
                    Campo(texto, { texto = it }, rotulo = "Buscar")
                }
                Column(Modifier.weight(1f).fillMaxWidth().desplazableConElTeclado(scroll)) {
                    repeat(60) { Text("Renglón $it", Modifier.height(80.dp)) }
                }
            }
        }
    }

    private fun teclas(bloque: KeyInjectionScope.() -> Unit) {
        composeRule.onRoot().performKeyInput(bloque)
        composeRule.waitForIdle()
    }

    @Test
    fun `flecha abajo baja un paso y flecha arriba lo sube`() {
        val scroll = ScrollState(0)
        enLaCascara { PantallaLarga(scroll) }

        teclas { pressKey(Key.DirectionDown) }
        assertEquals(paso.toInt(), scroll.value)

        teclas { pressKey(Key.DirectionDown) }
        assertEquals((2 * paso).toInt(), scroll.value)

        teclas { pressKey(Key.DirectionUp) }
        assertEquals(paso.toInt(), scroll.value)
    }

    @Test
    fun `Av Pag y el espacio bajan casi una pantalla, Re Pag y Shift+Espacio la suben`() {
        val scroll = ScrollState(0)
        enLaCascara { PantallaLarga(scroll) }
        val pagina = scroll.viewportSize - paso.toInt()
        assertTrue(pagina > 3 * paso, "la pantalla de la prueba tiene que medir varias filas: ${scroll.viewportSize}")

        teclas { pressKey(Key.PageDown) }
        assertEquals(pagina, scroll.value, "Av Pág: el alto visible menos un paso")

        teclas { pressKey(Key.Spacebar) }
        assertEquals(2 * pagina, scroll.value, "el espacio, igual que Av Pág")

        teclas { withKeyDown(Key.ShiftLeft) { pressKey(Key.Spacebar) } }
        assertEquals(pagina, scroll.value, "Shift+Espacio sube")

        teclas { pressKey(Key.PageUp) }
        assertEquals(0, scroll.value)
    }

    @Test
    fun `Fin llega al final e Inicio vuelve al principio`() {
        val scroll = ScrollState(0)
        enLaCascara { PantallaLarga(scroll) }

        teclas { pressKey(Key.MoveEnd) }
        assertTrue(scroll.maxValue > 0)
        assertEquals(scroll.maxValue, scroll.value)

        teclas { pressKey(Key.MoveHome) }
        assertEquals(0, scroll.value)
    }

    @Test
    fun `una LazyColumn tambien baja y Fin llega al ultimo renglon`() {
        var lista: LazyListState? = null
        enLaCascara {
            CapaDeLaPantalla(arriba = true) {
                val estado = rememberListaConElTeclado()
                lista = estado
                LazyColumn(state = estado, modifier = Modifier.fillMaxSize()) {
                    items(200) { Text("Movimiento $it", Modifier.height(64.dp)) }
                }
            }
        }
        val estado = lista!!

        teclas { pressKey(Key.PageDown) }
        assertTrue(estado.firstVisibleItemIndex > 0, "Av Pág tenía que mover la lista")

        teclas { pressKey(Key.MoveEnd) }
        assertTrue(!estado.canScrollForward, "Fin tenía que dejar la lista al final")
        assertEquals(199, estado.layoutInfo.visibleItemsInfo.last().index)

        teclas { pressKey(Key.MoveHome) }
        assertEquals(0, estado.firstVisibleItemIndex)
        assertEquals(0, estado.firstVisibleItemScrollOffset)
    }

    @Test
    fun `con una hoja abierta se desplaza la hoja y no la pantalla de atras`() {
        val pantalla = ScrollState(0)
        val hoja = ScrollState(0)
        enLaCascara {
            Box(Modifier.fillMaxSize()) {
                PantallaLarga(pantalla)
                MarcoDeHoja(onDismiss = {}) {
                    Column(Modifier.weight(1f, fill = false).desplazableConElTeclado(hoja)) {
                        repeat(40) { Text("Categoría $it", Modifier.height(80.dp)) }
                    }
                }
            }
        }

        teclas { pressKey(Key.DirectionDown) }
        teclas { pressKey(Key.PageDown) }

        assertTrue(hoja.value > paso, "las teclas tenían que mover la hoja: ${hoja.value}")
        assertEquals(0, pantalla.value, "la pantalla de atrás no se mueve con una hoja encima")
    }

    @Test
    fun `una hoja sin nada que desplazar tampoco mueve la pantalla de atras`() {
        val pantalla = ScrollState(0)
        var abierta by mutableStateOf(true)
        enLaCascara {
            Box(Modifier.fillMaxSize()) {
                PantallaLarga(pantalla)
                if (abierta) MarcoDeHoja(onDismiss = { abierta = false }) { Text("¿Borrar la cuenta?") }
            }
        }

        teclas { pressKey(Key.PageDown) }
        assertEquals(0, pantalla.value)

        // Control: cerrada la hoja, la misma tecla mueve la pantalla.
        abierta = false
        composeRule.waitForIdle()
        teclas { pressKey(Key.PageDown) }
        assertTrue(pantalla.value > 0, "sin la hoja, Av Pág tenía que mover la pantalla")
    }

    @Test
    fun `se mueve la pantalla de arriba, no la que sale`() {
        val deAbajo = ScrollState(0)
        val deArriba = ScrollState(0)
        enLaCascara {
            Box(Modifier.fillMaxSize()) {
                PantallaLarga(deAbajo, arriba = false)
                PantallaLarga(deArriba, arriba = true)
            }
        }

        teclas { pressKey(Key.DirectionDown) }

        assertEquals(paso.toInt(), deArriba.value)
        assertEquals(0, deAbajo.value)
    }

    @Test
    fun `con el foco en un campo de texto las teclas son del campo`() {
        val scroll = ScrollState(0)
        enLaCascara { PantallaLarga(scroll, conCampo = true) }
        composeRule.onNodeWithTag(TAG_CAMPO).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(TAG_CAMPO).assertIsFocused()

        listOf(Key.DirectionDown, Key.DirectionUp, Key.Spacebar, Key.PageDown, Key.MoveEnd, Key.MoveHome).forEach { tecla ->
            composeRule.onNodeWithTag(TAG_CAMPO).performKeyInput { pressKey(tecla) }
            composeRule.waitForIdle()
            assertEquals(0, scroll.value, "$tecla con el cursor en el campo movió la pantalla")
        }
    }

    @Test
    fun `sin campo enfocado las mismas teclas si mueven la pantalla con el campo a la vista`() {
        val scroll = ScrollState(0)
        enLaCascara { PantallaLarga(scroll, conCampo = true) }

        teclas { pressKey(Key.DirectionDown) }
        assertEquals(paso.toInt(), scroll.value)
        teclas { pressKey(Key.Spacebar) }
        assertTrue(scroll.value > paso)
    }

    /** Una pantalla larga con dos botones arriba, para el Tab y el Espacio sobre un botón. */
    @Composable
    private fun PantallaConBotones(scroll: ScrollState, alTocar: (String) -> Unit) {
        CapaDeLaPantalla(arriba = true) {
            Column(Modifier.fillMaxSize()) {
                // `canFocus = true`: en Robolectric el teléfono está en modo táctil, donde un `clickable`
                // no se enfoca; en la web (teclado) sí. Así la prueba ve lo mismo que la web.
                Text("Uno", Modifier.testTag("uno").focusProperties { canFocus = true }.clickable { alTocar("uno") })
                Text("Dos", Modifier.testTag("dos").focusProperties { canFocus = true }.clickable { alTocar("dos") })
                Column(Modifier.weight(1f).fillMaxWidth().desplazableConElTeclado(scroll)) {
                    repeat(60) { Text("Renglón $it", Modifier.height(80.dp)) }
                }
            }
        }
    }

    /**
     * La raíz toma el foco sin semántica (un `focusTarget` desnudo): si alguna vez fuera una parada del
     * Tab, el foco quedaría en ella y ningún nodo con semántica se vería enfocado.
     */
    @Test
    fun `el Tab recorre lo mismo de siempre y la raiz no es una parada`() {
        val scroll = ScrollState(0)
        enLaCascara { PantallaConBotones(scroll) {} }
        composeRule.onAllNodes(isFocused()).assertCountEquals(0)

        teclas { pressKey(Key.Tab) }
        composeRule.onAllNodes(isFocused()).assertCountEquals(1)

        composeRule.onNodeWithTag("uno").performSemanticsAction(SemanticsActions.RequestFocus)
        composeRule.waitForIdle()
        teclas { pressKey(Key.Tab) }
        composeRule.onNodeWithTag("dos").assertIsFocused()
        teclas { withKeyDown(Key.ShiftLeft) { pressKey(Key.Tab) } }
        composeRule.onNodeWithTag("uno").assertIsFocused()
        // Shift+Tab hacia atrás por el rail y más allá: en ningún paso el foco cae en la raíz invisible.
        repeat(16) { vez ->
            teclas { withKeyDown(Key.ShiftLeft) { pressKey(Key.Tab) } }
            assertEquals(1, composeRule.onAllNodes(isFocused()).fetchSemanticsNodes().size, "Shift+Tab número ${vez + 1}")
        }
    }

    @Test
    fun `con un boton enfocado el Espacio lo toca y no desplaza, y las flechas si desplazan`() {
        val scroll = ScrollState(0)
        val tocados = mutableListOf<String>()
        enLaCascara { PantallaConBotones(scroll) { tocados += it } }
        composeRule.onNodeWithTag("uno").performSemanticsAction(SemanticsActions.RequestFocus)
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("uno").assertIsFocused()

        teclas { pressKey(Key.Spacebar) }
        assertEquals(listOf("uno"), tocados)
        assertEquals(0, scroll.value, "el Espacio sobre un botón es del botón")

        teclas { pressKey(Key.DirectionDown) }
        assertEquals(paso.toInt(), scroll.value, "un botón no usa las flechas: bajan la pantalla")
    }

    @Test
    fun `sin teclado fisico (el telefono) las teclas no hacen nada`() {
        val scroll = ScrollState(0)
        enLaCascara(tecladoFisico = false) { PantallaLarga(scroll) }

        teclas { pressKey(Key.PageDown) }

        assertEquals(0, scroll.value)
    }

    @Test
    fun `con poco movimiento salta en el acto y si no se anima`() {
        val conAnimacion = ScrollState(0)
        enLaCascara { PantallaLarga(conAnimacion) }
        composeRule.mainClock.autoAdvance = false
        composeRule.onRoot().performKeyInput { pressKey(Key.PageDown) }
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.mainClock.advanceTimeByFrame()
        val alPrincipio = conAnimacion.value
        composeRule.mainClock.advanceTimeBy(1_000)
        val pagina = conAnimacion.viewportSize - paso.toInt()
        assertTrue(alPrincipio < pagina, "con movimiento, a dos cuadros todavía no llega: $alPrincipio")
        assertEquals(pagina, conAnimacion.value)
    }

    @Test
    fun `con el sistema pidiendo menos movimiento la pagina se mueve entera de una`() {
        val sinAnimacion = ScrollState(0)
        enLaCascara(reducido = true) { PantallaLarga(sinAnimacion) }
        composeRule.mainClock.autoAdvance = false
        composeRule.onRoot().performKeyInput { pressKey(Key.PageDown) }
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.mainClock.advanceTimeByFrame()
        assertEquals(sinAnimacion.viewportSize - paso.toInt(), sinAnimacion.value)
    }

    @Test
    fun `que hace cada tecla`() {
        val abajo = KeyEventType.KeyDown
        assertEquals(AccionDelTeclado.BAJAR, queHaceLaTecla(Key.DirectionDown, abajo, conModificador = false, conShift = false))
        assertEquals(AccionDelTeclado.SUBIR, queHaceLaTecla(Key.DirectionUp, abajo, false, false))
        assertEquals(AccionDelTeclado.PAGINA_ABAJO, queHaceLaTecla(Key.PageDown, abajo, false, false))
        assertEquals(AccionDelTeclado.PAGINA_ABAJO, queHaceLaTecla(Key.Spacebar, abajo, false, false))
        assertEquals(AccionDelTeclado.PAGINA_ARRIBA, queHaceLaTecla(Key.PageUp, abajo, false, false))
        assertEquals(AccionDelTeclado.PAGINA_ARRIBA, queHaceLaTecla(Key.Spacebar, abajo, false, conShift = true))
        assertEquals(AccionDelTeclado.AL_PRINCIPIO, queHaceLaTecla(Key.MoveHome, abajo, false, false))
        assertEquals(AccionDelTeclado.AL_FINAL, queHaceLaTecla(Key.MoveEnd, abajo, false, false))
        // Lo que no se toca: el KeyUp, los atajos con Ctrl/⌘/Alt, Shift con flechas, y las demás teclas.
        assertNull(queHaceLaTecla(Key.DirectionDown, KeyEventType.KeyUp, false, false))
        assertNull(queHaceLaTecla(Key.DirectionDown, abajo, conModificador = true, conShift = false))
        assertNull(queHaceLaTecla(Key.Spacebar, abajo, conModificador = true, conShift = false))
        assertNull(queHaceLaTecla(Key.DirectionDown, abajo, false, conShift = true))
        assertNull(queHaceLaTecla(Key.Escape, abajo, false, false))
        assertNull(queHaceLaTecla(Key.Enter, abajo, false, false))
        assertNull(queHaceLaTecla(Key.Tab, abajo, false, false))
        assertNull(queHaceLaTecla(Key.A, abajo, false, false))
    }
}
