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
 * # Las cuatro pestañas, en el teléfono y en la web (ola C)
 *
 * «Hoy · Movimientos · + · Plan · Patrimonio» en la barra del teléfono y las mismas cuatro más
 * «Agregar» en el rail de la web — sin «Más», Créditos ni Presupuestos. Y el avatar, que era la
 * puerta de Perfil, ahora abre Ajustes.
 *
 * `@GraphicsMode(NATIVE)` y `sdk = [34]`: que «Movimientos» entre entero a 390 dp se mide con el
 * motor de texto real (ver `CreditosNoAfirmanMientrasCarganTest`), y con la letra 12 % más grande
 * que App.kt le pone a toda la app — medirlo sin eso sería medir otra app.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w390dp-h800dp-xhdpi")
class LasCuatroPestanasTest {

    @get:Rule val composeRule = createComposeRule()

    private val rotulos = listOf("Hoy", "Movimientos", "Plan", "Patrimonio")
    private val loQueYaNoEsPestana = listOf("Más", "Inicio", "Movs", "Cuentas", "Créditos", "Presupuestos")

    private var elegida: NavTab? = null

    /** La barra del teléfono como la pinta App.kt: al pie, con la letra de la app. */
    private fun montarBarra(activa: NavTab? = NavTab.HOY, letraDelSistema: Float = 1f) {
        composeRule.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(base.density, base.fontScale * letraDelSistema * 1.12f),
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

    private fun montarRail() {
        composeRule.setContent {
            CompositionLocalProvider(LocalWindowWidthClass provides WindowWidthClass.Expanded) {
                MoviTheme { MinNavRail(active = NavTab.PLAN, onTabSelected = { elegida = it }) }
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
    fun `la barra pinta las cuatro pestanas y el boton de agregar, y no Mas`() {
        montarBarra()

        rotulos.forEach { composeRule.onNodeWithText(it, useUnmergedTree = true).assertIsDisplayed() }
        composeRule.onNodeWithContentDescription("Agregar", useUnmergedTree = true).assertIsDisplayed()
        loQueYaNoEsPestana.forEach { composeRule.onNodeWithText(it, useUnmergedTree = true).assertDoesNotExist() }
    }

    /**
     * «Movimientos» va entero, no «Movs»: entra a 390 dp junto a las otras tres y el «+», en un solo
     * renglón y sin recortarse. Se miran los cuatro rótulos porque si la fila no entrara, el que se
     * recorta es el último (la `Row` le da a cada uno lo que sobra), no necesariamente el largo.
     */
    @Test
    fun `Movimientos entra entero a 390 dp junto a las otras`() {
        montarBarra()

        rotulos.forEach { rotulo ->
            val texto = layoutDelTexto(rotulo)
            // `maxLines = 1`: un rótulo que no entrara se partiría en dos y el segundo renglón se
            // cortaría — eso es `didExceedMaxLines`. (No `hasVisualOverflow`: con la medida de
            // semántica compara contra el ancho máximo de la fila, no contra el del rótulo.)
            assertEquals(1, texto.lineCount, "«$rotulo» en un renglón")
            assertFalse(texto.multiParagraph.didExceedMaxLines, "«$rotulo» sin partirse")
            // Y el nodo mide al menos lo que mide el texto: la fila no lo apretó.
            val anchoDelTexto = texto.getLineRight(0) - texto.getLineLeft(0)
            val anchoDelNodo = composeRule.onNodeWithText(rotulo, useUnmergedTree = true)
                .fetchSemanticsNode().boundsInRoot.width
            assertTrue(anchoDelNodo + 0.5f >= anchoDelTexto, "«$rotulo» entero: $anchoDelNodo < $anchoDelTexto")
        }
        val ancho = composeRule.onRoot().fetchSemanticsNode().boundsInRoot.right
        val patrimonio = composeRule.onNodeWithText("Patrimonio", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        assertTrue(patrimonio.right <= ancho, "el último rótulo termina adentro de la pantalla")
    }

    /**
     * **Con la letra del sistema más grande (1,3×, y encima el 1,12× de la app) ningún rótulo sale
     * cortado.** Con `maxLines = 1`, una palabra que no entra se parte por la mitad y se ve
     * «Movimie». Medido: a 390 dp «Movimientos» ocupa ~95 dp y la fila entera ~323 dp, así que entra
     * entero y no hace falta volver a «Movs». Si algún día deja de entrar, esta prueba lo dice.
     */
    @Test
    fun `con la letra del sistema al 130 por ciento ningun rotulo sale cortado`() {
        montarBarra(letraDelSistema = 1.3f)

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
        val patrimonio = composeRule.onNodeWithText("Patrimonio", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        assertTrue(patrimonio.right <= ancho, "el último rótulo termina adentro de la pantalla")
    }

    @Test
    fun `tocar una pestana la elige`() {
        montarBarra()

        composeRule.onNodeWithText("Plan", useUnmergedTree = true).performClick()
        assertEquals(NavTab.PLAN, elegida)
        composeRule.onNodeWithText("Patrimonio", useUnmergedTree = true).performClick()
        assertEquals(NavTab.PATRIMONIO, elegida)
        composeRule.onNodeWithContentDescription("Agregar", useUnmergedTree = true).performClick()
        assertEquals(NavTab.ADD, elegida)
    }

    /** En Ajustes no hay pestaña marcada, pero la barra sigue ahí con sus cuatro. */
    @Test
    fun `sin pestana activa la barra se pinta igual`() {
        montarBarra(activa = null)

        rotulos.forEach { composeRule.onNodeWithText(it, useUnmergedTree = true).assertIsDisplayed() }
    }

    // ── El rail de la web ─────────────────────────────────────────────────────

    @Test
    fun `el rail pinta las mismas cuatro y Agregar, sin Creditos ni Presupuestos ni Mas`() {
        montarRail()

        rotulos.forEach { composeRule.onNodeWithText(it, useUnmergedTree = true).assertIsDisplayed() }
        composeRule.onNodeWithText("Agregar", useUnmergedTree = true).assertIsDisplayed()
        loQueYaNoEsPestana.forEach { composeRule.onNodeWithText(it, useUnmergedTree = true).assertDoesNotExist() }
        assertEquals(4, destinosPrincipales.size)
    }

    @Test
    fun `tocar una entrada del rail la elige`() {
        montarRail()

        composeRule.onNodeWithText("Movimientos", useUnmergedTree = true).performClick()
        assertEquals(NavTab.MOVIMIENTOS, elegida)
        composeRule.onNodeWithText("Agregar", useUnmergedTree = true).performClick()
        assertEquals(NavTab.ADD, elegida)
    }

    // ── El avatar abre Ajustes ────────────────────────────────────────────────

    @Test
    fun `el avatar abre Ajustes, no Perfil`() {
        SessionManager.save(token = "tok", userId = "u1", name = "Juan", email = "juan@ejemplo.com")
        var navegoA: Screen? = null
        composeRule.setContent {
            MoviTheme { MinScreenHeader(title = "Plan", leading = HeaderLeading.Avatar { navegoA = it }) }
        }

        composeRule.onNodeWithText("J", useUnmergedTree = true).performClick()

        assertEquals(Screen.Mas, navegoA)
    }

    /**
     * Solo la pantalla principal de una pestaña lleva avatar. Presupuestos suelto y Créditos se
     * abren desde Plan y desde Patrimonio, así que llevan flecha — en el teléfono y en la web por
     * igual, ahora que las dos muestran las mismas pestañas.
     */
    @Test
    fun `avatar solo en la pantalla principal de cada pestana`() {
        val ir: (Screen) -> Unit = {}
        listOf(Screen.Dashboard, Screen.Transactions(), Screen.Plan(), Screen.Accounts).forEach {
            assertIs<HeaderLeading.Avatar>(leadingFor(it, ir, fallback = Screen.Dashboard), "$it")
        }
        assertEquals(
            HeaderLeading.Back(Screen.Plan(SEGMENTO_PRESUPUESTOS)),
            leadingFor(Screen.Budgets, ir, fallback = Screen.Plan(SEGMENTO_PRESUPUESTOS)),
        )
        assertEquals(HeaderLeading.Back(Screen.Accounts), leadingFor(Screen.Credits, ir, fallback = Screen.Accounts))
        assertEquals(HeaderLeading.Back(Screen.Mas), leadingFor(Screen.Categorias, ir, fallback = Screen.Mas))
    }
}
