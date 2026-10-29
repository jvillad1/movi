package com.jvillada.movi.ui.dashboard

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
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
import com.jvillada.movi.ui.Screen
import com.jvillada.movi.ui.plan.TITULO_CUANTO_PUEDES_GASTAR
import com.jvillada.movi.ui.sdui.HeroDeUnVistazo
import com.jvillada.movi.ui.sdui.TAG_ALERTA_DEL_DISPONIBLE
import com.jvillada.movi.ui.sdui.TAG_BARRA_DE_VENTANA
import com.jvillada.movi.ui.sdui.TAG_BARRA_EN_ALERTA
import com.jvillada.movi.ui.sdui.TarjetaDelDisponible
import kotlinx.datetime.LocalDate
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

/**
 * **Vuelta 45 · una sola alarma, y que diga qué hacer.** Con la semana pasada de su meta, «Te pasaste de
 * la meta por $1,3M» salía en el héroe del Inicio y en la frase de Pagos, y Pagos pintaba tres barras
 * rojas. Ahora, montados el héroe y la tarjeta de Pagos con los mismos datos:
 *
 * - la alarma sale **una vez** (en Pagos), con «Mira en qué se fue ›», que abre «En qué se va»;
 * - el héroe no la repite;
 * - **a lo sumo una barra en alerta**: ninguna con la semana pasada, la del período si el período se pasó.
 *
 * Escenario de `TeQuedanEnHoyEnPantallaTest`: lunes 21-sep-2026, $10M de ingresos, $3,1M de arriendo;
 * desde la vuelta 60 la meta de la semana sale de lo que quedaba el lunes.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w390dp-h2400dp-xhdpi")
class UnaSolaAlarmaTest {

    @get:Rule val composeRule = createComposeRule()

    private val hoy = LocalDate(2026, 9, 21)
    private var navegoA: Screen? = null

    private fun datos(gasto: Map<String, Long>) = DashboardData(
        summary = FinanceSummary(scope = Scope.SELF, balance = 0, ingresos = 10_000_000, egresos = 4_000_000),
        accounts = listOf(Account(id = "a1", name = "Ahorros", type = AccountType.SAVINGS, balance = 2_345_678)),
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
        ocurrencias = listOf(OccurrenceState(ruleId = "rr_arriendo", period = "2026-09", dueDate = "2026-09-05", occurred = true)),
        gastoVariablePorDia = gasto,
        ajustesDePeriodo = PeriodSettings(cutoffDay = 25),
        periodoActual = PeriodoFinanciero(2026, 9),
    )

    private fun montar(data: DashboardData, hoy: LocalDate = this.hoy) {
        navegoA = null
        composeRule.setContent {
            MoviTheme {
                Column(Modifier.fillMaxSize()) {
                    HeroDeUnVistazo(
                        section = ScreenSection(type = "HERO_BALANCE"),
                        data = data,
                        onNavigate = { navegoA = it },
                        hoy = hoy,
                    )
                    TarjetaDelDisponible(
                        titulo = TITULO_CUANTO_PUEDES_GASTAR,
                        disponible = disponibleDelInicio(data, hoy),
                        onVerEnQueSeVa = { navegoA = Screen.EnQueSeVa() },
                    )
                }
            }
        }
        composeRule.mainClock.advanceTimeBy(DURACION_DE_LA_ENTRADA_MS.toLong() + 200L)
        composeRule.waitForIdle()
    }

    private fun cuantos(tag: String) = composeRule.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().size

    private fun cuantosDicen(texto: String) =
        composeRule.onAllNodesWithText(texto, substring = true, ignoreCase = true, useUnmergedTree = true).fetchSemanticsNodes().size

    @Test
    fun `semana pasada - una sola alarma, en Pagos, con que hacer, y ninguna barra en alerta`() {
        // Vuelta 60: la semana mide contra lo que quedaba el lunes, y la última del período se lleva todo
        // lo que queda (no se pasa sin pasar el período). Esta es la del lunes 14: quedaban $6,9M para 11
        // días, la semana $4.390.904; $4.500.000 hoy, y al período le sobra.
        montar(datos(gasto = mapOf("2026-09-14" to 4_500_000L)), hoy = LocalDate(2026, 9, 14))

        assertEquals(1, cuantos(TAG_ALERTA_DEL_DISPONIBLE), "una sola alarma")
        assertEquals(1, cuantosDicen("pasaste de la meta"), "el héroe no la repite")
        composeRule.onNodeWithText("Esta semana te pasaste de la meta por \$109.096.", useUnmergedTree = true).assertExists()
        assertEquals(0, cuantos(TAG_BARRA_EN_ALERTA), "ninguna barra en alerta: el período no se pasó")
        assertEquals(3, cuantos(TAG_BARRA_DE_VENTANA), "las tres barras, en tinta")

        composeRule.onNodeWithText(MIRA_EN_QUE_SE_FUE).performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
        assertEquals(Screen.EnQueSeVa(), navegoA, "«Mira en qué se fue» abre En qué se va")
    }

    @Test
    fun `periodo pasado - una sola alarma y a lo sumo una barra en alerta, la del periodo`() {
        montar(datos(gasto = mapOf("2026-09-01" to 7_000_000L)))

        assertEquals(1, cuantos(TAG_ALERTA_DEL_DISPONIBLE))
        assertEquals(0, cuantosDicen("pasaste de la meta"))
        assertEquals(1, cuantos(TAG_BARRA_EN_ALERTA), "solo la del período")
        assertEquals(2, cuantos(TAG_BARRA_DE_VENTANA), "la semana y hoy, en tinta")
    }

    @Test
    fun `vas bien - sin alarma ni barra en alerta`() {
        montar(datos(gasto = mapOf("2026-08-26" to 100_000L)))

        assertEquals(0, cuantos(TAG_ALERTA_DEL_DISPONIBLE))
        assertEquals(0, cuantos(TAG_BARRA_EN_ALERTA))
    }
}
