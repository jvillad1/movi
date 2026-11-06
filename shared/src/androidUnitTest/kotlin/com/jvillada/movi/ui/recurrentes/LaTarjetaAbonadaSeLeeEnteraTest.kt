package com.jvillada.movi.ui.recurrentes

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import com.jvillada.movi.shared.model.ExtractoDeLaTarjeta
import com.jvillada.movi.theme.MoviTheme
import com.jvillada.movi.ui.components.ds.SubtituloVisibleDeFilaKey
import com.jvillada.movi.ui.dashboard.PagoDelPeriodo
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * # Re-revisión de la vuelta 136 (1) · la tarjeta abonada dice entera la cifra que falta
 *
 * A 360 dp la frase de la vuelta 62 en un renglón se veía «Abonaste $100.000 · faltan $1.292.…»: se cortaba justo
 * la cifra que el arreglo C1 quería mostrar. Ahora lo corto es «Faltan $1.292.700 del extracto» y la frase larga va
 * solo si cabe entera. Se mide **lo que se dibuja** (su disposición: sin «…» y dentro del ancho), no la semántica
 * (que es siempre la larga, para el lector). Sin el cambio, lo dibujado terminaba en «…» a 360 dp.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LaTarjetaAbonadaSeLeeEnteraTest {

    @get:Rule val composeRule = createComposeRule()

    private val extracto = ExtractoDeLaTarjeta(
        corte = "2026-10-20", fechaDePago = "2026-11-05", facturado = 1_292_700L, pagadoDesdeElCorte = 100_000L, minimo = 180_000L,
    )
    private val abonada = PagoDelPeriodo(
        ruleId = "card_nu", nombre = "Pago tarjeta Nu", monto = 1_583_000L, pagado = true, diasParaVencer = 7,
        montoEsSaldo = true, vence = "2026-11-05", montoPagado = 100_000L, derivado = true,
    )

    private fun disposicion(nodo: SemanticsNode): TextLayoutResult {
        val r = mutableListOf<TextLayoutResult>()
        nodo.config[SemanticsActions.GetTextLayoutResult].action?.invoke(r)
        return r.single()
    }

    private fun montarYMedir() {
        composeRule.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(base.density, base.fontScale * 1.12f)) {
                MoviTheme {
                    Column(Modifier.fillMaxWidth()) {
                        FilaDeFactura(pago = abonada, estado = estadoDeLaFactura(abonada, null, extracto = extracto), extracto = extracto, onAbrir = {})
                    }
                }
            }
        }
        composeRule.waitForIdle()
        val nodo = composeRule.onAllNodes(SemanticsMatcher.keyIsDefined(SubtituloVisibleDeFilaKey), useUnmergedTree = true)
            .fetchSemanticsNodes().single()
        val visible = nodo.config.getOrNull(SubtituloVisibleDeFilaKey)!!
        assertTrue("\$1.292.700" in visible, "lo que se dibuja dice la cifra que falta: «$visible»")
        val d = disposicion(nodo)
        assertEquals(visible, d.layoutInput.text.text, "la disposición es la de lo que se dibuja")
        assertTrue((0 until d.lineCount).none { d.isLineEllipsized(it) }, "«$visible» sin «…»")
        assertTrue((0 until d.lineCount).all { d.getLineRight(it) - d.getLineLeft(it) <= nodo.size.width + 0.5f }, "«$visible» dentro de su ancho")
    }

    @Test
    @Config(sdk = [34], qualifiers = "w360dp-h800dp-xhdpi")
    fun `a 360 dp la cifra que falta se dibuja entera`() = montarYMedir()

    @Test
    @Config(sdk = [34], qualifiers = "w384dp-h800dp-xhdpi")
    fun `a 384 dp tambien`() = montarYMedir()
}
