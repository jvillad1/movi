package com.jvillada.movi.ui.dashboard

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.isUnspecified
import com.jvillada.movi.shared.model.FinanceSummary
import com.jvillada.movi.shared.model.OccurrenceState
import com.jvillada.movi.shared.model.PeriodSettings
import com.jvillada.movi.shared.model.PeriodoFinanciero
import com.jvillada.movi.shared.model.RecurringRule
import com.jvillada.movi.shared.model.Scope
import com.jvillada.movi.shared.model.ScreenSection
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.PaymentStatus
import com.jvillada.movi.shared.model.UpcomingPayment
import com.jvillada.movi.theme.MoviTheme
import com.jvillada.movi.ui.sdui.DisponibleDelPeriodoSection
import com.jvillada.movi.ui.sdui.TAMANO_MINIMO_DE_LO_GASTADO
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * **Las dos cifras de cada columna del disponible entran enteras a 390 dp.**
 *
 * Desde el 28-sep cada columna (período, semana, hoy) dice en grande lo que te queda —negativo
 * cuando te pasaste— y abajo «gastaste $X». Las dos van en un renglón (`maxLines = 1`), así que si
 * no entran se cortan callado. Cada columna tiene un tercio de lo que deja la tarjeta: ~100 dp.
 *
 * El peor caso es el del dueño hoy: sin margen, con un gasto de seis cifras en las tres ventanas
 * («−$888.888» arriba y «gastaste $888.888» abajo; del millón para arriba la cifra se achica a
 * «$1,2M»). Con la escala de letra ×1,12 que `App.kt` le pone a toda la app. A 11,5 sp «gastaste
 * $888.888» no entraba (231 px contra 202): la línea se achica sola (`autoSize`) y esta prueba
 * cuida que el mínimo alcance, sin «…» ni dos renglones.
 *
 * `@GraphicsMode(NATIVE)` y `sdk = [34]`: sin el motor de texto real Robolectric mide anchos que
 * no sirven para esta pregunta (ver `CuatroAccionesEntranA390Test`).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w390dp-h844dp-xhdpi")
class GastasteEntraEnSuColumnaTest {

    @get:Rule val composeRule = createComposeRule()

    private val hoy = LocalDate(2026, 9, 21)

    /** $2M de ingresos contra $3,1M de arriendo: sin margen, como el dueño. $888.888 gastados hoy. */
    private val sinMargen = DashboardData(
        summary = FinanceSummary(scope = Scope.SELF, balance = 0, ingresos = 2_000_000, egresos = 0),
        upcoming = listOf(
            UpcomingPayment(
                rule = RecurringRule(
                    id = "rr_arriendo", name = "Arriendo", category = "Vivienda", amount = 3_100_000,
                    dayOfMonth = 5, type = TransactionType.EXPENSE,
                ),
                dueDate = "2026-10-05",
                daysUntil = 14,
                status = PaymentStatus.UPCOMING,
            ),
        ),
        ocurrencias = listOf(
            OccurrenceState(ruleId = "rr_arriendo", period = "2026-09", dueDate = "2026-09-05", occurred = true),
        ),
        gastoVariablePorDia = mapOf("2026-09-21" to 888_888L),
        ajustesDePeriodo = PeriodSettings(cutoffDay = 25),
        periodoActual = PeriodoFinanciero(2026, 9),
    )

    @Test
    fun `sin margen y con seis cifras lo que queda y lo gastado entran en su columna`() {
        composeRule.setContent {
            MoviTheme {
                val base = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(base.density, base.fontScale * 1.12f)) {
                    Box(Modifier.fillMaxSize()) {
                        DisponibleDelPeriodoSection(
                            section = ScreenSection(type = "DISPONIBLE_DEL_PERIODO", title = "Disponible"),
                            data = sinMargen,
                            hoy = hoy,
                        )
                    }
                }
            }
        }
        composeRule.waitForIdle()

        listOf("−\$888.888", "gastaste \$888.888").forEach { texto ->
            val nodos = composeRule.onAllNodesWithText(texto, useUnmergedTree = true).fetchSemanticsNodes()
            assertEquals("«$texto» tendría que estar en las tres columnas", 3, nodos.size)
            nodos.forEach { nodo ->
                // Contra el ancho que la columna le da (`maxWidth`), no contra `hasVisualOverflow`:
                // el nodo de un `Text` mide lo que su texto, y el redondeo a píxeles enteros lo
                // marca «pasado de ancho» por décimas aunque sobre casi un cuarto de columna. Y el
                // ancho intrínseco, no el del primer renglón: un texto partido o con «…» parece
                // angosto en su primer renglón.
                val layout = layoutDe(nodo)
                val columna = layout.layoutInput.constraints.maxWidth
                val pide = layout.multiParagraph.intrinsics.maxIntrinsicWidth
                val tamano = layout.layoutInput.style.fontSize
                val detalle = "la columna da ${columna}px y el texto entero pide ${pide}px a $tamano"
                assertTrue("«$texto» no entra en su columna a 390 dp: $detalle", pide <= columna)
                assertEquals("«$texto» quedó en más de un renglón: $detalle", 1, layout.lineCount)
                assertTrue("«$texto» terminó en «…»: $detalle", !layout.isLineEllipsized(0))
                assertTrue(
                    "«$texto» se achicó por debajo del mínimo legible: $detalle",
                    tamano.isUnspecified || tamano.value >= TAMANO_MINIMO_DE_LO_GASTADO.value,
                )
            }
        }
    }

    private fun layoutDe(nodo: SemanticsNode): TextLayoutResult {
        val resultados = mutableListOf<TextLayoutResult>()
        val accion = nodo.config[SemanticsActions.GetTextLayoutResult].action
        assertTrue("El nodo de texto no expuso su layout", accion?.invoke(resultados) == true)
        return resultados.single()
    }
}
