package com.jvillada.movi

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.click
import androidx.compose.ui.test.performTouchInput
import com.jvillada.movi.ui.components.ANCHO_DEL_RAIL_COMPACTO
import com.jvillada.movi.ui.components.RELLENO_DEL_ITEM_DEL_RAIL_COMPACTO
import com.jvillada.movi.ui.components.RotuloDelRailCompacto
import com.jvillada.movi.ui.components.TAG_VELO_DE_HOJA
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import com.jvillada.movi.ui.Screen
import com.jvillada.movi.ui.components.LocalWindowWidthClass
import com.jvillada.movi.ui.components.MarcoDeHoja
import com.jvillada.movi.ui.components.NavTab
import com.jvillada.movi.ui.components.RelevoDeScroll
import com.jvillada.movi.ui.components.TAG_PANEL_DE_HOJA
import com.jvillada.movi.ui.components.WindowWidthClass
import com.jvillada.movi.ui.components.destinosPrincipales
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * # El esqueleto responsivo de la web (Ola W1), medido
 *
 * La queja del dueño: «parece la app pero grande». Estas pruebas montan el esqueleto de App.kt
 * ([EsqueletoDeLaCascara]) con la clase de ancho que le toca de verdad a cada ventana
 * ([ConClaseDeAncho]) y miden posiciones con `boundsInRoot` —no capturas: `captureToImage` se cuelga
 * en Robolectric—:
 *
 * - a 390 dp, barra inferior y contenido de borde a borde; la hoja, pegada abajo;
 * - a 768 dp, rail compacto de 80 dp y ninguna barra inferior;
 * - a 1.280 dp, rail ancho y la columna de lectura de 720 dp centrada en lo que queda; la hoja,
 *   centrada, de a lo sumo 560 dp, y Escape la cierra.
 *
 * `sdk = [34]` y `GraphicsMode.NATIVE`: los rótulos del rail se miden con el motor de texto real (en
 * el SDK 24 el texto con interlineado mide ancho cero).
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class EsqueletoResponsivoTest {

    @get:Rule val composeRule = createComposeRule()

    private var clase: WindowWidthClass? = null

    private fun montar(pantalla: Screen = Screen.Accounts, tab: NavTab? = NavTab.PATRIMONIO) {
        composeRule.setContent {
            ConClaseDeAncho {
                clase = LocalWindowWidthClass.current
                EsqueletoDeLaCascara(
                    pantalla = pantalla,
                    activeTab = tab,
                    conNavegacion = true,
                    onTabSelected = {},
                    relevoDeScroll = remember { RelevoDeScroll() },
                ) {
                    Box(Modifier.fillMaxSize().testTag("contenido"))
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun limites(tag: String): Rect = composeRule.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
    private fun raiz(): Rect = composeRule.onRoot().fetchSemanticsNode().boundsInRoot
    private fun enDp(px: Float): Float = with(composeRule.density) { px.toDp().value }
    private fun hay(texto: String): Boolean =
        composeRule.onAllNodesWithText(texto, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    // ── El esqueleto ──────────────────────────────────────────────────────────

    @Test
    @Config(qualifiers = "w390dp-h800dp-xhdpi")
    fun `a 390 dp barra inferior, sin rail y el contenido de borde a borde`() {
        montar()

        assertEquals(WindowWidthClass.Compact, clase)
        // La barra inferior dice «Movimientos» entero; y sin rail el contenido arranca en 0.
        composeRule.onNodeWithText("Movimientos", useUnmergedTree = true).assertIsDisplayed()
        val contenido = limites("contenido")
        assertEquals(0f, contenido.left)
        assertEquals(raiz().right, contenido.right)
    }

    @Test
    @Config(qualifiers = "w768dp-h1024dp-mdpi")
    fun `a 768 dp rail compacto y ninguna barra inferior`() {
        montar()

        assertEquals(WindowWidthClass.Medium, clase)
        // Cada ítem se lee con el nombre entero (el rótulo corto no llega a la accesibilidad).
        destinosPrincipales.forEach {
            composeRule.onNodeWithContentDescription(it.label, useUnmergedTree = true).assertIsDisplayed()
        }
        composeRule.onNodeWithContentDescription("Agregar", useUnmergedTree = true).assertIsDisplayed()
        // Ni la barra del teléfono («Movimientos» escrito) ni el rail ancho («Agregar» escrito).
        assertFalse(hay("Movimientos"), "la barra inferior es solo del teléfono")
        assertFalse(hay("Agregar"), "el rail ancho es del escritorio")

        val contenido = limites("contenido")
        assertEquals(80f, enDp(contenido.left), 0.5f, "el contenido empieza donde termina el rail de 80 dp")
        // 768 − 80 = 688 < 720: la columna de lectura llena lo que queda.
        assertEquals(688f, enDp(contenido.width), 0.5f)
    }

    /**
     * El rótulo corto, en el ancho que le deja el ítem del rail (80 − 2×2 dp), con la letra de la app
     * y con la del sistema. Se monta el rótulo solo porque el ítem le borra la semántica a propósito
     * (el lector de pantalla dice el nombre entero): así se puede leer cómo quedó el texto.
     */
    private fun comprobarQueLosRotulosEntran(letraDelSistema: Float) {
        composeRule.setContent {
            ConClaseDeAncho(escala = 1.12f * letraDelSistema) {
                Column {
                    destinosPrincipales.forEach { dest ->
                        Box(Modifier.width(ANCHO_DEL_RAIL_COMPACTO - RELLENO_DEL_ITEM_DEL_RAIL_COMPACTO * 2)) {
                            RotuloDelRailCompacto(dest.rotuloCorto, activo = dest.tab == NavTab.PATRIMONIO)
                        }
                    }
                }
            }
        }
        composeRule.waitForIdle()
        val ancho = ANCHO_DEL_RAIL_COMPACTO - RELLENO_DEL_ITEM_DEL_RAIL_COMPACTO * 2
        destinosPrincipales.forEach { dest ->
            val nodo = composeRule.onNodeWithText(dest.rotuloCorto, useUnmergedTree = true).fetchSemanticsNode()
            val resultados = mutableListOf<TextLayoutResult>()
            nodo.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(resultados)
            val texto = resultados.single()
            assertEquals(1, texto.lineCount, "«${dest.rotuloCorto}» en un renglón a ${letraDelSistema}×")
            assertFalse(texto.multiParagraph.didExceedMaxLines, "«${dest.rotuloCorto}» sin partirse a ${letraDelSistema}×")
            val anchoDelTexto = enDp(texto.getLineRight(0) - texto.getLineLeft(0))
            assertTrue(
                anchoDelTexto <= ancho.value + 0.5f,
                "«${dest.rotuloCorto}» entra entero a ${letraDelSistema}×: mide $anchoDelTexto dp contra ${ancho.value}",
            )
        }
    }

    @Test
    @Config(qualifiers = "w768dp-h1024dp-mdpi")
    fun `los rotulos cortos del rail entran enteros en un renglon`() {
        comprobarQueLosRotulosEntran(letraDelSistema = 1f)
    }

    /** Con la letra del sistema al 130 % «Patrimonio» ya no entra a su tamaño: se achica, no se corta. */
    @Test
    @Config(qualifiers = "w768dp-h1024dp-mdpi")
    fun `con la letra del sistema al 130 por ciento ningun rotulo del rail se corta`() {
        comprobarQueLosRotulosEntran(letraDelSistema = 1.3f)
    }

    @Test
    @Config(qualifiers = "w1280dp-h800dp-mdpi")
    fun `a 1280 dp la pantalla de lectura mide 720 y se centra a la derecha del rail`() {
        montar()

        assertEquals(WindowWidthClass.Expanded, clase)
        composeRule.onNodeWithText("Agregar", useUnmergedTree = true).assertIsDisplayed()

        val contenido = limites("contenido")
        assertTrue(enDp(contenido.width) <= 720.5f, "la columna de lectura mide ${enDp(contenido.width)} dp")
        assertEquals(720f, enDp(contenido.width), 0.5f)
        val margenIzquierdo = enDp(contenido.left) - 216f
        val margenDerecho = enDp(raiz().right - contenido.right)
        assertTrue(margenIzquierdo > 0f, "la columna empieza a la derecha del rail")
        assertTrue(abs(margenIzquierdo - margenDerecho) <= 1f, "centrada: $margenIzquierdo contra $margenDerecho")
    }

    @Test
    @Config(qualifiers = "w1920dp-h1080dp-mdpi")
    fun `a 1920 dp Hoy es un tablero de 1280`() {
        montar(pantalla = Screen.Dashboard, tab = NavTab.HOY)

        assertEquals(WindowWidthClass.Expanded, clase)
        assertEquals(1_280f, enDp(limites("contenido").width), 0.5f)
    }

    @Test
    @Config(qualifiers = "w1280dp-h800dp-mdpi")
    fun `a 1280 dp Hoy usa todo lo que deja el rail`() {
        montar(pantalla = Screen.Dashboard, tab = NavTab.HOY)

        // 1280 − 216 = 1064, por debajo del tope de 1280.
        assertEquals(1_064f, enDp(limites("contenido").width), 0.5f)
    }

    // ── La hoja ───────────────────────────────────────────────────────────────

    private var cerrada = false

    private fun montarHoja(dismissEnabled: Boolean = true) {
        cerrada = false
        composeRule.setContent {
            ConClaseDeAncho {
                var abierta by remember { mutableStateOf(true) }
                Box(Modifier.fillMaxSize()) {
                    if (abierta) {
                        MarcoDeHoja(onDismiss = { cerrada = true; abierta = false }, dismissEnabled = dismissEnabled) {
                            Text("Contenido de la hoja", Modifier.height(200.dp))
                        }
                    }
                }
            }
        }
        composeRule.waitForIdle()
    }

    @Test
    @Config(qualifiers = "w390dp-h800dp-xhdpi")
    fun `a 390 dp la hoja va pegada abajo y de ancho completo`() {
        montarHoja()

        val panel = limites(TAG_PANEL_DE_HOJA)
        assertEquals(raiz().bottom, panel.bottom)
        assertEquals(0f, panel.left)
        assertEquals(raiz().right, panel.right)
    }

    @Test
    @Config(qualifiers = "w1280dp-h800dp-mdpi")
    fun `a 1280 dp la hoja queda centrada y mide a lo sumo 560`() {
        montarHoja()

        val panel = limites(TAG_PANEL_DE_HOJA)
        val raiz = raiz()
        assertEquals(560f, enDp(panel.width), 0.5f)
        assertTrue(abs(panel.left - (raiz.right - panel.right)) <= 1f, "centrada a lo ancho")
        assertTrue(abs(panel.top - (raiz.bottom - panel.bottom)) <= 1f, "centrada a lo alto")
        // Solo la X: no hay nada que arrastrar.
        composeRule.onNodeWithContentDescription("Cerrar", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "w1280dp-h800dp-mdpi")
    fun `a 1280 dp Escape cierra la hoja`() {
        montarHoja()

        composeRule.onRoot().performKeyInput { pressKey(Key.Escape) }
        composeRule.waitForIdle()

        assertTrue(cerrada, "Escape tenía que cerrar la hoja")
        composeRule.onNodeWithTag(TAG_PANEL_DE_HOJA, useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = "w1280dp-h800dp-mdpi")
    fun `Escape respeta que la hoja no se pueda cerrar mientras guarda`() {
        montarHoja(dismissEnabled = false)

        composeRule.onRoot().performKeyInput { pressKey(Key.Escape) }
        composeRule.waitForIdle()

        assertFalse(cerrada)
        composeRule.onNodeWithTag(TAG_PANEL_DE_HOJA, useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "w768dp-h600dp-mdpi")
    fun `en una ventana baja la hoja no pasa del 90 por ciento del alto`() {
        cerrada = false
        composeRule.setContent {
            ConClaseDeAncho {
                MarcoDeHoja(onDismiss = {}) {
                    Text("Muy alta", Modifier.height(2_000.dp))
                }
            }
        }
        composeRule.waitForIdle()

        val panel = limites(TAG_PANEL_DE_HOJA)
        assertTrue(panel.height <= raiz().height * 0.9f + 1f, "la hoja mide ${enDp(panel.height)} dp")
    }

    // ── La hoja en la cáscara: velo de borde a borde y Escape por capas (fix W1) ─────

    private var pestanaElegida: NavTab? = null

    /** El esqueleto real con una hoja abierta desde adentro de la pantalla, como en la app. */
    private fun montarHojaEnLaCascara(contenidoDeLaHoja: @Composable (cerrar: () -> Unit) -> Unit = { Text("Hoja") }) {
        cerrada = false
        pestanaElegida = null
        composeRule.setContent {
            ConClaseDeAncho {
                var abierta by remember { mutableStateOf(true) }
                EsqueletoDeLaCascara(
                    pantalla = Screen.Accounts,
                    activeTab = NavTab.PATRIMONIO,
                    conNavegacion = true,
                    onTabSelected = { pestanaElegida = it },
                    relevoDeScroll = remember { RelevoDeScroll() },
                ) {
                    Box(Modifier.fillMaxSize().testTag("contenido"))
                    if (abierta) {
                        val cerrar = { cerrada = true; abierta = false }
                        MarcoDeHoja(onDismiss = cerrar) { contenidoDeLaHoja(cerrar) }
                    }
                }
            }
        }
        composeRule.waitForIdle()
    }

    @Test
    @Config(qualifiers = "w1280dp-h800dp-mdpi")
    fun `a 1280 dp el velo cubre la ventana entera, rail incluido, y la hoja se centra en ella`() {
        montarHojaEnLaCascara()

        assertEquals(raiz(), limites(TAG_VELO_DE_HOJA), "el velo va de borde a borde, no solo sobre la columna")
        val panel = limites(TAG_PANEL_DE_HOJA)
        assertTrue(abs(panel.left - (raiz().right - panel.right)) <= 1f, "centrada en la ventana")
    }

    @Test
    @Config(qualifiers = "w768dp-h1024dp-mdpi")
    fun `a 768 dp el rail queda bajo el velo y tocarlo no elige una pestana`() {
        montarHojaEnLaCascara()

        assertEquals(raiz(), limites(TAG_VELO_DE_HOJA))
        // Donde está el primer ítem del rail compacto (el «+» ocupa los primeros ~72 dp).
        val hoy = composeRule.onNodeWithContentDescription("Hoy", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        composeRule.onRoot().performTouchInput { click(hoy.center) }
        composeRule.waitForIdle()

        assertEquals(null, pestanaElegida, "con la hoja abierta el rail no navega")
        assertTrue(cerrada, "tocar afuera —el rail también es afuera— cierra la hoja, como el velo")
    }

    /**
     * I1: con un sub-editor abierto y el foco en SU campo, Escape cierra el sub-editor y no la hoja
     * (el campo no se traga la tecla: el velo la ve antes, en el preview). El segundo Escape —ya sin
     * campo, así que el foco volvió al velo— cierra la hoja.
     */
    @Test
    @Config(qualifiers = "w1280dp-h800dp-mdpi")
    fun `Escape en un sub-editor lo cierra sin cerrar la hoja, y el segundo cierra la hoja`() {
        cerrada = false
        composeRule.setContent {
            ConClaseDeAncho {
                var abierta by remember { mutableStateOf(true) }
                var subEditor by remember { mutableStateOf(true) }
                if (abierta) {
                    MarcoDeHoja(
                        onDismiss = { cerrada = true; abierta = false },
                        onEscape = { if (subEditor) subEditor = false else { cerrada = true; abierta = false } },
                    ) {
                        if (subEditor) {
                            val foco = remember { FocusRequester() }
                            var texto by remember { mutableStateOf("Nómina") }
                            LaunchedEffect(Unit) { foco.requestFocus() }
                            BasicTextField(texto, { texto = it }, Modifier.focusRequester(foco).testTag("campo"))
                        } else {
                            Text("Editor principal")
                        }
                    }
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("campo").assertIsFocused()

        composeRule.onNodeWithTag("campo").performKeyInput { pressKey(Key.Escape) }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("campo").assertDoesNotExist()
        composeRule.onNodeWithText("Editor principal").assertIsDisplayed()
        assertFalse(cerrada, "el primer Escape cierra el sub-editor, no la hoja")

        composeRule.onRoot().performKeyInput { pressKey(Key.Escape) }
        composeRule.waitForIdle()

        assertTrue(cerrada, "el segundo Escape cierra la hoja")
    }

    /** m4: dos hojas apiladas. Escape cierra la de arriba y la de abajo retoma el foco. */
    @Test
    @Config(qualifiers = "w1280dp-h800dp-mdpi")
    fun `con dos hojas apiladas Escape cierra una por vez`() {
        var abajo by mutableStateOf(true)
        var arriba by mutableStateOf(true)
        composeRule.setContent {
            ConClaseDeAncho {
                EsqueletoDeLaCascara(
                    pantalla = Screen.Accounts,
                    activeTab = NavTab.PATRIMONIO,
                    conNavegacion = true,
                    onTabSelected = {},
                    relevoDeScroll = remember { RelevoDeScroll() },
                ) {
                    if (abajo) MarcoDeHoja(onDismiss = { abajo = false }) { Text("La de abajo") }
                    if (arriba) MarcoDeHoja(onDismiss = { arriba = false }) { Text("La de arriba") }
                }
            }
        }
        composeRule.waitForIdle()

        composeRule.onRoot().performKeyInput { pressKey(Key.Escape) }
        composeRule.waitForIdle()
        assertFalse(arriba, "el primer Escape cierra la de arriba")
        assertTrue(abajo, "y deja la de abajo")

        composeRule.onRoot().performKeyInput { pressKey(Key.Escape) }
        composeRule.waitForIdle()
        assertFalse(abajo, "el segundo Escape le llega a la de abajo y la cierra")
    }
}
