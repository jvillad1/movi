package com.jvillada.movi.ui.dashboard

import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.unit.height
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.RepositorioDePrueba
import com.jvillada.movi.data.SessionManager
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.CardSummary
import com.jvillada.movi.shared.model.CreditSummary
import com.jvillada.movi.shared.model.DashboardSummary
import com.jvillada.movi.shared.model.FinanceSummary
import com.jvillada.movi.shared.model.GastoDelDiaADia
import com.jvillada.movi.shared.model.OccurrenceState
import com.jvillada.movi.shared.model.Scope
import com.jvillada.movi.shared.model.ScreenDefinition
import com.jvillada.movi.shared.model.UpcomingPayment
import com.jvillada.movi.shared.model.UserProfile
import com.jvillada.movi.theme.MoviTheme
import com.jvillada.movi.ui.sdui.TAG_HEROE_EN_TARJETA
import kotlinx.coroutines.CompletableDeferred
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * # Revisión de la 78 · el héroe no salta cuando llega «¿Me alcanza?»
 *
 * El renglón sale del supuesto del gasto del día a día, que se pedía **después** del resto del Inicio:
 * el héroe crecía 66 px cuando llegaba. Ahora va en el primer lote ([DashboardData.gastoDelDiaADia]).
 * Con esa lectura demorada a propósito (la más lenta del lote), cuadro por cuadro, la tarjeta del héroe
 * nunca se aparta más de 8 dp de su alto final.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w390dp-h2400dp-xhdpi")
class ElHeroeNoSaltaConLatenciaTest {

    @get:Rule val composeRule = createComposeRule()

    private val elGasto = CompletableDeferred<Unit>()

    @Before
    fun entrar() {
        InstantaneaDelInicio.sustitutoDePrueba = instantaneaEnMemoria(mutableMapOf())
        SessionManager.save(token = "tok", userId = "u1", name = "Juan", email = "juan@ejemplo.com")
        Repositories.sustitutoDePrueba = object : RepositorioDePrueba() {
            override suspend fun getScreen(slug: String, cachedVersion: Int?): ScreenDefinition? = null
            override suspend fun getUserProfile() = UserProfile(id = "u1", email = "juan@ejemplo.com", name = "Juan", avatarColor = "#1D4E9E", periodCutoffDay = 25)
            override suspend fun getAccounts() = listOf(Account("a1", "Ahorros", AccountType.SAVINGS, 2_345_678))
            override suspend fun getFinanceSummary(scope: Scope) = FinanceSummary(Scope.SELF, balance = 2_345_678, ingresos = 10_000_000, egresos = 4_000_000)
            override suspend fun getDashboardSummary(scope: Scope) = DashboardSummary(pendingSms = 5, porConfirmarSuman = 1_100_200L)
            override suspend fun getUpcomingPayments(): List<UpcomingPayment> = emptyList()
            override suspend fun getOccurrenceStates(): List<OccurrenceState> = emptyList()
            override suspend fun getCredits(): List<CreditSummary> = emptyList()
            override suspend fun getCards(): List<CardSummary> = emptyList()
            override suspend fun getGastoDelDiaADia(): GastoDelDiaADia {
                elGasto.await()
                return GastoDelDiaADia(porDia = 20_000L)
            }
        }
    }

    @After
    fun salir() {
        Repositories.sustitutoDePrueba = null
        InstantaneaDelInicio.sustitutoDePrueba = null
    }

    private fun piezas(): String = listOf(
        com.jvillada.movi.ui.sdui.TAG_ESQUELETO_ME_ALCANZA, TAG_ME_ALCANZA,
        com.jvillada.movi.ui.sdui.TAG_ESQUELETO_POR_CONFIRMAR, com.jvillada.movi.ui.sdui.TAG_FRANJA_POR_CONFIRMAR,
        TAG_ESQUELETO_CIFRA_DEL_HERO, TAG_CIFRA_TE_QUEDAN,
    ).filter { composeRule.onAllNodesWithTag(it, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }.joinToString("+")

    private fun altoDelHeroe(): Float? =
        composeRule.onAllNodesWithTag(TAG_HEROE_EN_TARJETA).fetchSemanticsNodes().firstOrNull()?.let { nodo ->
            composeRule.onAllNodesWithTag(TAG_HEROE_EN_TARJETA)[0].getUnclippedBoundsInRoot().height.value.takeIf { nodo.size.height > 0 }
        }

    @Test
    fun `con el supuesto del gasto demorado, el heroe no se aparta mas de 8 dp de su alto final`() {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent { MoviTheme { DashboardScreen(onNavigate = {}) } }
        val altos = mutableListOf<Float>()
        val vistos = mutableListOf<String>()
        // Todo llega menos el supuesto: el héroe espera con su esqueleto.
        repeat(30) {
            composeRule.mainClock.advanceTimeByFrame()
            altoDelHeroe()?.let { altos += it; vistos += "$it:" + piezas() }
        }
        elGasto.complete(Unit)
        repeat(90) {
            composeRule.mainClock.advanceTimeByFrame()
            altoDelHeroe()?.let { altos += it; vistos += "$it:" + piezas() }
        }
        composeRule.mainClock.autoAdvance = true
        composeRule.waitForIdle()
        assertEquals(1, composeRule.onAllNodesWithTag(TAG_ME_ALCANZA, useUnmergedTree = true).fetchSemanticsNodes().size, "llegó el renglón")
        val final = altoDelHeroe()!!
        val peor = altos.maxOf { abs(it - final) }
        assertTrue(peor <= 8f, "El héroe se apartó $peor dp de su alto final ($final dp). Altos: ${vistos.distinct()}")
    }
}
