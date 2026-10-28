package com.jvillada.movi.ui.navegacion

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import com.jvillada.movi.data.SessionManager
import com.jvillada.movi.theme.MoviTheme
import com.jvillada.movi.ui.Screen
import com.jvillada.movi.ui.components.HeaderLeading
import com.jvillada.movi.ui.components.LocalWindowWidthClass
import com.jvillada.movi.ui.components.MinBottomNav
import com.jvillada.movi.ui.components.MinNavRail
import com.jvillada.movi.ui.components.MinScreenHeader
import com.jvillada.movi.ui.components.NavTab
import com.jvillada.movi.ui.components.WindowWidthClass
import com.jvillada.movi.ui.components.destinoMoviAi
import com.jvillada.movi.ui.components.destinosDelRail
import com.jvillada.movi.ui.components.destinosPrincipales
import com.jvillada.movi.ui.components.leadingFor
import com.jvillada.movi.ui.plan.SEGMENTO_PRESUPUESTOS
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * # Las cuatro pestañas, en el teléfono y en la web (vuelta 38, la opción 3)
 *
 * «Inicio · Actividad · Pagos · Tú» en la barra del teléfono —sin «+»— y las mismas cuatro más Movi AI
 * y «Agregar» en el rail de la web. Patrimonio, Espacios (Personas) y Ajustes viven en «Tú», y el
 * avatar lleva ahí. (Ola C fijó la regla de las cuatro, las mismas en los dos.)
 *
 * `@GraphicsMode(NATIVE)` y `sdk = [34]`: que los rótulos entren enteros se mide con el motor de texto
 * real, y con la letra 12 % más grande que App.kt le pone a toda la app.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w390dp-h800dp-xhdpi")
class LasCuatroPestanasTest {

    @get:Rule val composeRule = createComposeRule()

    private val rotulos = listOf("Inicio", "Actividad", "Pagos", "Tú")
    // Lo que ya no es pestaña: las de antes de la vuelta 38 («Movimientos», «Plan», «Espacios»,
    // «Patrimonio») y las de antes de la ola C.
    private val loQueYaNoEsPestana = listOf("Movimientos", "Plan", "Espacios", "Patrimonio", "Más", "Hoy", "Movs", "Cuentas", "Créditos", "Presupuestos", "Ajustes")

    private var elegida: NavTab? = null

