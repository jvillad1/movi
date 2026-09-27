package com.jvillada.movi

import androidx.compose.foundation.layout.Box
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
        // La barra inferior dice «Movimientos» entero; el rail compacto diría «Movs».
        composeRule.onNodeWithText("Movimientos", useUnmergedTree = true).assertIsDisplayed()
        assertFalse(hay("Movs"), "no hay rail compacto en el teléfono")
        val contenido = limites("contenido")
        assertEquals(0f, contenido.left)
        assertEquals(raiz().right, contenido.right)
    }

    @Test
    @Config(qualifiers = "w768dp-h1024dp-mdpi")
    fun `a 768 dp rail compacto y ninguna barra inferior`() {
        montar()

        assertEquals(WindowWidthClass.Medium, clase)
        destinosPrincipales.forEach {
            composeRule.onNodeWithText(it.rotuloCorto, useUnmergedTree = true).assertIsDisplayed()
        }
        composeRule.onNodeWithContentDescription("Agregar", useUnmergedTree = true).assertIsDisplayed()
        // Ni la barra del teléfono («Movimientos» entero) ni el rail ancho («Agregar» escrito).
        assertFalse(hay("Movimientos"), "la barra inferior es solo del teléfono")
        assertFalse(hay("Agregar"), "el rail ancho es del escritorio")

        val contenido = limites("contenido")
        assertEquals(80f, enDp(contenido.left), 0.5f, "el contenido empieza donde termina el rail de 80 dp")
        // 768 − 80 = 688 < 720: la columna de lectura llena lo que queda.
        assertEquals(688f, enDp(contenido.width), 0.5f)
    }

    @Test
    @Config(qualifiers = "w768dp-h1024dp-mdpi")
    fun `los rotulos cortos del rail entran enteros en un renglon`() {
        montar()

        destinosPrincipales.forEach { dest ->
            val nodo = composeRule.onNodeWithText(dest.rotuloCorto, useUnmergedTree = true).fetchSemanticsNode()
            val resultados = mutableListOf<TextLayoutResult>()
            nodo.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(resultados)
            val texto = resultados.single()
            assertEquals(1, texto.lineCount, "«${dest.rotuloCorto}» en un renglón")
            assertFalse(texto.multiParagraph.didExceedMaxLines, "«${dest.rotuloCorto}» sin partirse")
            assertTrue(enDp(nodo.boundsInRoot.right) <= 80.5f, "«${dest.rotuloCorto}» adentro del rail")
        }
    }

    @Test
    @Config(qualifiers = "w1280dp-h800dp-mdpi")
    fun `a 1280 dp la pantalla de lectura mide 720 y se centra a la derecha del rail`() {
        montar()

        assertEquals(WindowWidthClass.Expanded, clase)
        composeRule.onNodeWithText("Agregar", useUnmergedTree = true).assertIsDisplayed()
        assertFalse(hay("Movs"))

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
}
