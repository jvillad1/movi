package com.jvillada.movi.ui.dashboard

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import com.jvillada.movi.shared.model.FinanceSummary
import com.jvillada.movi.shared.model.OccurrenceState
import com.jvillada.movi.shared.model.PaymentStatus
import com.jvillada.movi.shared.model.PeriodSettings
import com.jvillada.movi.shared.model.PeriodoFinanciero
import com.jvillada.movi.shared.model.RecurringRule
import com.jvillada.movi.shared.model.Scope
import com.jvillada.movi.shared.model.ScreenSection
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.UpcomingPayment
import com.jvillada.movi.theme.Movi
import com.jvillada.movi.theme.MoviTheme
import com.jvillada.movi.ui.sdui.DisponibleDelPeriodoSection
import com.jvillada.movi.ui.sdui.TAG_BARRA_DE_VENTANA
import kotlinx.datetime.LocalDate
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * **La tarjeta «Disponible» del Inicio** (compacta desde la entrega B), montada sola y en un teléfono de 390 dp, con el día fijo
 * (lunes 21 de septiembre de 2026, período del 25 de agosto al 24 de septiembre).
 *
 * Con $10M de ingresos y $3,1M de arriendo el disponible es $6,9M: $222.580 por día (31 días) y
 * $890.320 para esta semana, que el fin del período corta a 4 días (lunes 21 a jueves 24).
 *
 * **Cada columna dice lo que te queda, no lo que gastaste** (el dueño, 28-sep: *«cuánto me puedo
 * gastar por cada una de esas unidades de tiempo no cuánto me gasté»*): la cifra grande es la meta
 * menos lo gastado, y lo gastado va chico abajo («gastaste $X»). Las dos se ven con o sin margen;
 * la barra, solo con margen.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w390dp-h844dp-xhdpi")
class DisponibleEnInicioTest {

    @get:Rule val composeRule = createComposeRule()

    private val hoy = LocalDate(2026, 9, 21)

