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
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.TransactionType
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
 * # El enlace del candidato lejano cabe entero (revisión de la vuelta 51)
 *
 * A 390 dp el Carro decía «Hay un movimiento de $260.000 ·» y la pregunta se perdía. Ahora es «¿Es el
 * de $260.000?», y se mide como la pastilla de vencimiento: el motor de texto real, 360 dp y la letra
 * de la app (×1,12), debajo de la fila del Carro con su «Anotar pago».
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h800dp-xhdpi")
class ElEnlaceDelCandidatoCabeTest {

    @get:Rule val composeRule = createComposeRule()

    private val tanqueo = FinancialEvent(
        id = "ev-tanqueo", accountId = "acc", type = TransactionType.EXPENSE, amount = 260_000L,
        category = "Transporte", description = "Terpel", timestamp = 1_793_000_000_000L,
    )
    private val carro = PagoDelPeriodo(
        ruleId = "rr_carro", nombre = "Carro", monto = 9_000L, pagado = false, diasParaVencer = -3,
        vence = "2026-10-25", periodoDelSello = "2026-10", categoria = "Transporte", candidatos = listOf(tanqueo),
    )

    private fun disposicion(texto: String): Pair<TextLayoutResult, Float> {
        val nodo = composeRule.onAllNodesWithText(texto, useUnmergedTree = true).fetchSemanticsNodes().single()
        val r = mutableListOf<TextLayoutResult>()
        nodo.config[SemanticsActions.GetTextLayoutResult].action?.invoke(r)
        return r.single() to nodo.boundsInRoot.right
    }

    @Test
    fun `Es el de 260 mil cabe entero a 360 con la letra 1,12`() {
        composeRule.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(base.density, base.fontScale * 1.12f)) {
                MoviTheme {
                    Box(Modifier.fillMaxWidth()) {
                        FilaDeFactura(
                            pago = carro,
                            principal = true,
                            enVuelo = false,
                            conPropuesta = false,
                            onAnotar = {},
                            onVerMovimiento = null,
                            onAbrir = {},
                            etiquetaDeAbrir = null,
                            evidencia = null,
                            debajo = {
                                PropuestaLejana(
                                    pago = carro,
                                    propuesta = tanqueo,
                                    enVuelo = false,
                                    acciones = AccionesDelChecklist({ _, _ -> }, { _, _ -> }, {}, {}),
                                    conBotonDeAnotar = false,
                                )
                            },
                        )
                    }
                }
            }
        }
        composeRule.waitForIdle()

        val texto = "¿Es el de \$260.000?"
        assertEquals(texto, enlaceDeLaPropuestaLejana(tanqueo))
        val (enlace, derecha) = disposicion(texto)
        assertEquals(1, enlace.lineCount, "en un renglón")
        assertFalse(enlace.isLineEllipsized(0), "sin «…»")
        assertEquals(texto.length, enlace.getLineEnd(0, visibleEnd = true), "entero, con la pregunta")
        assertTrue(derecha <= 360f * composeRule.density.density, "dentro de la pantalla")
    }
}
