package com.jvillada.movi.ui.transactions

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.jvillada.movi.data.RecurringOfferGate
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.RepositorioDePrueba
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.EventDay
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.OccurrenceState
import com.jvillada.movi.shared.model.PaymentStatus
import com.jvillada.movi.shared.model.RecurringOccurrence
import com.jvillada.movi.shared.model.RecurringRule
import com.jvillada.movi.shared.model.SubscriptionsResult
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.UpcomingPayment
import com.jvillada.movi.theme.MoviTheme
import com.jvillada.movi.ui.Screen
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

/**
 * PR 3 del rediseño de Recurrentes (2026-09): «Próximos pagos» y el flujo de «¿esto ya ocurrió?»
 * montados de verdad dentro de [TransactionsScreen], bajo el chip «Recurrentes».
 *
 * Lo que prueba no es que un texto aparezca: es que **los tres botones que sellan un periodo
 * lleguen al repositorio y que la pantalla refleje lo que quedó**. Sellar algo que no ocurrió
 * apaga el aviso de una deuda real, y eso cuesta plata; por eso también se prueba el camino de
 * vuelta («Deshacer»), que en la pantalla vieja vivía en un inventario que no se mudó.
 *
 * Mismo patrón de montaje que [ResumenRecurrentesEnMovimientosTest].
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h731dp-xhdpi")
class ProximosPagosEnMovimientosTest {

    @get:Rule val composeRule = createComposeRule()

    private val bancolombia = Account("acc-banco", "Bancolombia", AccountType.SAVINGS, 1_000_000L, "COP")
    private val arriendo = RecurringRule(
        id = "rr_arriendo", name = "Arriendo", category = "Vivienda",
        amount = 1_800_000L, dayOfMonth = 5, type = TransactionType.EXPENSE,
    )

    /** El estado del periodo en juego. Mutable: sellarlo y deshacerlo lo reescriben. */
    private var estadoDelArriendo = OccurrenceState(
        ruleId = "rr_arriendo", period = "2026-09", dueDate = "2026-09-05",
        occurred = false, candidates = emptyList(),
    )

    private var marcadas = 0
    private var desmarcadas = 0

    private inner class Repo : RepositorioDePrueba() {
        override suspend fun getAccounts(): List<Account> = listOf(bancolombia)
        override suspend fun getEventsByDay(): List<EventDay> = emptyList()
        override suspend fun getCardPaymentCandidates(): List<FinancialEvent> = emptyList()
        override suspend fun getRecurringRules(): List<RecurringRule> = listOf(arriendo)
        override suspend fun getSubscriptions(): SubscriptionsResult =
            SubscriptionsResult(emptyList(), monthlyTotalCop = 0L)

        override suspend fun getUpcomingPayments(): List<UpcomingPayment> = listOf(
            UpcomingPayment(
                rule = arriendo, dueDate = "2026-09-05",
                // Vencido: `proximosQueUrgen` deja fuera lo que todavía es UPCOMING.
                daysUntil = -2, status = PaymentStatus.OVERDUE,
            ),
        )

        override suspend fun getOccurrenceStates(): List<OccurrenceState> = listOf(estadoDelArriendo)

        override suspend fun markOccurrence(ruleId: String, period: String, eventId: String?): RecurringOccurrence {
            marcadas++
            estadoDelArriendo = estadoDelArriendo.copy(occurred = true, eventId = eventId, candidates = emptyList())
            return RecurringOccurrence(ruleId = ruleId, period = period, eventId = eventId)
        }

        override suspend fun unmarkOccurrence(ruleId: String, period: String) {
            desmarcadas++
            estadoDelArriendo = estadoDelArriendo.copy(occurred = false, eventId = null)
        }
    }

    private fun montar(chipInicial: Int? = null) {
        composeRule.setContent {
            MoviTheme {
                Box(Modifier.fillMaxSize()) {
                    TransactionsScreen(onNavigate = { navegoA = it }, chipInicial = chipInicial)
                }
            }
        }
    }

    private var navegoA: Screen? = null

    @Before
    fun preparar() {
        RecurringOfferGate.clear()
        Repositories.sustitutoDePrueba = Repo()
        navegoA = null
        marcadas = 0
        desmarcadas = 0
        estadoDelArriendo = OccurrenceState(
            ruleId = "rr_arriendo", period = "2026-09", dueDate = "2026-09-05",
            occurred = false, candidates = emptyList(),
        )
    }

    @After
    fun limpiar() {
        Repositories.sustitutoDePrueba = null
        RecurringOfferGate.clear()
    }

    private fun esperarTexto(texto: String) {
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText(texto, substring = true, useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun esperarQueDesaparezca(texto: String) {
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText(texto, substring = true, useUnmergedTree = true)
                .fetchSemanticsNodes().isEmpty()
        }
    }

    private fun activarElChip() {
        composeRule.onNodeWithText("Recurrentes", useUnmergedTree = true).performClick()
    }

    @Test
    fun `el vencimiento y su propuesta abierta aparecen bajo el chip Recurrentes`() {
        montar()
        // Con el chip «Todo» no hay nada de esto: es un resumen DEL FILTRO, no una caja suelta.
        composeRule.onNodeWithText("Arriendo", useUnmergedTree = true).assertDoesNotExist()

        activarElChip()

        esperarTexto("Arriendo")
        // MinSectionHeader pinta el título en mayúsculas.
        composeRule.onNodeWithText("PRÓXIMOS", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("Vencido hace 2 días", useUnmergedTree = true).assertIsDisplayed()
        // La propuesta, con el mes que nombra y su salida sin movimiento que emparejar.
        composeRule.onNodeWithText("¿Ya pagaste el de septiembre?", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("Ya lo pagué", useUnmergedTree = true).assertIsDisplayed()
    }

    /**
     * El cierre sin movimiento: pagó en efectivo, o todavía no lo anotó. Sella el periodo, la fila
     * sale de «Próximos» (su vencimiento vigente ya es el del mes que viene) y aparece en «Ya
     * ocurrieron», que es donde vive el arrepentimiento.
     */
    @Test
    fun `Ya lo pague sella el periodo y la fila se muda a Ya ocurrieron`() {
        montar()
        activarElChip()
        esperarTexto("Ya lo pagué")

        composeRule.onNodeWithText("Ya lo pagué", useUnmergedTree = true).performClick()

        esperarTexto("YA OCURRIERON")
        assertEquals(1, marcadas)
        composeRule.onNodeWithText("Ya ocurrió en septiembre", useUnmergedTree = true).assertIsDisplayed()
        // Sellado, ya no se vuelve a preguntar.
        composeRule.onNodeWithText("¿Ya pagaste el de septiembre?", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun `Deshacer revierte el sello y la pregunta vuelve`() {
        montar()
        activarElChip()
        esperarTexto("Ya lo pagué")
        composeRule.onNodeWithText("Ya lo pagué", useUnmergedTree = true).performClick()
        esperarTexto("YA OCURRIERON")

        composeRule.onNodeWithText("Deshacer", useUnmergedTree = true).performClick()

        esperarQueDesaparezca("YA OCURRIERON")
        assertEquals(1, desmarcadas)
        esperarTexto("¿Ya pagaste el de septiembre?")
    }

    /**
     * El destino de los enlaces que antes iban a la pantalla de Recurrentes: Movimientos **con el
     * filtro puesto**. Sin esto, tocar «Ver todos» sobre un pago que vence aterrizaba en la lista
     * completa de movimientos, sin ninguna relación visible con lo que se acababa de tocar.
     */
    @Test
    fun `entrar pidiendo el chip Recurrentes lo deja activo desde el arranque`() {
        montar(chipInicial = CHIP_RECURRENTES)

        esperarTexto("Arriendo")
        composeRule.onNodeWithText("PRÓXIMOS", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("Flujo libre", useUnmergedTree = true).assertIsDisplayed()
    }
}
