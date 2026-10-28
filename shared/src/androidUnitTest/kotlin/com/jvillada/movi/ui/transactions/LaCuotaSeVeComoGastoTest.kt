package com.jvillada.movi.ui.transactions

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertIsDisplayed
import com.jvillada.movi.data.DiasPlegadosStore
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.RepositorioDePruebaDeMovimientos
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.CreatePagoDeCuotaRequest
import com.jvillada.movi.shared.model.CreateTransferRequest
import com.jvillada.movi.shared.model.EventDay
import com.jvillada.movi.shared.model.FinanceSummary
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.PeriodSettings
import com.jvillada.movi.shared.model.PeriodoFinanciero
import com.jvillada.movi.shared.model.Scope
import com.jvillada.movi.shared.model.defaultDashboardDefinition
import com.jvillada.movi.shared.model.desglosarCuota
import com.jvillada.movi.shared.model.pagoDeCuotaLegs
import com.jvillada.movi.shared.model.transferLegsFor
import com.jvillada.movi.shared.time.epochMillisToAppDate
import com.jvillada.movi.theme.MoviTheme
import com.jvillada.movi.ui.dashboard.DashboardData
import com.jvillada.movi.ui.sdui.SduiRenderer
import kotlinx.datetime.Clock
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertTrue

