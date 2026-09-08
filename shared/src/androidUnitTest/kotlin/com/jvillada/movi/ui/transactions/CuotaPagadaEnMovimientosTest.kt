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
import com.jvillada.movi.shared.model.CREDIT_RULE_PREFIX
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
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

/**
 * **La cuota que ya se pagó, en la pantalla.**
 *
 * El dueño lo dijo mirando su plata: *«"Ya ocurrieron · 1" esto es falso, de hecho todos los que
 * registran movimientos ocurrieron»*. El server ya deriva esas ocurrencias del movimiento que bajó
 * la deuda (ver `PagosDeDeuda.kt`); lo que se prueba acá es la otra mitad, la que se ve:
 *
 *  - la fila **aparece** en «Ya ocurrieron», y dice que sale de un movimiento;
 *  - y **no ofrece «Deshacer»**. No hay ningún sello que borrar: el DELETE contestaría 404 y la
 *    fila se quedaría igual. Un control muerto es peor que la ausencia del control, y en este repo
 *    eso ya pasó una vez.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h731dp-xhdpi")
class CuotaPagadaEnMovimientosTest {

    @get:Rule val composeRule = createComposeRule()

    private val bancolombia = Account("acc-banco", "Bancolombia", AccountType.SAVINGS, 1_000_000L, "COP")

    /** La regla sintética de un crédito: no existe en `recurring_rules`, la arma el server. */
    private val cuotaDelCredito = RecurringRule(
        id = "${CREDIT_RULE_PREFIX}acc-crediagil",
        name = "Cuota Crediágil 3090",
        category = "Créditos",
        amount = 26_485L,
        dayOfMonth = 15,
        type = TransactionType.EXPENSE,
    )

    /** Una regla real sellada a mano, para tener las DOS formas en la misma sección. */
    private val gimnasio = RecurringRule(
        id = "rr_gimnasio", name = "Gimnasio Cami", category = "Salud",
        amount = 120_000L, dayOfMonth = 3, type = TransactionType.EXPENSE,
    )

    private var desmarcadas = 0

    private inner class Repo : RepositorioDePrueba() {
        override suspend fun getAccounts(): List<Account> = listOf(bancolombia)
        override suspend fun getEventsByDay(): List<EventDay> = emptyList()
        override suspend fun getCardPaymentCandidates(): List<FinancialEvent> = emptyList()
        override suspend fun getRecurringRules(): List<RecurringRule> = listOf(gimnasio)
        override suspend fun getSubscriptions(): SubscriptionsResult =
            SubscriptionsResult(emptyList(), monthlyTotalCop = 0L)

        // Las dos ya rodaron al mes que viene: pagadas, no urgen. Siguen en la respuesta —el
        // server manda una entrada por regla— que es de donde «Ya ocurrieron» saca el nombre.
        override suspend fun getUpcomingPayments(): List<UpcomingPayment> = listOf(
            UpcomingPayment(cuotaDelCredito, "2026-10-15", daysUntil = 38, status = PaymentStatus.UPCOMING),
            UpcomingPayment(gimnasio, "2026-10-03", daysUntil = 26, status = PaymentStatus.UPCOMING),
        )

        override suspend fun getOccurrenceStates(): List<OccurrenceState> = listOf(
            // Derivada: la escribió el pago, no el dueño.
            OccurrenceState(
                ruleId = cuotaDelCredito.id, period = "2026-09", dueDate = "2026-09-15",
                occurred = true, eventId = "ev-cuota-deuda", derivadaDeUnMovimiento = true,
            ),
            // Sellada a mano: esta sí se deshace.
            OccurrenceState(
                ruleId = gimnasio.id, period = "2026-09", dueDate = "2026-09-03",
                occurred = true, eventId = null,
            ),
        )

        override suspend fun unmarkOccurrence(ruleId: String, period: String) {
            desmarcadas++
        }
    }

    @Before
    fun preparar() {
        RecurringOfferGate.clear()
        Repositories.sustitutoDePrueba = Repo()
        desmarcadas = 0
    }

    @After
    fun limpiar() {
        Repositories.sustitutoDePrueba = null
        RecurringOfferGate.clear()
    }

    private fun montar() {
        composeRule.setContent {
            MoviTheme {
                Box(Modifier.fillMaxSize()) {
                    TransactionsScreen(onNavigate = {}, chipInicial = CHIP_RECURRENTES)
                }
            }
        }
    }

    private fun esperarTexto(texto: String) {
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText(texto, substring = true, useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun `la cuota pagada aparece en Ya ocurrieron diciendo que la prueba un movimiento`() {
        montar()
        esperarTexto("YA OCURRIERON")

        composeRule.onNodeWithText("Cuota Crediágil 3090", useUnmergedTree = true).assertIsDisplayed()
        composeRule
            .onNodeWithText("Ya ocurrió en septiembre · lo prueba un movimiento", useUnmergedTree = true)
            .assertIsDisplayed()
    }

    /**
     * **El control muerto que no puede existir.** Una ocurrencia derivada no se desmarca: para
     * quitarla hay que borrar el movimiento, y la fila lo dice en vez de ofrecer un botón que no
     * haría nada.
     */
    @Test
    fun `la cuota pagada no ofrece Deshacer`() {
        montar()
        esperarTexto("YA OCURRIERON")

        composeRule.onNodeWithText("Se quita borrando", substring = true, useUnmergedTree = true)
            .assertIsDisplayed()
        // Hay exactamente UN «Deshacer» en la sección, y es el del sello a mano.
        assertEquals(
            1,
            composeRule.onAllNodesWithText("Deshacer", useUnmergedTree = true).fetchSemanticsNodes().size,
            "Solo la ocurrencia sellada a mano puede ofrecer «Deshacer»",
        )
    }

    /** Y el «Deshacer» que sí existe sigue funcionando: esto no rompió el camino de vuelta. */
    @Test
    fun `el sello a mano sigue teniendo su Deshacer`() {
        montar()
        esperarTexto("YA OCURRIERON")

        composeRule.onNodeWithText("Deshacer", useUnmergedTree = true).performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) { desmarcadas == 1 }
        assertEquals(1, desmarcadas)
    }
}
