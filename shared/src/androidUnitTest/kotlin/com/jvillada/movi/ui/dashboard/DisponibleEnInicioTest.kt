package com.jvillada.movi.ui.dashboard

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
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
import com.jvillada.movi.theme.MoviTheme
import com.jvillada.movi.ui.sdui.DisponibleDelPeriodoSection
import kotlinx.datetime.LocalDate
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * **La tarjeta «Disponible» del Inicio**, montada sola y en un teléfono de 390 dp, con el día fijo
 * (lunes 21 de septiembre de 2026, período del 25 de agosto al 24 de septiembre).
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

    private fun montar(data: DashboardData) {
        composeRule.setContent {
            MoviTheme {
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

    @Test
    fun `con margen muestra las tres metas y como viene cada una`() {
        // $10M de ingresos menos $3,1M de arriendo = $6,9M. Hoy se gastaron $150.000.
        montar(datos(ingresos = 10_000_000, arriendo = 3_100_000, gasto = mapOf("2026-09-21" to 150_000L)))

        composeRule.onNodeWithText("DISPONIBLE", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("\$6,9M", useUnmergedTree = true).assertIsDisplayed()
        // Sin los campos nuevos (un server viejo) el encabezado es el de siempre.
        composeRule.onNodeWithText("Ingresos \$10M menos fijos \$3,1M", useUnmergedTree = true).assertIsDisplayed()
        // $6,9M ÷ 31 = $222.580 por día; por 7, $1,6M por semana.
        composeRule.onNodeWithText("Meta por semana \$1,6M · Meta por día \$222.580", useUnmergedTree = true)
            .assertIsDisplayed()
        // El 21 quedan el 22, el 23 y el 24: los mismos «3 días» que dice «Tu plata» arriba, aunque
        // la cuenta por día reparta entre 4 (hoy incluido).
        composeRule.onNodeWithText("Este período · quedan 3 días", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText(
            "Vas \$6,1M por debajo de lo previsto a hoy · te quedan \$6,8M, unos \$1,7M por día",
            useUnmergedTree = true,
        ).assertIsDisplayed()
        // Lunes 21 a jueves 24: la semana se corta con el período.
        composeRule.onNodeWithText("Esta semana · semana corta: 4 días", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("\$150.000 de \$890.320", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("Vas bien: te quedan \$740.320 para esta semana", useUnmergedTree = true)
            .assertIsDisplayed()
        composeRule.onNodeWithText("Hoy", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("\$150.000 de \$222.580", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("Te quedan \$72.580 para hoy", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun `con el periodo ya gastado la semana y hoy siguen con su meta`() {
        // $6,9M de disponible y $8M gastados el 1-sep: el período está pasado. La semana y hoy
        // siguen midiendo lo suyo contra su meta: el dueño las usa para organizar lo que queda.
        montar(datos(ingresos = 10_000_000, arriendo = 3_100_000, gasto = mapOf("2026-09-01" to 8_000_000L)))

        composeRule.onNodeWithText("\$8M de \$6,9M", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("Te pasaste por \$1,1M", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("\$0 de \$890.320", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("Vas bien: te quedan \$890.320 para esta semana", useUnmergedTree = true)
            .assertIsDisplayed()
        composeRule.onNodeWithText("\$0 de \$222.580", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("Te quedan \$222.580 para hoy", useUnmergedTree = true).assertIsDisplayed()
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
        composeRule.onNodeWithText("Tenías \$1,4M el 25 · entraron \$33,9M", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("Guardaste \$500.000 · fijos \$3,1M", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("Ingresos", substring = true, useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNodeWithText("Meta por semana", substring = true, useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun `sin margen lo dice y no promete un disponible`() {
        montar(datos(ingresos = 2_000_000, arriendo = 3_100_000, gasto = mapOf("2026-09-21" to 90_000L)))

        composeRule.onNodeWithText(
            "Los fijos del período superan tus ingresos por \$1,1M",
            useUnmergedTree = true,
        ).assertIsDisplayed()
        composeRule.onNodeWithText("Gastado este período", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("Meta por", substring = true, useUnmergedTree = true).assertDoesNotExist()
    }
}
