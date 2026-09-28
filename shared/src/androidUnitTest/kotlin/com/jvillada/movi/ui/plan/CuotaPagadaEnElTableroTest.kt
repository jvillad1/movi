package com.jvillada.movi.ui.plan

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
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
import com.jvillada.movi.shared.model.PeriodSettings
import com.jvillada.movi.shared.model.periodoDeLaFecha
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
 * **La cuota que ya se pagó, en la lista del período.**
 *
 * El dueño lo dijo mirando su plata: *«"Ya ocurrieron · 1" esto es falso, de hecho todos los que
 * registran movimientos ocurrieron»*. El server deriva esas ocurrencias del movimiento que bajó la
 * deuda (ver `PagosDeDeuda.kt`); lo que se prueba acá es la mitad que se ve. Desde la ola «una sola
 * lista» ya no hay sección «Ya ocurrieron»: la cuota está en «Ya pagaste», y
 *
 *  - dice que la prueba un pago, y de cuánto;
 *  - **no ofrece nada que la quite** (ni «No fue este» ni «Quitar la marca»): no hay sello que
 *    borrar, se revierte borrando el movimiento. Un control muerto es peor que la ausencia del
 *    control, y en este repo eso ya pasó una vez;
 *  - y el sello a mano de al lado sí conserva su salida: «Quitar la marca» (el «Deshacer» de antes).
 *
 * Con el período fijo en septiembre: las fechas del fixture son de septiembre de 2026 y sin esto la
 * prueba pasaría o no según el día en que corra.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h731dp-xhdpi")
class CuotaPagadaEnElTableroTest {

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
            // Derivada: la escribió el pago, no el dueño. Y viaja con el monto — la plata que
            // salió de la cuenta, que es la cuota entera y no el abono a capital.
            OccurrenceState(
                ruleId = cuotaDelCredito.id, period = "2026-09", dueDate = "2026-09-15",
                occurred = true, eventId = "ev-cuota-deuda", derivadaDeUnMovimiento = true,
                montoDelPago = 26_485L, monedaDelPago = "COP",
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
                    TableroDeRecurrentesDePrueba(
                        ajustesDelPeriodo = PeriodSettings(),
                        periodoFijo = periodoDeLaFecha("2026-09-20", PeriodSettings()),
                        onNavigate = {},
                    )
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

    /**
     * Y la fila **dice cuánta plata fue**: el monto no filtra —un abono parcial salda el periodo
     * igual, porque movi no conoce el extracto— así que sin el número a la vista el dueño no
     * tendría cómo notar que pagó una parte. Ver `PagosDeDeuda.kt`.
     */
    @Test
    fun `la cuota pagada aparece en ya pagaste diciendo cuanto prueba el movimiento`() {
        montar()
        esperarTexto("Ya pagaste · 2")

        assertEquals(
            1,
            composeRule.onAllNodesWithText("Cuota Crediágil 3090", useUnmergedTree = true).fetchSemanticsNodes().size,
            "la cuota sale una sola vez",
        )
        composeRule.onNodeWithText("Lo prueba un pago de $26.485", useUnmergedTree = true).assertExists()
    }

    /**
     * **El control muerto que no puede existir.** Una ocurrencia derivada no se desmarca: la fila
     * no ofrece nada que la quite. El único «Quitar la marca» es el del sello a mano.
     */
    @Test
    fun `la cuota pagada no ofrece nada que la quite`() {
        montar()
        esperarTexto("Ya pagaste · 2")

        assertEquals(
            1,
            composeRule.onAllNodesWithText("Quitar la marca", useUnmergedTree = true).fetchSemanticsNodes().size,
            "solo el sello a mano puede quitarse",
        )
        assertEquals(
            0,
            composeRule.onAllNodesWithText("No fue este", useUnmergedTree = true).fetchSemanticsNodes().size,
            "una cuota probada por su pago no se discute",
        )
    }

    /** Y el sello a mano sigue teniendo su salida: esto no rompió el camino de vuelta. */
    @Test
    fun `el sello a mano sigue teniendo su quitar la marca`() {
        montar()
        esperarTexto("Marcado a mano, sin movimiento")

        composeRule.onAllNodes(hasClickAction() and hasAnyDescendant(hasText("Quitar la marca")), useUnmergedTree = true)
            .onFirst().performSemanticsAction(SemanticsActions.OnClick)

        composeRule.waitUntil(timeoutMillis = 5_000) { desmarcadas == 1 }
        assertEquals(1, desmarcadas)
    }
}
