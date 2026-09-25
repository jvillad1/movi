package com.jvillada.movi.ui.transactions

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import com.jvillada.movi.data.DiasPlegadosStore
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.RepositorioDePrueba
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.EventDay
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.PeriodSettings
import com.jvillada.movi.shared.model.PeriodoFinanciero
import com.jvillada.movi.shared.model.ReconciliationStatus
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.UserProfile
import com.jvillada.movi.shared.model.nombreDe
import com.jvillada.movi.shared.model.periodoActual
import com.jvillada.movi.shared.model.periodoAnterior
import com.jvillada.movi.shared.model.rangoLegibleDe
import com.jvillada.movi.shared.model.ventanaDe
import com.jvillada.movi.shared.time.epochMillisToAppDate
import com.jvillada.movi.theme.MoviTheme
import com.jvillada.movi.ui.NavStack
import com.jvillada.movi.ui.Screen
import com.jvillada.movi.ui.destinoVigente
import kotlinx.datetime.Clock
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * # Movimientos abre en el período que se le pide
 *
 * «Ver los movimientos de este período», desde el detalle de un período, pide
 * `Screen.Transactions(periodoInicial = "…")`. Sin el parámetro todo sigue igual que antes: el
 * período de hoy.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h731dp-xhdpi")
class MovimientosEnUnPeriodoPedidoTest {

    @get:Rule val composeRule = createComposeRule()

    private val banco = Account("acc-banco", "Bancolombia", AccountType.SAVINGS, 1_000_000L, "COP")
    private val ajustes = PeriodSettings(cutoffDay = 26)
    private val hoy = periodoActual(Clock.System.now().toEpochMilliseconds(), ajustes)
    private val anterior = periodoAnterior(hoy)

    /** Un gasto a mitad del período anterior: se ve allá y no en el de hoy. */
    private val gastoDelAnterior = FinancialEvent(
        id = "e-ant",
        accountId = banco.id,
        type = TransactionType.EXPENSE,
        amount = 77_000L,
        category = "Comida",
        description = "Almuerzo del período anterior",
        timestamp = ventanaDe(anterior, ajustes).first + 10L * 24 * 60 * 60 * 1000,
        reconciliationStatus = ReconciliationStatus.RECONCILED,
        countsAsCashFlow = true,
    )

    private fun montar(periodoInicial: String?) {
        DiasPlegadosStore.clear()
        Repositories.sustitutoDePrueba = object : RepositorioDePrueba() {
            override suspend fun getAccounts(): List<Account> = listOf(banco)
            override suspend fun getEventsByDay(): List<EventDay> = listOf(
                EventDay(
                    date = epochMillisToAppDate(gastoDelAnterior.timestamp).toString(),
                    total = -77_000L,
                    items = listOf(gastoDelAnterior),
                ),
            )
            override suspend fun getCardPaymentCandidates(): List<FinancialEvent> = emptyList()
            override suspend fun getUserProfile(): UserProfile = UserProfile(
                id = "u1", email = "jvillad1@gmail.com", name = "Juan", avatarColor = "#FF0000",
                periodCutoffDay = 26,
            )
        }
        composeRule.setContent {
            MoviTheme {
                Box(Modifier.fillMaxSize()) {
                    TransactionsScreen(onNavigate = {}, periodoInicial = periodoInicial)
                }
            }
        }
        composeRule.waitForIdle()
    }

    @After
    fun limpiar() {
        Repositories.sustitutoDePrueba = null
        DiasPlegadosStore.clear()
    }

    private fun rotulo(periodo: PeriodoFinanciero) = nombreDe(periodo).replaceFirstChar { it.uppercase() }

    private fun hay(texto: String): Boolean =
        composeRule.onAllNodesWithText(texto, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun `con un periodo pedido arranca en ese periodo y muestra sus movimientos`() {
        montar(anterior.prefijo)
        composeRule.waitUntil(timeoutMillis = 5_000) { hay("Almuerzo del período anterior") }
        assertTrue(hay(rotulo(anterior)))
        assertTrue(!hay(rotulo(hoy)))
    }

    @Test
    fun `sin periodo pedido arranca en el de hoy, como siempre`() {
        montar(null)
        // La línea del rango aparece recién cuando el perfil (corte 26) contestó.
        composeRule.waitUntil(timeoutMillis = 5_000) { hay(rangoLegibleDe(hoy, ajustes)!!) }
        assertTrue(hay(rotulo(hoy)))
        assertTrue(!hay("Almuerzo del período anterior"))
    }

    @Test
    fun `el periodo pedido no cambia adonde llevan los chips que se mudaron`() {
        assertEquals(Screen.Transactions(), Screen.Transactions(chipInicial = null, periodoInicial = null))
        val pila = mutableListOf<Screen>(Screen.Dashboard)
        NavStack.navegar(pila, Screen.Transactions(chipInicial = CHIP_RECURRENTES, periodoInicial = "2026-09"))
        assertEquals(destinoVigente(Screen.Transactions(CHIP_RECURRENTES)), pila.last())
    }
}
