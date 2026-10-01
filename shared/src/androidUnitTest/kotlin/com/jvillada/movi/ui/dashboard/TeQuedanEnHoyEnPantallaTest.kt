package com.jvillada.movi.ui.dashboard

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.TextLayoutResult
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
import com.jvillada.movi.theme.Movi
import com.jvillada.movi.theme.MoviTheme
import com.jvillada.movi.ui.Screen
import com.jvillada.movi.ui.components.formatMoneyCompact
import com.jvillada.movi.ui.plan.TITULO_CUANTO_PUEDES_GASTAR
import com.jvillada.movi.ui.sdui.HeroDeUnVistazo
import com.jvillada.movi.ui.sdui.TarjetaDelDisponible
import kotlinx.datetime.LocalDate
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * **Hoy y Plan, montados uno encima del otro con los mismos datos, dicen la misma cifra** (Ola 1).
 *
 * Arriba el hero de Hoy (`HeroDeUnVistazo`), abajo la tarjeta de Plan tal como la arma
 * `PlanScreen` (`TarjetaDelDisponible` con `disponibleDelInicio(data)`). Se lee lo que de verdad se
 * pinta: el texto de la cifra grande y el de la columna «Período», y el color de los dos.
 *
 * Escenario de `DisponibleEnInicioTest`: lunes 21-sep-2026, $10M de ingresos, $3,1M de arriendo.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w390dp-h2400dp-xhdpi")
class TeQuedanEnHoyEnPantallaTest {

    @get:Rule val composeRule = createComposeRule()

    private val hoy = LocalDate(2026, 9, 21)

    private fun datos(gasto: Map<String, Long>, cuentas: List<Account>) = DashboardData(
        summary = FinanceSummary(scope = Scope.SELF, balance = 0, ingresos = 10_000_000, egresos = 4_000_000),
        accounts = cuentas,
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

    private val unaCuenta = listOf(Account(id = "a1", name = "Ahorros", type = AccountType.SAVINGS, balance = 2_345_678))
    private val dosCuentas = listOf(
        Account(id = "a1", name = "Ahorros", type = AccountType.SAVINGS, balance = 2_000_000),
        Account(id = "a2", name = "Nequi", type = AccountType.CHECKING, balance = 345_678),
    )

    private var navegoA: Screen? = null
    private var rojo: Color = Color.Unspecified
    private var normal: Color = Color.Unspecified

    private fun montar(data: DashboardData) {
        navegoA = null
        composeRule.setContent {
            MoviTheme {
                rojo = Movi.colores.sale
                normal = Movi.colores.texto
                Column(Modifier.fillMaxSize()) {
                    HeroDeUnVistazo(
                        section = ScreenSection(type = "HERO_BALANCE"),
                        data = data,
                        conPatrimonio = false,
                        onNavigate = { navegoA = it },
                        hoy = hoy,
                    )
                    // Lo mismo que hace `PlanScreen.SeccionCuantoPuedesGastar`.
                    TarjetaDelDisponible(titulo = TITULO_CUANTO_PUEDES_GASTAR, disponible = disponibleDelInicio(data, hoy))
                }
            }
        }
        // La cifra entra contando: se deja terminar.
        composeRule.mainClock.advanceTimeBy(DURACION_DE_LA_ENTRADA_MS.toLong() + 200L)
        composeRule.waitForIdle()
    }

    private fun texto(nodo: SemanticsNode): String =
        nodo.config.getOrNull(SemanticsProperties.Text)?.joinToString("") { it.text }.orEmpty()

    private fun color(nodo: SemanticsNode): Color {
        val resultados = mutableListOf<TextLayoutResult>()
        nodo.config[SemanticsActions.GetTextLayoutResult].action?.invoke(resultados)
        return resultados.single().layoutInput.style.color
    }

    private fun cifraDeHoy(): SemanticsNode =
        composeRule.onNodeWithTag(TAG_CIFRA_TE_QUEDAN, useUnmergedTree = true).fetchSemanticsNode()

    @Test
    fun `te quedan - Hoy dice el mismo texto que la columna Periodo de Plan`() {
        montar(datos(gasto = mapOf("2026-08-26" to 100_000L), cuentas = unaCuenta))

        val esperado = disponibleDelInicio(datos(mapOf("2026-08-26" to 100_000L), unaCuenta), hoy)!!.periodo.teQuedan
        val deHoy = texto(cifraDeHoy())
        assertEquals(formatMoneyCompact(esperado), deHoy)
        assertEquals("\$6,8M", deHoy)
        // La misma cadena está dos veces: la cifra grande de Hoy y la columna «Período» de Plan.
        assertEquals(2, composeRule.onAllNodesWithText(deHoy, useUnmergedTree = true).fetchSemanticsNodes().size)
        composeRule.onNodeWithText(ROTULO_TE_QUEDAN, useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("para 4 días · meta diaria \$222.580", useUnmergedTree = true).assertIsDisplayed()
        assertEquals(normal, color(cifraDeHoy()))
    }

    @Test
    fun `te pasaste - la misma cifra que Plan, sin signo, y el mismo rojo`() {
        montar(datos(gasto = mapOf("2026-09-01" to 7_000_000L), cuentas = unaCuenta))

        composeRule.onNodeWithText(ROTULO_TE_PASASTE, useUnmergedTree = true).assertIsDisplayed()
        assertEquals("\$100.000", texto(cifraDeHoy()))
        val columnaDePlan = composeRule.onAllNodesWithText("−\$100.000", useUnmergedTree = true).fetchSemanticsNodes()
        assertEquals(1, columnaDePlan.size, "Plan dice −\$100.000 en su columna «Período»")
        assertEquals(rojo, color(cifraDeHoy()))
        assertEquals(color(columnaDePlan.single()), color(cifraDeHoy()), "Hoy y Plan, del mismo color")
    }

    @Test
    fun `Tu plata baja a una fila, y con una sola cuenta lleva a Cuentas`() {
        montar(datos(gasto = emptyMap(), cuentas = unaCuenta))

        composeRule.onNodeWithTag(TAG_FILA_TU_PLATA_DEL_HERO, useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("\$2.345.678", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag(TAG_FILA_TU_PLATA_DEL_HERO, useUnmergedTree = true).performClick()
        assertEquals(Screen.Accounts, navegoA)
    }

    @Test
    fun `con dos cuentas, tocar Tu plata despliega el detalle sin navegar`() {
        montar(datos(gasto = emptyMap(), cuentas = dosCuentas))

        composeRule.onNodeWithText("Nequi", useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNodeWithTag(TAG_FILA_TU_PLATA_DEL_HERO, useUnmergedTree = true).performClick()
        composeRule.onNodeWithText("Nequi", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Ocultar el detalle de tu plata", useUnmergedTree = true).assertIsDisplayed()
        assertEquals(null, navegoA)
    }

    @Test
    fun `el resto de la tarjeta sigue llevando a Tus periodos`() {
        montar(datos(gasto = emptyMap(), cuentas = unaCuenta))
        composeRule.onNodeWithTag(TAG_CIFRA_TE_QUEDAN, useUnmergedTree = true).performClick()
        assertEquals(Screen.Periodos, navegoA)
        assertTrue(composeRule.onAllNodesWithText("Del 25 de agosto al 24 de septiembre", useUnmergedTree = true)
            .fetchSemanticsNodes().isNotEmpty(), "el rango va sin «quedan N días»: los días los dice el detalle")
    }
}
