package com.jvillada.movi.ui.transactions

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performSemanticsAction
import com.jvillada.movi.data.DiasPlegadosStore
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.RepositorioDePruebaDeMovimientos
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.EventDay
import com.jvillada.movi.shared.model.EventOccurrenceMark
import com.jvillada.movi.shared.model.FiltroDeMovimientos
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.ReconciliationStatus
import com.jvillada.movi.shared.model.RecurringRule
import com.jvillada.movi.shared.model.SubscriptionsResult
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.UserProfile
import com.jvillada.movi.shared.time.AppTimeZone
import com.jvillada.movi.shared.time.epochMillisToAppDate
import com.jvillada.movi.theme.MoviTheme
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.atStartOfDayIn
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * # Movimientos con el filtro de un rango de «En qué se va» (vuelta 140, revisión)
 *
 * - Un filtro con los días de un rango («Comida · 3 meses») muestra todo el rango y **esconde el
 *   encabezado del período**: decir «octubre» encima de julio a noviembre mentiría.
 * - El filtro de una barra de un solo período sigue recortándose al período que se ve: con ‹, la lista
 *   del período anterior no muestra el día de la barra (la primera versión de la vuelta lo mostraba,
 *   porque medía el cruce contra el período a la vista y no contra el de la apertura).
 *
 * Corte 1: los períodos son los meses de calendario, y «hoy» es el de la máquina.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w390dp-h2400dp-xhdpi")
class MovimientosConElFiltroDeUnRangoTest {

    @get:Rule val composeRule = createComposeRule()

    private val nu = Account("acc-nu", "Nu", AccountType.SAVINGS, 1_000_000L, "COP")
    private val hoy = epochMillisToAppDate(Clock.System.now().toEpochMilliseconds())
    private val esteMes = LocalDate(hoy.year, hoy.monthNumber, 1)
    private val mesPasado = LocalDate.fromEpochDays(esteMes.toEpochDays() - 1).let { LocalDate(it.year, it.monthNumber, 1) }

    private fun gasto(id: String, dia: LocalDate, descripcion: String) = FinancialEvent(
        id = id, accountId = nu.id, type = TransactionType.EXPENSE, amount = 20_000, category = "Comida",
        description = descripcion, timestamp = dia.atStartOfDayIn(AppTimeZone.zone).toEpochMilliseconds() + 12 * 3_600_000L,
        reconciliationStatus = ReconciliationStatus.RECONCILED, countsAsCashFlow = true,
    )

    private val deEsteMes = gasto("e1", esteMes, "Carulla de este mes")
    private val delMesPasado = gasto("e2", mesPasado, "Exito del mes pasado")

    private inner class Repo : RepositorioDePruebaDeMovimientos() {
        override suspend fun getUserProfile(): UserProfile =
            UserProfile(id = "u1", email = "juan@ejemplo.com", name = "Juan", avatarColor = "morado", periodCutoffDay = 1)
        override suspend fun getAccounts(): List<Account> = listOf(nu)
        override suspend fun getEventsByDay(): List<EventDay> = listOf(
            EventDay(esteMes.toString(), 0L, listOf(deEsteMes)),
            EventDay(mesPasado.toString(), 0L, listOf(delMesPasado)),
        )
        override suspend fun getCardPaymentCandidates(): List<FinancialEvent> = emptyList()
        override suspend fun getEventOccurrenceMark(id: String): EventOccurrenceMark? = null
        override suspend fun getRecurringRules(): List<RecurringRule> = emptyList()
        override suspend fun getSubscriptions(): SubscriptionsResult = SubscriptionsResult(emptyList(), 0)
        override suspend fun getTuParteDeOtros(): List<FinancialEvent> = emptyList()
        override suspend fun getParecidos(id: String): List<FinancialEvent> = emptyList()
    }

    private fun montar(filtro: FiltroDeMovimientos) {
        DiasPlegadosStore.clear()
        Repositories.sustitutoDePrueba = Repo()
        composeRule.setContent {
            MoviTheme {
                Box(Modifier.fillMaxSize()) {
                    TransactionsScreen(onNavigate = {}, filtroInicial = filtro)
                }
            }
        }
    }

    @After
    fun limpiar() {
        Repositories.sustitutoDePrueba = null
        DiasPlegadosStore.clear()
    }

    private fun hay(texto: String) =
        composeRule.onAllNodesWithText(texto, substring = true, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun esperar(condicion: () -> Boolean) = composeRule.waitUntil(timeoutMillis = 5_000, condition = condicion)

    @Test
    fun `el filtro de un rango muestra todo el rango y esconde el encabezado del periodo`() {
        montar(FiltroDeMovimientos("Comida · rango", categoria = "Comida", desde = mesPasado.toString(), hasta = hoy.toString()))
        esperar { hay("Carulla de este mes") }
        assertTrue(hay("Exito del mes pasado"), "el rango no se recorta al período")
        assertEquals(
            0,
            composeRule.onAllNodesWithTag(TAG_LINEA_DE_PERIODO, useUnmergedTree = true).fetchSemanticsNodes().size,
            "el encabezado diría un período que no es el que se ve",
        )
    }

    @Test
    fun `el filtro de una barra de un solo periodo sigue recortado al ir al anterior`() {
        montar(FiltroDeMovimientos("1", desde = esteMes.toString(), hasta = esteMes.toString()))
        esperar { hay("Carulla de este mes") }
        assertTrue(composeRule.onAllNodesWithTag(TAG_LINEA_DE_PERIODO, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty())
        composeRule.onNode(hasClickAction() and hasContentDescription("Período anterior"))
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
        assertFalse(hay("Carulla de este mes"), "el día de la barra no es del período anterior")
    }
}
