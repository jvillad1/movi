package com.jvillada.movi.ui.transactions

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.jvillada.movi.data.RecurringOfferGate
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.RepositorioDePrueba
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.CARD_RULE_PREFIX
import com.jvillada.movi.shared.model.CREDIT_RULE_PREFIX
import com.jvillada.movi.shared.model.EventDay
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.OccurrenceState
import com.jvillada.movi.shared.model.PaymentStatus
import com.jvillada.movi.shared.model.RecurringRule
import com.jvillada.movi.shared.model.SubscriptionsResult
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.UpcomingPayment
import com.jvillada.movi.theme.MoviTheme
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

/**
 * **El mínimo de la tarjeta, dibujado.**
 *
 * `MinimoDeTarjetaEnElFlujoLibreTest` (puro) prueba la aritmética. Lo que se prueba acá es lo
 * otro: que la cifra grande del card sea la que ya descontó, que el desglose se pueda verificar a
 * ojo, y —lo que de verdad importa— que **con el mínimo sin cargar la pantalla deje de afirmar el
 * número**. Una resta correcta que la pantalla no pinta se ve igual que la pantalla de antes.
 *
 * Los montos son los suyos: ingresos − gastos = $601.574, mínimo del Master Black $1.843.014,
 * disponible −$1.241.440.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h731dp-xhdpi")
class MinimoDeTarjetaEnMovimientosTest {

    @get:Rule val composeRule = createComposeRule()

    private val bancolombia = Account("acc-banco", "Bancolombia", AccountType.SAVINGS, 1_000_000L, "COP")

    private val sueldo = RecurringRule(
        id = "rr_sueldo", name = "Sueldo", category = "Ingresos",
        amount = 12_000_000L, dayOfMonth = 25, type = TransactionType.INCOME,
    )
    private val arriendo = RecurringRule(
        id = "rr_arriendo", name = "Arriendo", category = "Vivienda",
        amount = 6_093_239L, dayOfMonth = 5, type = TransactionType.EXPENSE,
    )
    private val vehiculo = RecurringRule(
        id = "${CREDIT_RULE_PREFIX}8761", name = "Cuota Vehiculo 8761", category = "Créditos",
        amount = 4_101_123L, dayOfMonth = 20, type = TransactionType.EXPENSE,
    )
    private val libreInversion = RecurringRule(
        id = "${CREDIT_RULE_PREFIX}9695", name = "Cuota Libre inversion 9695", category = "Créditos",
        amount = 1_204_064L, dayOfMonth = 15, type = TransactionType.EXPENSE,
    )

    private fun masterBlack(minimo: Long?) = RecurringRule(
        id = "${CARD_RULE_PREFIX}master", name = "Pago tarjeta Master Black", category = "Créditos",
        amount = 27_647_837L, dayOfMonth = 25, type = TransactionType.EXPENSE,
        montoEsSaldo = true, pagoMinimoCop = minimo,
    )

    private fun vence(rule: RecurringRule) =
        UpcomingPayment(rule = rule, dueDate = "2026-09-25", daysUntil = 16, status = PaymentStatus.UPCOMING)

    private fun montar(minimoDelMaster: Long?) {
        RecurringOfferGate.clear()
        Repositories.sustitutoDePrueba = object : RepositorioDePrueba() {
            override suspend fun getAccounts(): List<Account> = listOf(bancolombia)
            override suspend fun getEventsByDay(): List<EventDay> = emptyList()
            override suspend fun getCardPaymentCandidates(): List<FinancialEvent> = emptyList()
            override suspend fun getRecurringRules(): List<RecurringRule> = listOf(sueldo, arriendo)
            override suspend fun getSubscriptions(): SubscriptionsResult =
                SubscriptionsResult(emptyList(), monthlyTotalCop = 0L)
            // Las sintéticas llegan SOLO por acá — las cuotas de sus créditos y el pago de la
            // tarjeta. Ver `reglasSinteticas`.
            override suspend fun getUpcomingPayments(): List<UpcomingPayment> =
                listOf(vence(vehiculo), vence(libreInversion), vence(masterBlack(minimoDelMaster)))
            override suspend fun getOccurrenceStates(): List<OccurrenceState> = emptyList()
        }
        composeRule.setContent {
            MoviTheme { Box(Modifier.fillMaxSize()) { TransactionsScreen(onNavigate = {}) } }
        }
        esperarTexto("Sin movimientos")
        composeRule.onNodeWithText("Recurrentes", useUnmergedTree = true).performClick()
        esperarTexto("Flujo libre")
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

    private fun cuantasVeces(texto: String): Int =
        composeRule.onAllNodesWithText(texto, substring = true, useUnmergedTree = true).fetchSemanticsNodes().size

    /**
     * **La cifra grande ya descontó el mínimo, y el desglose deja verificarlo.** Sin las tres
     * cifras juntas el dueño no puede saber de dónde salió el signo negativo, y una cifra en rojo
     * que no se puede reconstruir es tan poco útil como la positiva que estaba mal.
     */
    @Test
    fun `con el minimo cargado la cifra grande es negativa y la resta se ve`() {
        montar(minimoDelMaster = 1_843_014L)

        composeRule.onNodeWithText(MENOS + "$1.241.440", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText(
            "Ingresos " + MENOS + " Gastos recurrentes " + MENOS + " Mínimos de tarjeta",
            useUnmergedTree = true,
        ).assertExists()
        composeRule.onNodeWithText("Mínimos de tarjeta", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText(MENOS + "$1.843.014", useUnmergedTree = true).assertExists()
        // Y el número de antes sigue estando, con su nombre: son dos preguntas distintas.
        composeRule.onNodeWithText("Libre sin las tarjetas", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText("$601.574", useUnmergedTree = true).assertExists()
        assertEquals(0, cuantasVeces("Falta el pago mínimo"), "no falta ninguno")
    }

    /**
     * **Y sin el mínimo cargado —el estado real de sus cinco tarjetas— la pantalla vuelve a
     * $601.574 pero deja de afirmarlo.** Es la mitad que hace honesta la decisión de no estimar:
     * sin este aviso, no cargar el dato se ve exactamente igual que no deber nada.
     */
    @Test
    fun `sin el minimo cargado la pantalla avisa que la cifra no es un hecho`() {
        montar(minimoDelMaster = null)

        composeRule.onNodeWithText("$601.574", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText(
            "Falta el pago mínimo de 1 tarjeta con deuda",
            substring = true,
            useUnmergedTree = true,
        ).assertExists()
        // Y no se dibuja una deducción de $0, que sería una fila que dice «no te cobran nada».
        assertEquals(0, cuantasVeces("Mínimos de tarjeta"), "sin dato no hay deducción que mostrar")
        composeRule.onNodeWithText("Ingresos recurrentes " + MENOS + " Gastos recurrentes", useUnmergedTree = true)
            .assertExists()
    }
}

/** El menos tipográfico (U+2212) que usa `formatCOP`, y no el guion del teclado. */
private const val MENOS = "−"
