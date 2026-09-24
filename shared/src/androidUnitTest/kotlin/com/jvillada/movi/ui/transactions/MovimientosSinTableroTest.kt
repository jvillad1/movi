package com.jvillada.movi.ui.transactions

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import com.jvillada.movi.data.RecurringOfferGate
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.RepositorioDePrueba
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.EventDay
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.OccurrenceState
import com.jvillada.movi.shared.model.ReconciliationStatus
import com.jvillada.movi.shared.model.RecurringRule
import com.jvillada.movi.shared.model.SubscriptionsResult
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.UpcomingPayment
import com.jvillada.movi.shared.time.epochMillisToAppDate
import com.jvillada.movi.theme.MoviTheme
import kotlinx.datetime.Clock
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

/**
 * **Movimientos sin el tablero de Recurrentes** (ola C).
 *
 * El tablero —qué vence, qué ya ocurrió, qué falta confirmar— se mudó a Plan · Pagos del mes. Lo que
 * esta prueba fija es lo que queda en Movimientos: **ni el chip ni el tablero**, ni siquiera si
 * alguien la abre pidiendo el chip viejo, pero **sí el ícono de repetición** en cada fila que se
 * reconoce como recurrente — que era lo único de todo aquello que hablaba de los movimientos.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h731dp-xhdpi")
class MovimientosSinTableroTest {

    @get:Rule val composeRule = createComposeRule()

    private val bancolombia = Account("acc-banco", "Bancolombia", AccountType.SAVINGS, 8_000_000L, "COP")
    private val arriendo = RecurringRule(
        id = "rr_arriendo", name = "Arriendo", category = "Vivienda",
        amount = 1_800_000L, dayOfMonth = 5, type = TransactionType.EXPENSE,
    )

    private fun gasto(id: String, descripcion: String, monto: Long) = FinancialEvent(
        id = id, accountId = bancolombia.id, type = TransactionType.EXPENSE, amount = monto,
        category = "Vivienda", description = descripcion, timestamp = 1_756_684_800_000L,
        reconciliationStatus = ReconciliationStatus.RECONCILED, countsAsCashFlow = true,
    )

    private var lecturasDeVencimientos = 0

    private inner class Repo : RepositorioDePrueba() {
        override suspend fun getAccounts(): List<Account> = listOf(bancolombia)
        override suspend fun getEventsByDay(): List<EventDay> = listOf(
            EventDay(
                date = epochMillisToAppDate(Clock.System.now().toEpochMilliseconds()).toString(),
                total = -1_850_000L,
                items = listOf(gasto("ev-arriendo", "Arriendo", 1_800_000L), gasto("ev-pan", "Panadería", 50_000L)),
            ),
        )
        override suspend fun getCardPaymentCandidates(): List<FinancialEvent> = emptyList()
        override suspend fun getRecurringRules(): List<RecurringRule> = listOf(arriendo)
        override suspend fun getSubscriptions(): SubscriptionsResult = SubscriptionsResult(emptyList(), monthlyTotalCop = 0L)
        // Contestadas: si Movimientos volviera a montar el tablero, se notaría en el contador.
        override suspend fun getUpcomingPayments(): List<UpcomingPayment> {
            lecturasDeVencimientos++
            return emptyList()
        }
        override suspend fun getOccurrenceStates(): List<OccurrenceState> = emptyList()
    }

    @Before
    fun preparar() {
        RecurringOfferGate.clear()
        Repositories.sustitutoDePrueba = Repo()
    }

    @After
    fun limpiar() {
        Repositories.sustitutoDePrueba = null
        RecurringOfferGate.clear()
    }

    private fun montar(chipInicial: Int? = null) {
        composeRule.setContent {
            MoviTheme {
                Box(Modifier.fillMaxSize()) { TransactionsScreen(onNavigate = {}, chipInicial = chipInicial) }
            }
        }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("Panadería", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        // Y que las marcas ya llegaron (se leen aparte de los movimientos).
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithContentDescription("Recurrente", useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun `los chips son Todo, Gastos e Ingresos, sin Recurrentes`() {
        montar()

        listOf("Todo", "Gastos", "Ingresos").forEach {
            composeRule.onNodeWithText(it, useUnmergedTree = true).assertIsDisplayed()
        }
        composeRule.onNodeWithText("Recurrentes", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun `el tablero no se pinta ni se lee`() {
        montar()

        composeRule.onNodeWithText("Flujo libre", useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNodeWithText("PRÓXIMOS", useUnmergedTree = true).assertDoesNotExist()
        assertEquals(0, lecturasDeVencimientos, "Movimientos no pide lo que solo el tablero usa")
    }

    /** La marca de repetición se queda: la fila del arriendo la lleva y la de la panadería no. */
    @Test
    fun `la fila que se repite sigue marcada`() {
        montar()

        composeRule.onAllNodesWithContentDescription("Recurrente", useUnmergedTree = true).assertCountEquals(1)
    }

    /**
     * Si alguien la abre pidiendo el chip viejo (un enlace que la navegación no desvió), arranca en
     * «Todo» con la lista completa — no en un filtro que ya no existe ni en un tablero a medias.
     */
    @Test
    fun `pedir el chip Recurrentes abre la lista completa`() {
        montar(chipInicial = CHIP_RECURRENTES)

        composeRule.onNodeWithText("Arriendo", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("Panadería", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("Flujo libre", useUnmergedTree = true).assertDoesNotExist()
    }
}
