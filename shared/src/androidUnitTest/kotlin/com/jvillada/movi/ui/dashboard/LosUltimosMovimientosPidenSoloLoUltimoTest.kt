package com.jvillada.movi.ui.dashboard

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.RepositorioDePrueba
import com.jvillada.movi.data.SessionManager
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.EventDay
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.ScreenDefinition
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.theme.MoviTheme
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

/**
 * # «Últimos movimientos» no se baja la historia entera (vuelta 39)
 *
 * El Inicio pide `getUltimosDias(3)` —en la web, `/api/events/by-day?ultimos=3`— y nunca
 * `getEventsByDay()`, que en la web es la historia completa en cada carga.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w390dp-h2400dp-xhdpi")
class LosUltimosMovimientosPidenSoloLoUltimoTest {

    @get:Rule val composeRule = createComposeRule()

    private val pedidos = mutableListOf<Int>()
    private var pidioLaHistoria = false

    @Before
    fun entrar() {
        InstantaneaDelInicio.sustitutoDePrueba = instantaneaEnMemoria(mutableMapOf())
        SessionManager.save(token = "tok", userId = "u1", name = "Juan", email = "juan@ejemplo.com")
        Repositories.sustitutoDePrueba = object : RepositorioDePrueba() {
            override suspend fun getScreen(slug: String, cachedVersion: Int?): ScreenDefinition? = null
            override suspend fun getAccounts(): List<Account> = listOf(Account("a1", "Nu", AccountType.SAVINGS, 100_000))
            override suspend fun getEventsByDay(): List<EventDay> {
                pidioLaHistoria = true
                return emptyList()
            }
            override suspend fun getUltimosDias(ultimos: Int): List<EventDay> {
                pedidos += ultimos
                return listOf(
                    EventDay(
                        date = "2026-10-27",
                        total = -27_500,
                        items = listOf(
                            FinancialEvent(
                                id = "e1", accountId = "a1", type = TransactionType.EXPENSE, amount = 27_500,
                                category = "Mercado", description = "Panadería La Esquina", timestamp = 0L,
                            ),
                        ),
                    ),
                )
            }
        }
    }

    @After
    fun salir() {
        Repositories.sustitutoDePrueba = null
        InstantaneaDelInicio.sustitutoDePrueba = null
    }

    @Test
    fun `el Inicio pide los ultimos tres, no la historia entera`() {
        composeRule.setContent { MoviTheme { DashboardScreen(onNavigate = {}) } }
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("Panadería La Esquina", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        assertEquals(listOf(FILAS_DE_ULTIMOS_MOVIMIENTOS), pedidos)
        assertEquals(3, FILAS_DE_ULTIMOS_MOVIMIENTOS)
        assertEquals(false, pidioLaHistoria, "no se baja la historia entera")
    }
}