    private fun datos(ingresos: Long, arriendo: Long, gasto: Map<String, Long>) = DashboardData(
        summary = FinanceSummary(scope = Scope.SELF, balance = 0, ingresos = ingresos, egresos = 0),
        upcoming = listOf(
            UpcomingPayment(
                rule = RecurringRule(
                    id = "rr_arriendo", name = "Arriendo", category = "Vivienda", amount = arriendo,
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
        gastoVariablePorDia = gasto,
        ajustesDePeriodo = PeriodSettings(cutoffDay = 25),
        periodoActual = PeriodoFinanciero(2026, 9),
    )

    /** Los colores del tema, leídos dentro de la composición para comparar sin copiar sus valores. */
    private var rojo: Color = Color.Unspecified
    private var normal: Color = Color.Unspecified

    private fun montar(data: DashboardData) {
        composeRule.setContent {
            MoviTheme {
                rojo = Movi.colores.sale
                normal = Movi.colores.texto
                // La escala de letra ×1,12 que `App.kt` le pone a toda la app.
                val base = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(base.density, base.fontScale * 1.12f)) {
                    Box(Modifier.fillMaxSize()) {
                        DisponibleDelPeriodoSection(
                            section = ScreenSection(type = "DISPONIBLE_DEL_PERIODO", title = "Disponible"),
                            data = data,
                            hoy = hoy,
                        )
                    }
                }
            }
        }
    }

    private fun nodos(texto: String): List<SemanticsNode> =
        composeRule.onAllNodesWithText(texto, useUnmergedTree = true).fetchSemanticsNodes()

    private fun layoutDe(nodo: SemanticsNode): TextLayoutResult {
        val resultados = mutableListOf<TextLayoutResult>()
        val accion = nodo.config[SemanticsActions.GetTextLayoutResult].action
        assertTrue(accion?.invoke(resultados) == true, "El nodo de texto no expuso su layout")
        return resultados.single()
    }

    /** Las [cuantas] cifras con este texto están pintadas de [color]. */
    private fun assertCifras(texto: String, cuantas: Int, color: Color) {
        val encontradas = nodos(texto)
        assertEquals(cuantas, encontradas.size, "«$texto» tendría que estar $cuantas veces")
        encontradas.forEach { assertEquals(color, layoutDe(it).layoutInput.style.color, "el color de «$texto»") }
    }

    private fun barras(): Int = composeRule.onAllNodesWithTag(TAG_BARRA_DE_VENTANA, useUnmergedTree = true)
        .fetchSemanticsNodes().size

    @Test
    fun `con margen muestra las tres ventanas y una sola frase`() {
        // $10M de ingresos menos $3,1M de arriendo = $6,9M. Hoy se gastaron $150.000.
        montar(datos(ingresos = 10_000_000, arriendo = 3_100_000, gasto = mapOf("2026-09-21" to 150_000L)))

        composeRule.onNodeWithText("DISPONIBLE", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("\$6,9M", useUnmergedTree = true).assertIsDisplayed()
        // Quedan $6,8M para 4 días, hoy incluido. Una frase para la tarjeta, sin «vas bien».
        composeRule.onNodeWithText("Te quedan \$6,8M, unos \$1,7M por día", useUnmergedTree = true).assertIsDisplayed()
        // Las tres columnas: el período, la semana (cortada a 4 días por el fin del período) y hoy.
        // Cada una dice lo que te queda en grande: $6,9M − $150.000, $890.320 − $150.000 y
        // $222.580 − $150.000. Y lo gastado chico, que en las tres es lo de hoy.
        composeRule.onNodeWithText("Período", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("Semana · 4 días", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("Hoy", useUnmergedTree = true).assertIsDisplayed()
        assertCifras("\$6,8M", 1, normal)
        assertCifras("\$740.320", 1, normal)
        assertCifras("\$72.580", 1, normal)
        composeRule.onAllNodesWithText("gastaste \$150.000", useUnmergedTree = true).assertCountEquals(3)
        // La meta ya no va escrita como «de $X»: la cifra grande es lo que queda de ella.
        composeRule.onNodeWithText("de \$", substring = true, useUnmergedTree = true).assertDoesNotExist()
        assertEquals(3, barras(), "con margen, cada columna tiene su barra")
        // Que «gastaste $X» entre en su columna lo mide `GastasteEntraEnSuColumnaTest`, con el
        // motor de texto real: acá Robolectric mide con uno que no sirve para anchos.
        // De dónde sale, detrás de un toque.
        composeRule.onNodeWithText("Ingresos \$10M menos fijos \$3,1M", useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNodeWithText("¿De dónde sale?", useUnmergedTree = true).performClick()
        composeRule.onNodeWithText("Ingresos \$10M menos fijos \$3,1M", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("bien", substring = true, ignoreCase = true, useUnmergedTree = true).assertDoesNotExist()
    }

    /**
     * La escena que motivó la entrega B: el período pasado y, en la tarjeta vieja, «Te pasaste» en
     * rojo con «Vas bien» para la semana un renglón abajo. Ahora hay una frase —la del período— y las
     * metas de la semana y de hoy siguen a la vista en sus columnas.
     */
    @Test
    fun `con el periodo pasado dice una sola cosa y no vas bien`() {
        montar(datos(ingresos = 10_000_000, arriendo = 3_100_000, gasto = mapOf("2026-09-01" to 8_000_000L)))

        composeRule.onNodeWithText("Te pasaste del disponible del período por \$1,1M", useUnmergedTree = true)
            .assertIsDisplayed()
        // Te pasaste: al período le queda $6,9M − $8M, en rojo — la misma cifra que la frase.
        assertCifras("−\$1,1M", 1, rojo)
        composeRule.onNodeWithText("gastaste \$8M", useUnmergedTree = true).assertIsDisplayed()
        // La semana y hoy no tienen gasto: les queda la meta entera, y lo siguen diciendo.
        assertCifras("\$890.320", 1, normal)
        assertCifras("\$222.580", 1, normal)
        composeRule.onAllNodesWithText("gastaste \$0", useUnmergedTree = true).assertCountEquals(2)
        composeRule.onNodeWithText("bien", substring = true, ignoreCase = true, useUnmergedTree = true).assertDoesNotExist()
    }

    /**
     * Te pasaste solo hoy, con el período holgado: la columna de hoy dice cuánto de más en rojo, y
     * la frase lo cuenta con la misma cifra.
     */
    @Test
    fun `hoy pasado dice lo que queda en negativo y en rojo`() {
        // $300.000 hoy contra una meta de $222.580.
        montar(datos(ingresos = 10_000_000, arriendo = 3_100_000, gasto = mapOf("2026-09-21" to 300_000L)))

        assertCifras("−\$77.420", 1, rojo)
        assertCifras("\$590.320", 1, normal)
        assertCifras("\$6,6M", 1, normal)
        composeRule.onAllNodesWithText("gastaste \$300.000", useUnmergedTree = true).assertCountEquals(3)
        composeRule.onNodeWithText(
            "Hoy te pasaste de la meta por \$77.420 · a la semana le quedan \$590.320",
            useUnmergedTree = true,
        ).assertIsDisplayed()
        assertEquals(3, barras())
    }

    /**
     * Con lo que manda el server nuevo, el encabezado dice de dónde sale el disponible: lo que
     * tenías el 25, lo que entró (un préstamo, un ahorro) y los fijos, en dos líneas cortas.
     */
    @Test
    fun `con lo que tenias al empezar el encabezado lo desglosa en dos lineas`() {
        // $1,4M el 25 + $33,9M que entraron − $500.000 guardados − $3,1M de arriendo = $31,7M.
        montar(
            datos(ingresos = 22_152_488, arriendo = 3_100_000, gasto = mapOf("2026-09-21" to 150_000L))
                .copy(plataDelDisponible = PlataDelDisponible(1_386_694, 33_930_447, 500_000)),
        )

        composeRule.onNodeWithText("\$31,7M", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("¿De dónde sale?", useUnmergedTree = true).performClick()
        composeRule.onNodeWithText("Tenías \$1,4M el 25 · entraron \$33,9M", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("Guardaste \$500.000 · fijos \$3,1M", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("Ingresos", substring = true, useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNodeWithText("Semana · 4 días", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun `sin margen lo dice y no promete un disponible`() {
        montar(datos(ingresos = 2_000_000, arriendo = 3_100_000, gasto = mapOf("2026-09-21" to 90_000L)))

        composeRule.onNodeWithText(
            "Los fijos del período superan tus ingresos por \$1,1M",
            useUnmergedTree = true,
        ).assertIsDisplayed()
        // Sin margen la meta de cada ventana es cero, y lo que queda es menos lo gastado: las tres
        // columnas lo siguen diciendo —en rojo, porque ya gastaste algo sin tener de dónde— con lo
        // gastado abajo. Lo que se va es la barra: contra cero no hay nada que medir.
        composeRule.onNodeWithText("Período", useUnmergedTree = true).assertIsDisplayed()
        assertCifras("−\$90.000", 3, rojo)
        composeRule.onAllNodesWithText("gastaste \$90.000", useUnmergedTree = true).assertCountEquals(3)
        composeRule.onNodeWithText("de \$", substring = true, useUnmergedTree = true).assertDoesNotExist()
        assertEquals(0, barras(), "sin margen no hay barras")
    }

    /** Sin margen y sin gastar nada: cada columna dice «$0» —no se esconde— y no está en rojo. */
    @Test
    fun `sin margen y sin gasto cada columna dice cero`() {
        montar(datos(ingresos = 2_000_000, arriendo = 3_100_000, gasto = emptyMap()))

        composeRule.onNodeWithText("Los fijos del período superan tus ingresos por \$1,1M", useUnmergedTree = true)
            .assertIsDisplayed()
        assertCifras("\$0", 3, normal)
        composeRule.onAllNodesWithText("gastaste \$0", useUnmergedTree = true).assertCountEquals(3)
        assertEquals(0, barras())
    }
}
