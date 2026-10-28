package com.jvillada.movi.ui.dashboard

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.CardSummary
import com.jvillada.movi.shared.model.CreditSummary
import com.jvillada.movi.shared.model.FinanceSummary
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.PaymentStatus
import com.jvillada.movi.shared.model.PeriodSettings
import com.jvillada.movi.shared.model.PeriodoFinanciero
import com.jvillada.movi.shared.model.ReconciliationStatus
import com.jvillada.movi.shared.model.RecurringRule
import com.jvillada.movi.shared.model.Scope
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.UpcomingPayment
import com.jvillada.movi.shared.model.defaultDashboardDefinition
import com.jvillada.movi.theme.MoviTheme
import com.jvillada.movi.ui.Screen
import com.jvillada.movi.ui.sdui.SduiRenderer
import com.jvillada.movi.ui.sdui.TAG_FRANJA_POR_CONFIRMAR
import com.jvillada.movi.ui.sdui.TAG_HEROE_EN_TARJETA
import com.jvillada.movi.ui.sdui.TAG_PROXIMO_PAGO
import com.jvillada.movi.ui.sdui.TAG_ULTIMOS_MOVIMIENTOS
import com.jvillada.movi.ui.sdui.VER_TODA_LA_ACTIVIDAD
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * # El Inicio de la opción 3 (vuelta 39), montado
 *
 * La franja «Por confirmar» de la tarjeta héroe aparece con algo que confirmar y no está sin nada; el
 * «Próximo pago» con su «Anotar pago»; los «Últimos movimientos» con tres filas en orden y «Ver toda la
 * actividad»; y el orden de arriba abajo de la maqueta.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w390dp-h3200dp-xhdpi")
class ElInicioDeLaOpcion3Test {

    @get:Rule val composeRule = createComposeRule()

    private val navegado = mutableListOf<Screen>()

    private fun pago(id: String, nombre: String, monto: Long, dias: Int) = UpcomingPayment(
        rule = RecurringRule(id = id, name = nombre, category = "Vivienda", amount = monto, dayOfMonth = 10, type = TransactionType.EXPENSE),
        dueDate = "2026-09-10",
        daysUntil = dias,
        status = if (dias < 0) PaymentStatus.OVERDUE else PaymentStatus.UPCOMING,
    )

    private fun gasto(id: String, descripcion: String, monto: Long) = FinancialEvent(
        id = id, accountId = "a1", type = TransactionType.EXPENSE, amount = monto, category = "Mercado",
        description = descripcion, timestamp = 0L, reconciliationStatus = ReconciliationStatus.RECONCILED,
    )

    private val datos = DashboardData(
        summary = FinanceSummary(scope = Scope.SELF, balance = 0, ingresos = 10_000_000, egresos = 4_000_000),
        accounts = listOf(Account("a1", "Nu", AccountType.SAVINGS, 2_890_000)),
        credits = emptyList<CreditSummary>(),
        cards = emptyList<CardSummary>(),
        upcoming = listOf(pago("r_cel", "Celular", 53_000, 2), pago("r_arr", "Arriendo", 1_850_000, -3)),
        ocurrencias = emptyList(),
        periodoActual = PeriodoFinanciero(2026, 9),
        ajustesDePeriodo = PeriodSettings(),
        ultimosMovimientos = listOf(gasto("e1", "Panadería La Esquina", 27_500), gasto("e2", "Ferretería Central", 64_300), gasto("e3", "Taller del barrio", 18_700), gasto("e4", "Lo cuarto", 1_000)),
    )

    private fun montar(data: DashboardData) {
        navegado.clear()
        composeRule.setContent {
            MoviTheme {
                Box(Modifier.fillMaxSize()) {
                    SduiRenderer(
                        definition = defaultDashboardDefinition(),
                        data = data,
                        modifier = Modifier.fillMaxSize(),
                        onNavigate = { navegado += it },
                        conBanda = true,
                    )
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun arriba(tag: String) = composeRule.onNodeWithTag(tag, useUnmergedTree = true).getUnclippedBoundsInRoot().top

    @Test
    fun `de arriba abajo, heroe, acciones, proximo pago y ultimos movimientos`() {
        montar(datos)
        val heroe = arriba(TAG_HEROE_EN_TARJETA)
        val acciones = composeRule.onNodeWithText("Anotar gasto", useUnmergedTree = true).getUnclippedBoundsInRoot().top
        val proximo = arriba(TAG_PROXIMO_PAGO)
        val ultimos = arriba(TAG_ULTIMOS_MOVIMIENTOS)
        assertTrue(heroe < acciones && acciones < proximo && proximo < ultimos, "$heroe / $acciones / $proximo / $ultimos")
    }

    @Test
    fun `la franja Por confirmar aparece con avisos y lleva a Por revisar`() {
        montar(datos.copy(pendingSms = 3))
        composeRule.onNodeWithTag(TAG_FRANJA_POR_CONFIRMAR, useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("3 avisos", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag(TAG_FRANJA_POR_CONFIRMAR, useUnmergedTree = true).performClick()
        assertEquals(Screen.PorRevisar, navegado.last())
    }

    @Test
    fun `sin nada por confirmar la franja no esta`() {
        montar(datos)
        assertEquals(0, composeRule.onAllNodesWithTag(TAG_FRANJA_POR_CONFIRMAR, useUnmergedTree = true).fetchSemanticsNodes().size)
    }

    @Test
    fun `el proximo pago es el vencido, con Anotar pago y y N mas`() {
        montar(datos)
        composeRule.onNodeWithText("Próximo pago · venció hace 3 días", useUnmergedTree = true).assertIsDisplayed()
        // «Arriendo» también está en el widget «Falta por pagar», más abajo: el primero es el próximo pago.
        assertTrue(composeRule.onAllNodesWithText("Arriendo", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty())
        composeRule.onNodeWithText("Vencido", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("Anotar pago", useUnmergedTree = true).performClick()
        assertTrue(navegado.last() is Screen.QuickAdd, "abre Agregar con el pago: ${navegado.last()}")
        composeRule.onNodeWithText("y 1 más", useUnmergedTree = true).performClick()
        assertEquals(Screen.Plan(com.jvillada.movi.ui.plan.SEGMENTO_PAGOS), navegado.last())
    }

    @Test
    fun `sin pagos pendientes no hay proximo pago`() {
        montar(datos.copy(upcoming = emptyList()))
        assertEquals(0, composeRule.onAllNodesWithTag(TAG_PROXIMO_PAGO, useUnmergedTree = true).fetchSemanticsNodes().size)
    }

    @Test
    fun `los ultimos movimientos son tres filas en orden y Ver toda la actividad abre Actividad`() {
        montar(datos)
        val titulos = listOf("Panadería La Esquina", "Ferretería Central", "Taller del barrio").map {
            composeRule.onNodeWithText(it, useUnmergedTree = true).getUnclippedBoundsInRoot().top
        }
        assertEquals(titulos.sorted(), titulos, "en el orden de Actividad")
        // El cuarto no se lista: son tres.
        assertEquals(0, composeRule.onAllNodesWithText("Lo cuarto", useUnmergedTree = true).fetchSemanticsNodes().size)
        // La cifra de la fila de Actividad: el gasto con su «−».
        assertEquals(1, composeRule.onAllNodesWithText("−\$27.500", substring = true, useUnmergedTree = true).fetchSemanticsNodes().size)
        composeRule.onNodeWithText(VER_TODA_LA_ACTIVIDAD, useUnmergedTree = true).performClick()
        assertEquals(Screen.Transactions(), navegado.last())
    }
}