/**
 * # Vuelta 50: la cuota de un crédito se ve como lo que es, un gasto
 *
 * En «Últimos movimientos» del Inicio la «Cuota de crédito $980.000» salía como un traspaso —flechas,
 * sin signo y en gris—, igual que el pago de tarjeta. Por la regla del dueño la cuota sí es gasto. Acá,
 * con las patas que escriben los constructores de verdad ([pagoDeCuotaLegs], [transferLegsFor]):
 * en Actividad y en el Inicio la cuota dice «−$980.000», el pago de tarjeta y el traspaso van sin signo,
 * y el «Gastaste» del día suma la cuota y no el pago de tarjeta.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h1600dp-xhdpi")
class LaCuotaSeVeComoGastoTest {

    @get:Rule val composeRule = createComposeRule()

    private val banco = Account("acc-banco", "Bancolombia Ahorros", AccountType.SAVINGS, 15_534_069L)
    private val cdt = Account("acc-cdt", "CDT", AccountType.INVESTMENT, 5_000_000L)
    private val credito = Account("acc-credito", "Libre inversión 9695", AccountType.LOAN, 40_710_555L)
    private val tarjeta = Account("acc-tarjeta", "AMEX 9208", AccountType.CREDIT_CARD, 1_818_701L)
    private val cuentas = listOf(banco, cdt, credito, tarjeta)

    private val ahora = Clock.System.now().toEpochMilliseconds()

    private fun pago(id: String, deuda: Account, monto: Long, minutosAntes: Int): List<FinancialEvent> {
        val (dinero, pataDeuda) = pagoDeCuotaLegs(
            CreatePagoDeCuotaRequest(
                fromAccountId = banco.id,
                debtAccountId = deuda.id,
                amount = monto,
                timestamp = ahora - minutosAntes * 60_000L,
                transferId = "tr-$id",
                fromEventId = "$id-sale",
                toEventId = "$id-entra",
            ),
            banco,
            deuda,
            desglosarCuota(monto, deuda.type, deuda.balance, 11.27, null, null, yaCobradoEnElMes = 0L),
        )
        return listOf(dinero, pataDeuda)
    }

    private fun traspaso(minutosAntes: Int): List<FinancialEvent> {
        val (sale, entra) = transferLegsFor(
            CreateTransferRequest(
                transferId = "tr-traspaso",
                fromEventId = "traspaso-sale",
                toEventId = "traspaso-entra",
                fromAccountId = banco.id,
                toAccountId = cdt.id,
                amount = 50_000L,
                timestamp = ahora - minutosAntes * 60_000L,
            ),
            banco,
            cdt,
        )
        return listOf(sale, entra)
    }

    /** El día del dueño: la cuota, el pago de una tarjeta y un traspaso, el más reciente primero. */
    private val elDia: List<FinancialEvent> =
        pago("cuota", credito, 980_000L, 1) + pago("tarjeta", tarjeta, 100_000L, 2) + traspaso(3)

    @Before
    fun limpiarAntes() {
        DiasPlegadosStore.clear()
    }

    @After
    fun limpiar() {
        Repositories.sustitutoDePrueba = null
        DiasPlegadosStore.clear()
    }

    private fun hay(texto: String) =
        composeRule.onAllNodesWithText(texto, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun `en Actividad la cuota dice menos 980000 y el Gastaste del dia la suma, sin el pago de tarjeta`() {
        Repositories.sustitutoDePrueba = object : RepositorioDePruebaDeMovimientos() {
            override suspend fun getAccounts(): List<Account> = cuentas
            override suspend fun getEventsByDay(): List<EventDay> =
                listOf(EventDay(date = epochMillisToAppDate(ahora).toString(), total = 0L, items = elDia))
            override suspend fun getCardPaymentCandidates(): List<FinancialEvent> = emptyList()
        }
        composeRule.setContent {
            MoviTheme { Box(Modifier.fillMaxSize()) { TransactionsScreen(onNavigate = {}) } }
        }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag(TAG_ENCABEZADO_DE_DIA, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.waitUntil(timeoutMillis = 5_000) { hay("Cuota de crédito") }
        composeRule.waitForIdle()

        // La fila de la cuota: un gasto, con «−».
        composeRule.onNodeWithText("Cuota de crédito", useUnmergedTree = true).assertIsDisplayed()
        assertTrue(hay("−\$980.000"), "la cuota va con «−»")
        // El pago de tarjeta y el traspaso, sin signo.
        composeRule.onNodeWithText("Pago de tarjeta", useUnmergedTree = true).assertIsDisplayed()
        assertTrue(hay("\$100.000"), "el pago de tarjeta se ve")
        assertTrue(!hay("−\$100.000") && !hay("+\$100.000"), "el pago de tarjeta no lleva signo")
        assertTrue(hay("\$50.000") && !hay("−\$50.000"), "el traspaso no lleva signo")
        // «Gastaste $980.000»: la cuota sí, el pago de tarjeta no (sería $1.080.000).
        composeRule.onNodeWithText("Gastaste", useUnmergedTree = true).assertIsDisplayed()
        assertTrue(hay("\$980.000"), "el encabezado del día suma la cuota")
        assertTrue(!hay("\$1.080.000") && !hay("\$1.030.000"), "el encabezado no suma el pago de tarjeta ni el traspaso")
    }

    @Test
    fun `en Ultimos movimientos del Inicio la cuota dice menos 980000 y el pago de tarjeta no lleva signo`() {
        val datos = DashboardData(
            summary = FinanceSummary(scope = Scope.SELF, balance = 0, ingresos = 0, egresos = 0),
            accounts = cuentas,
            credits = emptyList(),
            cards = emptyList(),
            upcoming = emptyList(),
            ocurrencias = emptyList(),
            periodoActual = PeriodoFinanciero(2026, 10),
            ajustesDePeriodo = PeriodSettings(),
            ultimosMovimientos = elDia,
        )
        composeRule.setContent {
            MoviTheme {
                Box(Modifier.fillMaxSize()) {
                    SduiRenderer(
                        definition = defaultDashboardDefinition(),
                        data = datos,
                        modifier = Modifier.fillMaxSize(),
                        onNavigate = {},
                        conBanda = true,
                    )
                }
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Cuota de crédito", useUnmergedTree = true).assertIsDisplayed()
        assertTrue(hay("−\$980.000"), "la cuota va con «−»")
        assertTrue(hay("\$100.000") && !hay("−\$100.000"), "el pago de tarjeta no lleva signo")
        assertTrue(hay("\$50.000") && !hay("−\$50.000"), "el traspaso no lleva signo")
    }
}
