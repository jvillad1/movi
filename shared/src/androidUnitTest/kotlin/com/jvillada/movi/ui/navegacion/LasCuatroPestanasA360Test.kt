package com.jvillada.movi.ui.navegacion

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import com.jvillada.movi.theme.Medida
import com.jvillada.movi.theme.MoviTheme
import com.jvillada.movi.ui.components.LocalWindowWidthClass
import com.jvillada.movi.ui.components.MinBottomNav
import com.jvillada.movi.ui.components.NavTab
import com.jvillada.movi.ui.components.TAG_BARRA_DE_PESTANAS
import com.jvillada.movi.ui.components.WindowWidthClass
import com.jvillada.movi.ui.components.destinosPrincipales
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * # Las cuatro pestañas a 360 dp (vuelta 38)
 *
 * «Inicio · Actividad · Pagos · Tú», sin «+». Con cinco pestañas y el «+» (R3) a 360 dp solo entraba el
 * rótulo de la activa; con cuatro, **las cuatro llevan su rótulo**, entero y en un renglón, también
 * con la letra del sistema al 130 %. Medido con el motor de texto real y la letra 12 % más grande que
 * App.kt le pone a toda la app.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h800dp-xhdpi")
class LasCuatroPestanasA360Test {

    @get:Rule val composeRule = createComposeRule()

    private var elegida: NavTab? = null

    private val rotulos = listOf("Inicio", "Actividad", "Pagos", "Tú")

    private fun montar(activa: NavTab, letraDelSistema: Float = 1f) {
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

    private fun layoutDelTexto(texto: String): TextLayoutResult {
        val nodo = composeRule.onNodeWithText(texto, useUnmergedTree = true).fetchSemanticsNode()
        val resultados = mutableListOf<TextLayoutResult>()
        nodo.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(resultados)
        return resultados.single()
    }

    private fun lasCuatroEnterasYAdentro() {
        val ancho = composeRule.onRoot().fetchSemanticsNode().boundsInRoot.right
        rotulos.forEach { rotulo ->
            composeRule.onNodeWithText(rotulo, useUnmergedTree = true).assertIsDisplayed()
            val texto = layoutDelTexto(rotulo)
            assertEquals(1, texto.lineCount, "«$rotulo» en un renglón")
            assertFalse(texto.multiParagraph.didExceedMaxLines, "«$rotulo» sin partirse")
            val nodo = composeRule.onNodeWithText(rotulo, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            assertTrue(nodo.width + 0.5f >= texto.getLineRight(0) - texto.getLineLeft(0), "«$rotulo» entero")
            assertTrue(nodo.right <= ancho, "«$rotulo» termina adentro de la pantalla")
        }
    }

    @Test
    fun `a 360 dp las cuatro llevan su rotulo entero, sin mas`() {
        montar(NavTab.TU)

        assertEquals(4, destinosPrincipales.size)
        lasCuatroEnterasYAdentro()
        composeRule.onNodeWithContentDescription("Agregar", useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNodeWithText("Agregar", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun `con la letra del sistema al 130 por ciento tambien entran las cuatro`() {
        montar(NavTab.HOY, letraDelSistema = 1.3f)
        lasCuatroEnterasYAdentro()
    }

    @Test
    fun `la barra mide 76 y la activa esta marcada`() {
        montar(NavTab.PLAN)

        val barra = composeRule.onNodeWithTag(TAG_BARRA_DE_PESTANAS, useUnmergedTree = true).fetchSemanticsNode()
        val densidad = composeRule.density.density
        assertEquals(Medida.altoDeBarraDePestanas.value, barra.boundsInRoot.height / densidad, 0.5f)

        // La pestaña elegida lo dice (el lector de pantalla y la píldora salen del mismo `isActive`).
        val seleccionadas = rotulos.filter { rotulo ->
            val nodo = composeRule.onNodeWithText(rotulo, useUnmergedTree = true).fetchSemanticsNode()
            generateSequence(nodo) { it.parent }.any { it.config.getOrNull(SemanticsProperties.Selected) == true }
        }
        assertEquals(listOf("Pagos"), seleccionadas)
    }

    @Test
    fun `tocar cada pestana la elige`() {
        montar(NavTab.HOY)
        listOf("Actividad" to NavTab.MOVIMIENTOS, "Pagos" to NavTab.PLAN, "Tú" to NavTab.TU, "Inicio" to NavTab.HOY)
            .forEach { (rotulo, tab) ->
                composeRule.onNodeWithText(rotulo, useUnmergedTree = true).performClick()
                assertEquals(tab, elegida, rotulo)
            }
    }
}