    /** La barra del teléfono como la pinta App.kt: al pie, con la letra de la app. */
    private fun montarBarra(activa: NavTab? = NavTab.HOY) {
        composeRule.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(base.density, base.fontScale * 1.12f),
                LocalWindowWidthClass provides WindowWidthClass.Compact,
            ) {
                MoviTheme {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                        MinBottomNav(active = activa, onTabSelected = { elegida = it })
                    }
                }
            }
        }
    }

    private fun montarRail(compacto: Boolean = false, activa: NavTab = NavTab.PLAN) {
        composeRule.setContent {
            val clase = if (compacto) WindowWidthClass.Medium else WindowWidthClass.Expanded
            CompositionLocalProvider(LocalWindowWidthClass provides clase) {
                MoviTheme { MinNavRail(active = activa, onTabSelected = { elegida = it }, compacto = compacto) }
            }
        }
    }

    private fun layoutDelTexto(texto: String): TextLayoutResult {
        val nodo = composeRule.onNodeWithText(texto, useUnmergedTree = true).fetchSemanticsNode()
        val resultados = mutableListOf<TextLayoutResult>()
        nodo.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(resultados)
        return resultados.single()
    }

    // ── La barra del teléfono ─────────────────────────────────────────────────

    @Test
    fun `la barra pinta las cuatro pestanas, sin mas ni las de antes`() {
        montarBarra()

        rotulos.forEach { composeRule.onNodeWithText(it, useUnmergedTree = true).assertIsDisplayed() }
        composeRule.onNodeWithContentDescription("Agregar", useUnmergedTree = true).assertDoesNotExist()
        loQueYaNoEsPestana.forEach { composeRule.onNodeWithText(it, useUnmergedTree = true).assertDoesNotExist() }
    }

    /** **Movi AI es del rail, no de la barra del teléfono.** En el teléfono se abre desde Inicio y desde Tú. */
    @Test
    fun `la barra del telefono no tiene a Movi AI`() {
        montarBarra()

        composeRule.onNodeWithText(destinoMoviAi.label, useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNodeWithContentDescription(destinoMoviAi.label, useUnmergedTree = true).assertDoesNotExist()
    }

    /** Los cuatro rótulos entran enteros a 390 dp, en un renglón (a 360, `LasCuatroPestanasA360Test`). */
    @Test
    fun `los cuatro rotulos entran enteros a 390 dp`() {
        montarBarra()

        rotulos.forEach { rotulo ->
            val texto = layoutDelTexto(rotulo)
            assertEquals(1, texto.lineCount, "«$rotulo» en un renglón")
            assertFalse(texto.multiParagraph.didExceedMaxLines, "«$rotulo» sin partirse")
            val anchoDelTexto = texto.getLineRight(0) - texto.getLineLeft(0)
            val anchoDelNodo = composeRule.onNodeWithText(rotulo, useUnmergedTree = true)
                .fetchSemanticsNode().boundsInRoot.width
            assertTrue(anchoDelNodo + 0.5f >= anchoDelTexto, "«$rotulo» entero: $anchoDelNodo < $anchoDelTexto")
        }
        val ancho = composeRule.onRoot().fetchSemanticsNode().boundsInRoot.right
        val tu = composeRule.onNodeWithText("Tú", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertTrue(tu.right <= ancho, "el último rótulo termina adentro de la pantalla")
    }

    @Test
    fun `tocar una pestana la elige`() {
        montarBarra()

        composeRule.onNodeWithText("Pagos", useUnmergedTree = true).performClick()
        assertEquals(NavTab.PLAN, elegida)
        composeRule.onNodeWithText("Tú", useUnmergedTree = true).performClick()
        assertEquals(NavTab.TU, elegida)
        composeRule.onNodeWithText("Actividad", useUnmergedTree = true).performClick()
        assertEquals(NavTab.MOVIMIENTOS, elegida)
    }

    /** Con Movi AI abierta en el teléfono no hay pestaña marcada, pero la barra sigue ahí con sus cuatro. */
    @Test
    fun `sin pestana activa la barra se pinta igual`() {
        montarBarra(activa = null)

        rotulos.forEach { composeRule.onNodeWithText(it, useUnmergedTree = true).assertIsDisplayed() }
    }

    // ── El rail de la web ─────────────────────────────────────────────────────

    @Test
    fun `el rail pinta las mismas cuatro, Movi AI y Agregar, sin las de antes`() {
        montarRail()

        rotulos.forEach { composeRule.onNodeWithText(it, useUnmergedTree = true).assertIsDisplayed() }
        composeRule.onNodeWithText("Movi AI", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("Agregar", useUnmergedTree = true).assertIsDisplayed()
        loQueYaNoEsPestana.forEach { composeRule.onNodeWithText(it, useUnmergedTree = true).assertDoesNotExist() }
        assertEquals(4, destinosPrincipales.size)
        assertEquals(destinosPrincipales + destinoMoviAi, destinosDelRail)
    }

    @Test
    fun `tocar Movi AI en el rail lo elige, ancho y compacto`() {
        montarRail()
        composeRule.onNodeWithText("Movi AI", useUnmergedTree = true).performClick()
        assertEquals(NavTab.MOVI_AI, elegida)
    }

    @Test
    fun `el rail compacto tambien tiene a Movi AI`() {
        montarRail(compacto = true)

        composeRule.onNodeWithContentDescription("Movi AI", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Movi AI", useUnmergedTree = true).performClick()
        assertEquals(NavTab.MOVI_AI, elegida)
    }

    @Test
    fun `tocar una entrada del rail la elige`() {
        montarRail()

        composeRule.onNodeWithText("Actividad", useUnmergedTree = true).performClick()
        assertEquals(NavTab.MOVIMIENTOS, elegida)
        composeRule.onNodeWithText("Tú", useUnmergedTree = true).performClick()
        assertEquals(NavTab.TU, elegida)
        composeRule.onNodeWithText("Agregar", useUnmergedTree = true).performClick()
        assertEquals(NavTab.ADD, elegida)
    }

    // ── El avatar abre Tú ─────────────────────────────────────────────────────

    @Test
    fun `el avatar abre Tu`() {
        SessionManager.save(token = "tok", userId = "u1", name = "Juan", email = "juan@ejemplo.com")
        var navegoA: Screen? = null
        composeRule.setContent {
            MoviTheme { MinScreenHeader(title = "Pagos", leading = HeaderLeading.Avatar { navegoA = it }) }
        }

        composeRule.onNodeWithText("J", useUnmergedTree = true).performClick()

        assertEquals(Screen.Tu, navegoA)
    }

    /**
     * Solo la pantalla principal de una pestaña lleva avatar; «Tú» no lleva nada (sería ella misma).
     * Presupuestos suelto, Créditos, Patrimonio y Personas se abren desde Pagos y desde Tú, así que
     * llevan flecha — en el teléfono y en la web por igual.
     */
    @Test
    fun `avatar solo en la pantalla principal de cada pestana`() {
        val ir: (Screen) -> Unit = {}
        listOf(Screen.Dashboard, Screen.Transactions(), Screen.Plan()).forEach {
            assertIs<HeaderLeading.Avatar>(leadingFor(it, ir, fallback = Screen.Dashboard), "$it")
        }
        assertEquals(HeaderLeading.Ninguno, leadingFor(Screen.Tu, ir, fallback = Screen.Dashboard))
        assertEquals(
            HeaderLeading.Back(Screen.Plan(SEGMENTO_PRESUPUESTOS)),
            leadingFor(Screen.Budgets, ir, fallback = Screen.Plan(SEGMENTO_PRESUPUESTOS)),
        )
        assertEquals(HeaderLeading.Back(Screen.Accounts), leadingFor(Screen.Credits, ir, fallback = Screen.Accounts))
        assertEquals(HeaderLeading.Back(Screen.Tu), leadingFor(Screen.Accounts, ir, fallback = Screen.Tu))
        assertEquals(HeaderLeading.Back(Screen.Tu), leadingFor(Screen.Espacios, ir, fallback = Screen.Tu))
        assertEquals(HeaderLeading.Back(Screen.Tu), leadingFor(Screen.Categorias, ir, fallback = Screen.Tu))
    }

    // ── El rail compacto de una ventana mediana (Ola W1) ──────────────────────

    /**
     * Entre 600 y 999 dp ni la barra del teléfono ni el rail de 216 dp: un rail de 80 dp con las
     * mismas cuatro pestañas, con su rótulo, y el «+» arriba.
     */
    @Test
    fun `el rail compacto pinta las mismas cuatro con su rotulo y el boton de agregar`() {
        montarRail(compacto = true)

        destinosPrincipales.forEach {
            composeRule.onNodeWithContentDescription(it.label, useUnmergedTree = true).assertIsDisplayed()
        }
        composeRule.onNodeWithContentDescription("Agregar", useUnmergedTree = true).assertIsDisplayed()
        loQueYaNoEsPestana.forEach { composeRule.onNodeWithText(it, useUnmergedTree = true).assertDoesNotExist() }
    }

    @Test
    fun `tocar una entrada del rail compacto la elige, y el mas abre Agregar`() {
        montarRail(compacto = true)

        composeRule.onNodeWithContentDescription("Actividad", useUnmergedTree = true).performClick()
        assertEquals(NavTab.MOVIMIENTOS, elegida)
        composeRule.onNodeWithContentDescription("Agregar", useUnmergedTree = true).performClick()
        assertEquals(NavTab.ADD, elegida)
    }

    /** La barra del teléfono no se pinta desde 600 dp: ahí manda el rail, aunque sea el compacto. */
    @Test
    fun `la barra inferior no se pinta en una ventana mediana`() {
        composeRule.setContent {
            CompositionLocalProvider(LocalWindowWidthClass provides WindowWidthClass.Medium) {
                MoviTheme { MinBottomNav(active = NavTab.HOY, onTabSelected = {}) }
            }
        }

        rotulos.forEach { composeRule.onNodeWithText(it, useUnmergedTree = true).assertDoesNotExist() }
    }
}
