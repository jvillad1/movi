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
import com.jvillada.movi.shared.model.RecurringRule
import com.jvillada.movi.shared.model.SubConfidence
import com.jvillada.movi.shared.model.SubStatus
import com.jvillada.movi.shared.model.Subscription
import com.jvillada.movi.shared.model.SubscriptionsResult
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.theme.MoviTheme
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * PR 2 del rediseño de Recurrentes (2026-09): [TransactionsScreen] montada de verdad, con el chip
 * «Recurrentes» activo, para probar lo que una función pura no puede — que el card de «Flujo
 * libre» y la sección de candidatas «por confirmar» solo aparezcan con ESE chip (ver
 * [mostrarResumenDeRecurrentes]) y que «Confirmar» de verdad mueva la candidata, no solo cambie
 * un texto en pantalla. Mismo patrón de montaje que [MovimientosPlegablesTest] y
 * [ChipRecurrentesTest] (la parte pura de este mismo cambio).
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h731dp-xhdpi")
class ResumenRecurrentesEnMovimientosTest {

    @get:Rule val composeRule = createComposeRule()

    private val bancolombia = Account("acc-banco", "Bancolombia", AccountType.SAVINGS, 1_000_000L, "COP")
    private val arriendo = RecurringRule(
        id = "rr_arriendo", name = "Arriendo", category = "Vivienda",
        amount = 1_800_000L, dayOfMonth = 5, type = TransactionType.EXPENSE,
    )

    /** Mutable a propósito: `updateSubscription` reescribe esto, y el siguiente `getSubscriptions`
     *  (el re-fetch que dispara `recurrentesReloadKey`) tiene que devolver lo que quedó. */
    private var disney = Subscription(
        id = "sub_disney", merchantKey = "disney_plus", displayName = "Disney+",
        amount = 25_900L, currency = "COP", dayOfMonth = 12, status = SubStatus.CANDIDATE,
        confidence = SubConfidence.HIGH, firstSeen = 0, lastSeen = 0, occurrences = 2,
    )

    /** Lo que el barrido encuentra cuando se toca «Buscar cobros». */
    private var barridosPedidos = 0
    private val spotify = Subscription(
        id = "sub_spotify", merchantKey = "spotify", displayName = "Spotify",
        amount = 16_900L, currency = "COP", dayOfMonth = 3, status = SubStatus.CANDIDATE,
        confidence = SubConfidence.HIGH, firstSeen = 0, lastSeen = 0, occurrences = 3,
    )

    private inner class Repo : RepositorioDePrueba() {
        override suspend fun getAccounts(): List<Account> = listOf(bancolombia)
        override suspend fun getEventsByDay(): List<EventDay> = emptyList()
        override suspend fun getCardPaymentCandidates(): List<FinancialEvent> = emptyList()
        override suspend fun getRecurringRules(): List<RecurringRule> = listOf(arriendo)
        override suspend fun getSubscriptions(): SubscriptionsResult =
            SubscriptionsResult(listOfNotNull(disney, spotify.takeIf { barridosPedidos > 0 }), monthlyTotalCop = 1_800_000L)
        override suspend fun updateSubscription(id: String, subscription: Subscription): Subscription {
            disney = subscription
            return subscription
        }
        override suspend fun detectSubscriptions(): SubscriptionsResult {
            barridosPedidos++
            return getSubscriptions()
        }
    }

    @Before
    fun montar() {
        RecurringOfferGate.clear()
        Repositories.sustitutoDePrueba = Repo()
        composeRule.setContent {
            MoviTheme { Box(Modifier.fillMaxSize()) { TransactionsScreen(onNavigate = {}) } }
        }
        // Chip «Todo» de arranque, sin movimientos: es el primer texto garantizado una vez que
        // las cargas iniciales (todas async) terminaron.
        esperarTexto("Sin movimientos aún")
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
            composeRule.onAllNodesWithText(texto, useUnmergedTree = true).fetchSemanticsNodes().isEmpty()
        }
    }

    @Test
    fun `el flujo libre y las candidatas solo aparecen con el chip Recurrentes activo`() {
        composeRule.onNodeWithText("Flujo libre", useUnmergedTree = true).assertDoesNotExist()

        composeRule.onNodeWithText("Recurrentes", useUnmergedTree = true).performClick()
        esperarTexto("Flujo libre")

        composeRule.onNodeWithText("Flujo libre", useUnmergedTree = true).assertIsDisplayed()
        esperarTexto("Disney+")
        composeRule.onNodeWithText("Disney+", useUnmergedTree = true).assertIsDisplayed()
        // MinSectionHeader pinta el título en mayúsculas.
        composeRule.onNodeWithText("DETECTADAS · POR CONFIRMAR", useUnmergedTree = true).assertIsDisplayed()
    }

    /**
     * El barrido se mudó con el resto de Recurrentes. Importa que exista acá: el único disparo
     * automático del detector está en el import de extractos, y el día a día del dueño entra por
     * SMS, que no lo dispara. Sin este botón, sacar la pantalla vieja del menú lo dejaba sin
     * ninguna forma de correr el detector.
     */
    @Test
    fun `buscar cobros corre el detector y muestra lo que encuentra`() {
        composeRule.onNodeWithText("Recurrentes", useUnmergedTree = true).performClick()
        esperarTexto("Flujo libre")
        composeRule.onNodeWithText("Spotify", useUnmergedTree = true).assertDoesNotExist()

        composeRule.onNodeWithText("Buscar cobros", useUnmergedTree = true).performClick()

        esperarTexto("Spotify")
        composeRule.onNodeWithText("Spotify", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun `confirmar una candidata la saca de detectadas por confirmar`() {
        composeRule.onNodeWithText("Recurrentes", useUnmergedTree = true).performClick()
        esperarTexto("Disney+")

        composeRule.onNodeWithText("Confirmar", useUnmergedTree = true).performClick()

        esperarQueDesaparezca("DETECTADAS · POR CONFIRMAR")
        // Sale de «por confirmar», pero NO de la pantalla: confirmarla la vuelve una suscripción
        // ACTIVA, y desde el PR 5 las activas tienen dónde verse — con su etiqueta de origen y su
        // «Quitar». Antes de ese PR desaparecía sin dejar rastro, aunque siguiera sumando en el
        // «Flujo libre» de arriba.
        esperarTexto("SUSCRIPCIONES ACTIVAS")
        composeRule.onNodeWithText("Disney+", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText("Suscripción · la encontró Movi", useUnmergedTree = true).assertExists()
    }
}
