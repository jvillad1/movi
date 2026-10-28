package com.jvillada.movi.ui.recurrentes

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.theme.Medida
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
 * # «Sí, fue este» y «No fue este» caben enteros a 360 dp (revisión de la vuelta 40)
 *
 * Dentro de la fila de una factura, con la sangría del texto, a 360 dp y la letra de la app (×1,12),
 * «No fue este» se cortaba en «No fue». Los botones de la fila van en un `FlowRow`: el que no cabe
 * baja a su renglón. Se mide con el motor de texto real.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h800dp-xhdpi")
class LosBotonesDeLaPropuestaCabenTest {

    @get:Rule val composeRule = createComposeRule()

    private val movimiento = FinancialEvent(
        id = "ev_carro", accountId = "acc-banco", type = TransactionType.EXPENSE, amount = 1_204_064L,
        category = "Carro", description = "Pago cuota vehículo Bancolombia", timestamp = 0L,
    )

    private val carro = PagoDelPeriodo(
        ruleId = "rr_carro", nombre = "Carro", monto = 1_204_064, pagado = false, diasParaVencer = -3,
        vence = "2026-10-25", periodoDelSello = "2026-10", candidatos = listOf(movimiento), categoria = "Carro",
    )

    private fun entero(texto: String) {
        val nodo = composeRule.onAllNodesWithText(texto, useUnmergedTree = true).fetchSemanticsNodes().single()
        val r = mutableListOf<TextLayoutResult>()
        nodo.config[SemanticsActions.GetTextLayoutResult].action?.invoke(r)
        val d = r.single()
        assertEquals(1, d.lineCount, "«$texto» en un renglón")
        assertFalse(d.isLineEllipsized(0), "«$texto» sin «…»")
        assertEquals(texto.length, d.getLineEnd(0, visibleEnd = true), "«$texto» entero")
        assertTrue(d.getLineRight(0) - d.getLineLeft(0) <= nodo.size.width + 0.5f, "«$texto» dentro de su caja")
        assertTrue(nodo.boundsInRoot.right <= 360f * composeRule.density.density, "«$texto» dentro de la pantalla")
    }

    @Test
    fun `No fue este se lee entero dentro de la fila a 360`() {
        composeRule.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(base.density, base.fontScale * 1.12f)) {
                MoviTheme {
                    Box(Modifier.fillMaxWidth().padding(horizontal = Medida.margenDePantalla)) {
                        SeccionChecklistDelPeriodo(
                            checklist = listOf(carro),
                            cargando = false,
                            pudoLeer = true,
                            marcando = emptySet(),
                            onConfirmar = { _, _ -> },
                            onNoFueEste = { _, _ -> },
                            onAnotarMovimiento = {},
                            onQuitarLaMarca = {},
                            onReintentar = {},
                        )
                    }
                }
            }
        }
        composeRule.waitForIdle()

        entero(ETIQUETA_SI_FUE_ESTE)
        entero(ETIQUETA_NO_FUE_ESTE)
    }
}
