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
import androidx.compose.ui.unit.dp
import com.jvillada.movi.theme.MoviTheme
import com.jvillada.movi.ui.components.LocalWindowWidthClass
import com.jvillada.movi.ui.components.MinBottomNav
import com.jvillada.movi.ui.components.NavTab
import com.jvillada.movi.ui.components.WindowWidthClass
import com.jvillada.movi.ui.components.rotulosQueEntran
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
 * # Las cinco pestañas a 360 dp (R3)
 *
 * «Inicio · Movimientos · + · Plan · Espacios · Patrimonio». Medido con el motor de texto real: a
 * 390 dp entran las cinco con su rótulo (`LasCuatroPestanasTest`); a 360 dp no (hacen falta ~377).
 * La decisión: **ningún rótulo se achica** por debajo del token — se ve el de la pestaña activa y las
 * demás quedan con su ícono, que el lector de pantalla sigue nombrando.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h800dp-xhdpi")
class LasCincoPestanasA360Test {

    @get:Rule val composeRule = createComposeRule()

    private var elegida: NavTab? = null

    private fun montar(activa: NavTab) {
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

    private fun layoutDelTexto(texto: String): TextLayoutResult {
        val nodo = composeRule.onNodeWithText(texto, useUnmergedTree = true).fetchSemanticsNode()
        val resultados = mutableListOf<TextLayoutResult>()
        nodo.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(resultados)
        return resultados.single()
    }

    @Test
    fun `a 360 dp se ve entero el rotulo de la activa y las cinco se pueden tocar`() {
        montar(NavTab.PATRIMONIO)

        val texto = layoutDelTexto("Patrimonio")
        assertEquals(1, texto.lineCount)
        assertFalse(texto.multiParagraph.didExceedMaxLines, "«Patrimonio» sin partirse")
        val ancho = composeRule.onRoot().fetchSemanticsNode().boundsInRoot.right
        val nodo = composeRule.onNodeWithText("Patrimonio", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertTrue(nodo.width + 0.5f >= texto.getLineRight(0) - texto.getLineLeft(0), "«Patrimonio» entero")
        assertTrue(nodo.right <= ancho, "termina adentro de la pantalla")

        listOf("Hoy", "Movimientos", "Plan", "Espacios").forEach {
            composeRule.onNodeWithText(it, useUnmergedTree = true).assertDoesNotExist()
            composeRule.onNodeWithContentDescription(it, useUnmergedTree = true).assertIsDisplayed()
        }
        composeRule.onNodeWithContentDescription("Espacios", useUnmergedTree = true).performClick()
        assertEquals(NavTab.ESPACIOS, elegida)
    }

    @Test
    fun `el umbral - cinco rotulos medidos entran en 390 y no en 360`() {
        // Los anchos medidos en este emulador (1,12×): Hoy, Movimientos, Plan, Espacios, Patrimonio.
        val medidos = listOf(23.dp, 74.dp, 25.dp, 52.dp, 66.dp)
        assertTrue(rotulosQueEntran(medidos, 390.dp))
        assertFalse(rotulosQueEntran(medidos, 360.dp))
    }
}
