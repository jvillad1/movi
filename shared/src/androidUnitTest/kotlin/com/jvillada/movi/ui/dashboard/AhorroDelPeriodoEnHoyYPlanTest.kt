package com.jvillada.movi.ui.dashboard

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
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
 * # El ahorro del período en Hoy y en Plan
 *
 * El dueño eligió apartar $2M cada período (Ajustes → «Ahorro de cada período»). El héroe de Hoy y la
 * tarjeta de Plan, montados con los mismos datos, dicen «Te quedan» ya sin el ahorro —la misma cifra,
 * y su «$X por día»—; el héroe no agrega texto, y «¿De dónde sale?» de Plan nombra el tramo para que el
 * desglose sume. Sin elegir nada, todo da exactamente lo de antes.
 *
 * Escenario de `TeQuedanEnHoyEnPantallaTest`: lunes 21-sep-2026 (período del 25-ago al 24-sep), $10M
 * que entraron, $3,1M de arriendo ya pagado y $100.000 gastados.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w390dp-h2400dp-xhdpi")
class AhorroDelPeriodoEnHoyYPlanTest {

    @get:Rule val composeRule = createComposeRule()

    private val hoy = LocalDate(2026, 9, 21)

    private fun datos(ahorro: Long) = DashboardData(
        summary = FinanceSummary(scope = Scope.SELF, balance = 0, ingresos = 10_000_000, egresos = 4_000_000),
        accounts = listOf(Account(id = "a1", name = "Ahorros", type = AccountType.SAVINGS, balance = 6_900_000)),
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
        gastoVariablePorDia = mapOf("2026-08-26" to 100_000L),
        ajustesDePeriodo = PeriodSettings(cutoffDay = 25),
        periodoActual = PeriodoFinanciero(2026, 9),
        plataDelDisponible = PlataDelDisponible(saldoAlInicio = 0, entradas = 10_000_000, guardado = 0, ahorroDelPeriodo = ahorro),
    )

    private fun montar(data: DashboardData) {
        composeRule.setContent {
            MoviTheme {
                Column(Modifier.fillMaxSize()) {
                    HeroDeUnVistazo(section = ScreenSection(type = "HERO_BALANCE"), data = data, onNavigate = {}, hoy = hoy)
                    TarjetaDelDisponible(titulo = TITULO_CUANTO_PUEDES_GASTAR, disponible = disponibleDelInicio(data, hoy))
                }
            }
        }
        composeRule.mainClock.advanceTimeBy(DURACION_DE_LA_ENTRADA_MS.toLong() + 200L)
        composeRule.waitForIdle()
    }

    private fun cifraDeHoy(): String =
        composeRule.onNodeWithTag(TAG_CIFRA_TE_QUEDAN, useUnmergedTree = true).fetchSemanticsNode()
            .config.getOrNull(SemanticsProperties.Text)?.joinToString("") { it.text }.orEmpty()

    private fun cuantos(texto: String) =
        composeRule.onAllNodesWithText(texto, useUnmergedTree = true).fetchSemanticsNodes().size

    private fun hay(texto: String) =
        composeRule.onAllNodesWithText(texto, substring = true, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    /** El candado: sin elegir nada, Hoy y Plan dicen exactamente lo de antes del ahorro. */
    @Test
    fun `sin ahorro Hoy y Plan dicen lo de siempre`() {
        montar(datos(ahorro = 0))
        assertEquals("\$6.800.000", cifraDeHoy())
        // Vuelta 45: Pagos dice la misma cifra y el mismo detalle que el héroe.
        assertEquals(2, cuantos("Para 4 días · \$1,7M por día"))
        // Y es la misma cifra que con un server que no manda el campo (sin `plataDelDisponible` el
        // disponible es «ingresos menos fijos»: 10M − 3,1M, igual que acá).
        assertEquals(
            disponibleDelInicio(datos(0).copy(plataDelDisponible = null), hoy)!!.periodo.teQuedan,
            disponibleDelInicio(datos(0), hoy)!!.periodo.teQuedan,
        )
        composeRule.onNodeWithText("¿De dónde sale?", useUnmergedTree = true).performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
        assertTrue(!hay("ahorro del período"), "sin ahorro no hay tramo")
    }

    @Test
    fun `con ahorro el heroe y Plan dicen la misma cifra ya sin el ahorro`() {
        montar(datos(ahorro = 2_000_000))
        val d = disponibleDelInicio(datos(2_000_000), hoy)!!
        assertEquals(4_800_000L, d.periodo.teQuedan)

        // Hoy: la cifra entera, y debajo los días y lo que da por día, sin texto nuevo.
        assertEquals("\$4.800.000", cifraDeHoy())
        // Vuelta 45: Pagos lleva la misma cifra entera y el mismo detalle (el rótulo, dos veces).
        assertEquals(2, cuantos(ROTULO_TE_QUEDAN))
        assertEquals(2, cuantos("Para 4 días · \$1,2M por día"))
        // Hoy y Pagos (la semana, a 4 días del cierre, se calla desde la revisión de la 83).
        assertEquals(2, cuantos("\$4.800.000"))
        assertTrue(!hay("ahorro"), "el ahorro no se nombra hasta abrir «¿De dónde sale?»")
    }

    @Test
    fun `De donde sale nombra el ahorro del periodo y el desglose suma`() {
        montar(datos(ahorro = 2_000_000))
        composeRule.onNodeWithText("¿De dónde sale?", useUnmergedTree = true).performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()

        // Vuelta de diseño 28: la cascada, un renglón por tramo, con su cifra entera y su signo.
        composeRule.onNodeWithText("Tu ahorro del período", useUnmergedTree = true).assertIsDisplayed()
        assertTrue(hay("−\$2.000.000"), "el ahorro resta, con su «−»")
        assertTrue(hay("−\$3.100.000"), "los fijos también")
        // Lo que tenías + lo que entró − fijos − ahorro = el Disponible de la tarjeta.
        val d = disponibleDelInicio(datos(2_000_000), hoy)!!
        assertEquals(d.plata!!.neto + d.ingresosPorRecibir - d.fijos - d.ahorroDelPeriodo, d.disponible)
    }

    @Test
    fun `un ahorro mayor que lo libre dice que no hay margen, nombrando al ahorro`() {
        val d = disponibleDelInicio(datos(ahorro = 9_000_000), hoy)!!
        assertEquals(-2_100_000L, d.disponible)
        assertEquals(
            "Los fijos y tu ahorro del período superan lo que tenías y lo que entró por \$2,1M",
            fraseDelDisponible(d).texto,
        )
        montar(datos(ahorro = 9_000_000))
        // El héroe dice lo de siempre sin margen: «Te pasaste por» lo gastado (y Pagos, lo mismo).
        assertEquals(2, cuantos(ROTULO_TE_PASASTE))
        assertEquals("\$100.000", cifraDeHoy())
    }
}
