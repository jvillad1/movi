package com.jvillada.movi.ui.recurrentes

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import com.jvillada.movi.theme.MoviTheme
import com.jvillada.movi.ui.dashboard.PagoDelPeriodo
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
 * # La pastilla de vencimiento cabe entera a 360 dp (revisión de la vuelta 40)
 *
 * Al lado de «Anotar pago», a 360 dp, «Venció hace 23 días» se cortaba en «Venció hace 23». La
 * pastilla bajó a su renglón, a todo el ancho desde el texto de la fila: se mide con el motor de
 * texto real y la escala de letra de la app (×1,12), que es lo que se ve.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h800dp-xhdpi")
class LaPastillaCabeEnteraTest {

    @get:Rule val composeRule = createComposeRule()

    private val vencido = PagoDelPeriodo(
        ruleId = "rr_arriendo", nombre = "Arriendo del apartamento", monto = 1_850_000, pagado = false,
        diasParaVencer = -23, vence = "2026-10-05", categoria = "Vivienda",
    )

    private fun disposicion(texto: String): Triple<TextLayoutResult, Float, Int> {
        val nodo = composeRule.onAllNodesWithText(texto, useUnmergedTree = true).fetchSemanticsNodes().single()
        val r = mutableListOf<TextLayoutResult>()
        nodo.config[SemanticsActions.GetTextLayoutResult].action?.invoke(r)
        return Triple(r.single(), nodo.boundsInRoot.right, nodo.size.width)
    }

    @Test
    fun `Vencio hace 23 dias se lee entera al lado de Anotar pago`() {
        composeRule.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(base.density, base.fontScale * 1.12f)) {
                MoviTheme {
                    Box(Modifier.fillMaxWidth()) {
                        FilaDeFactura(
                            pago = vencido,
                            principal = true,
                            enVuelo = false,
                            conPropuesta = false,
                            onAnotar = {},
                            onVerMovimiento = null,
                            onAbrir = {},
                            etiquetaDeAbrir = null,
                            evidencia = null,
                        )
                    }
                }
            }
        }
        composeRule.waitForIdle()

        assertEquals(1, composeRule.onAllNodesWithText(ETIQUETA_ANOTAR, useUnmergedTree = true).fetchSemanticsNodes().size)
        val (pastilla, derecha, ancho) = disposicion("Venció hace 23 días")
        assertEquals(1, pastilla.lineCount, "en un renglón")
        // (No se mira `hasVisualOverflow`: ver `ElDiaPlegadoNoCortaLaFechaTest`.)
        assertFalse(pastilla.isLineEllipsized(0), "sin «…»")
        assertTrue(pastilla.getLineRight(0) - pastilla.getLineLeft(0) <= ancho + 0.5f, "sin pasarse de su caja")
        assertEquals("Venció hace 23 días".length, pastilla.getLineEnd(0, visibleEnd = true), "entera")
        val anchoDeLaPantalla = 360f * composeRule.density.density
        assertTrue(derecha <= anchoDeLaPantalla, "dentro de la pantalla")
    }
}
