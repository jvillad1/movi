package com.jvillada.movi.ui.dashboard

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.RepositorioDePrueba
import com.jvillada.movi.data.SessionManager
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.CardSummary
import com.jvillada.movi.shared.model.CierreDelPeriodo
import com.jvillada.movi.shared.model.CifrasDelAnterior
import com.jvillada.movi.shared.model.CreditSummary
import com.jvillada.movi.shared.model.DashboardSummary
import com.jvillada.movi.shared.model.FinanceSummary
import com.jvillada.movi.shared.model.Scope
import com.jvillada.movi.shared.model.ScreenDefinition
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * **«Cerró tu período» en el Inicio**, montado entero. Ola R1: es una tarjeta de «Movi propone hoy»
 * con la frase del resumen; «Ver el período» abre el detalle del período cerrado, y «Ya lo vi» la saca
 * en el mismo toque y le avisa al server. Sin cierre (204), no hay tarjeta.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w390dp-h2400dp-xhdpi")
class CierreEnHoyTest {

    @get:Rule val composeRule = createComposeRule()

    private val septiembre = CierreDelPeriodo(
        periodo = "2026-09",
        nombre = "Septiembre 2026",
        entro = 22_500_000,
        salio = 18_200_000,
        anterior = CifrasDelAnterior("2026-08", "Agosto 2026", 21_500_000, 18_700_000),
        quedoDelDisponible = 420_000,
        diaDelPeriodoNuevo = 1,
    )

    private val descartados = mutableListOf<String>()
    private val navegado = mutableListOf<Screen>()

    private fun entrar(cierre: CierreDelPeriodo?) {
        InstantaneaDelInicio.sustitutoDePrueba = instantaneaEnMemoria(mutableMapOf())
        SessionManager.save(token = "tok", userId = "u1", name = "Juan", email = "juan@ejemplo.com")
        Repositories.sustitutoDePrueba = object : RepositorioDePrueba() {
            override suspend fun getScreen(slug: String, cachedVersion: Int?): ScreenDefinition? = null
            override suspend fun getFinanceSummary(scope: Scope) = FinanceSummary(scope = Scope.SELF, balance = 0, ingresos = 0, egresos = 0, eventCount = 12)
            override suspend fun getAccounts(): List<Account> = listOf(Account("a1", "Ahorros", AccountType.SAVINGS, 1_000_000))
            override suspend fun getCredits(): List<CreditSummary> = emptyList()
            override suspend fun getCards(): List<CardSummary> = emptyList()
            override suspend fun getUpcomingPayments(): List<UpcomingPayment> = emptyList()
            override suspend fun getDashboardSummary(scope: Scope) = DashboardSummary()
            override suspend fun getCierreDelPeriodo(): CierreDelPeriodo? = cierre
            override suspend fun descartarCierre(periodo: String) { descartados += periodo }
        }
        composeRule.setContent { MoviTheme { DashboardScreen(onNavigate = { navegado += it }) } }
        composeRule.waitForIdle()
    }

    @After
    fun salir() {
        Repositories.sustitutoDePrueba = null
        InstantaneaDelInicio.sustitutoDePrueba = null
    }

    private fun tarjetas(): Int =
        composeRule.onAllNodesWithText("Resumen de septiembre 2026", useUnmergedTree = true).fetchSemanticsNodes().size

    private fun hay(texto: String): Boolean =
        composeRule.onAllNodesWithText(texto, substring = true, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun `la tarjeta dice como cerro el periodo y abre su detalle`() {
        entrar(septiembre)
        assertEquals(1, tarjetas())
        assertTrue(hay("Cerró tu período: entró \$22.500.000, salió \$18.200.000"))
        // Va en el feed de propuestas, no suelta arriba de todo.
        assertEquals(1, composeRule.onAllNodesWithTag(com.jvillada.movi.ui.components.ds.TAG_TARJETA_DE_PROPUESTA, useUnmergedTree = true)
            .fetchSemanticsNodes().size)
        composeRule.onNodeWithText("Ver el período", useUnmergedTree = true).performClick()
        assertEquals(Screen.DetalleDePeriodo("2026-09"), navegado.last())
    }

    @Test
    fun `ya lo vi la saca y le avisa al server`() {
        entrar(septiembre)
        composeRule.onNodeWithText("Ya lo vi", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()
        assertEquals(0, tarjetas())
        assertEquals(listOf("2026-09"), descartados)
        assertNull(DashboardDataCache.data?.cierre, "volver al Inicio no la vuelve a pintar")
    }

    @Test
    fun `sin cierre no hay tarjeta, ni pasados los primeros dias`() {
        entrar(null)
        assertEquals(0, tarjetas())
    }

    @Test
    fun `pasados los primeros dias tampoco`() {
        entrar(septiembre.copy(diaDelPeriodoNuevo = 6))
        assertEquals(0, tarjetas())
    }
}
