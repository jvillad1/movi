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
import com.jvillada.movi.shared.model.RecurringRule
import com.jvillada.movi.shared.model.SubConfidence
import com.jvillada.movi.shared.model.SubStatus
import com.jvillada.movi.shared.model.Subscription
import com.jvillada.movi.shared.model.SubscriptionsResult
import com.jvillada.movi.shared.model.UpcomingPayment
import com.jvillada.movi.theme.MoviTheme
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * # «Suscripciones activas» arranca plegada, y plegada sigue diciendo lo que importa
 *
 * El dueño, mirando Recurrentes: *«Debemos dejar que suscripciones sea una opción de filtro o de
 * menú colapsable dentro de recurrentes»*. Con varios cobros activos la sección medía más que todo
 * lo demás junto, y la enorme mayoría de las veces que se abre esa pantalla no es para revisar el
 * inventario: es para ver **qué vence y qué falta confirmar**.
 *
 * Lo que hay que poder afirmar, y por eso hace falta montarla de verdad: que plegar **no esconde
 * información, solo filas**. La cifra que se mira de reojo —cuánto suman al mes— sigue a la vista.
 * Sin eso, plegar convertiría a la sección en un cartel mudo.
 *
 * `SuscripcionesActivasEnMovimientosTest` prueba las filas y las abre en su `@Before`; esta clase
 * prueba justamente lo contrario, que por defecto no estén.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = AVD_PLEGABLES)
class SuscripcionesPlegablesTest {

    @get:Rule val composeRule = createComposeRule()

    private val bancolombia = Account("acc-banco", "Bancolombia", AccountType.SAVINGS, 1_000_000L, "COP")

    private fun cobro(id: String, nombre: String, monto: Long, dia: Int) = Subscription(
        id = id,
        merchantKey = nombre.lowercase(),
        displayName = nombre,
        amount = monto,
        currency = "COP",
        dayOfMonth = dia,
        status = SubStatus.CONFIRMED,
        confidence = SubConfidence.HIGH,
        firstSeen = 0L,
        lastSeen = 0L,
        occurrences = 3,
        accountId = bancolombia.id,
    )

    private val activas = listOf(
        cobro("s1", "Netflix", 44_900L, 5),
        cobro("s2", "Spotify", 22_900L, 12),
    )

    @Before
    fun montar() {
        RecurringOfferGate.clear()
        Repositories.sustitutoDePrueba = object : RepositorioDePrueba() {
            override suspend fun getAccounts(): List<Account> = listOf(bancolombia)
            override suspend fun getEventsByDay(): List<EventDay> = emptyList()
            override suspend fun getCardPaymentCandidates(): List<FinancialEvent> = emptyList()
            override suspend fun getRecurringRules(): List<RecurringRule> = emptyList()
            override suspend fun getSubscriptions(): SubscriptionsResult =
                SubscriptionsResult(activas, monthlyTotalCop = 67_800L, usdToCop = 4_000.0)
            override suspend fun getUpcomingPayments(): List<UpcomingPayment> = emptyList()
            override suspend fun getOccurrenceStates(): List<OccurrenceState> = emptyList()
        }
        composeRule.setContent {
            MoviTheme { Box(Modifier.fillMaxSize()) { TransactionsScreen(onNavigate = {}) } }
        }
        esperarTexto("Sin movimientos aún")
        composeRule.onNodeWithText("Recurrentes", useUnmergedTree = true).performClick()
        esperarTexto("SUSCRIPCIONES ACTIVAS")
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

    @Test
    fun `arranca plegada, sin las filas`() {
        composeRule.onNodeWithText("Netflix", useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNodeWithText("Spotify", useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNodeWithText("Quitar", useUnmergedTree = true).assertDoesNotExist()
    }

    /**
     * **Plegar no esconde información, solo filas.** El total del mes es lo que el dueño mira de
     * reojo, y es una cifra que no aparece en ninguna otra parte de la pantalla: «Gastos
     * recurrentes», arriba, mezcla reglas, cuotas de créditos y suscripciones.
     */
    @Test
    fun `plegada dice cuantas son y cuanto suman al mes`() {
        composeRule.onNodeWithText("2 cobros · $67.800 al mes", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun `Ver abre las filas y Ocultar las vuelve a cerrar`() {
        composeRule.onNodeWithText("Ver", useUnmergedTree = true).performClick()
        esperarTexto("Netflix")

        composeRule.onNodeWithText("Netflix", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("Spotify", useUnmergedTree = true).assertIsDisplayed()

        composeRule.onNodeWithText("Ocultar", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Netflix", useUnmergedTree = true).assertDoesNotExist()
        // Y al cerrarse vuelve el resumen, no un hueco.
        composeRule.onNodeWithText("2 cobros · $67.800 al mes", useUnmergedTree = true).assertIsDisplayed()
    }

    /** Tocar el resumen también abre: el renglón entero es el botón, no solo la palabra. */
    @Test
    fun `tocar el resumen tambien abre`() {
        composeRule.onNodeWithText("2 cobros · $67.800 al mes", useUnmergedTree = true).performClick()
        esperarTexto("Netflix")
        composeRule.onNodeWithText("Netflix", useUnmergedTree = true).assertIsDisplayed()
    }
}

/** El mismo tamaño de pantalla que usan las otras pruebas de esta carpeta (privado por archivo). */
private const val AVD_PLEGABLES = "w411dp-h731dp-xhdpi"
