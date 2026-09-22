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
    fun `con margen muestra las tres ventanas y lo que queda por dia`() {
        // $10M de ingresos menos $3,1M de arriendo = $6,9M. Hoy se gastaron $150.000.
        montar(datos(ingresos = 10_000_000, arriendo = 3_100_000, gasto = mapOf("2026-09-21" to 150_000L)))

        composeRule.onNodeWithText("DISPONIBLE", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("\$6,9M", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("Ingresos \$10M menos fijos \$3,1M", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("Este período", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("Esta semana", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("Hoy", useUnmergedTree = true).assertIsDisplayed()
        // $6,9M ÷ 31 = $222.580 para hoy.
        composeRule.onNodeWithText("\$150.000 de \$222.580", useUnmergedTree = true).assertIsDisplayed()
        // (6.900.000 − 150.000) ÷ 4 días = $1.687.500.
        composeRule.onNodeWithText("Para lo que queda: \$1,7M por día", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun `sin margen lo dice y no promete un disponible`() {
        montar(datos(ingresos = 2_000_000, arriendo = 3_100_000, gasto = mapOf("2026-09-21" to 90_000L)))

        composeRule.onNodeWithText(
            "Los fijos del período superan tus ingresos por \$1,1M",
            useUnmergedTree = true,
        ).assertIsDisplayed()
        composeRule.onNodeWithText("Gastado este período", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("Para lo que queda", substring = true, useUnmergedTree = true).assertDoesNotExist()
    }
}
