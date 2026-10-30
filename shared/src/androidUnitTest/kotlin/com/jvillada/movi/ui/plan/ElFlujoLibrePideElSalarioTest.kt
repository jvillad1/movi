package com.jvillada.movi.ui.plan

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
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
import com.jvillada.movi.shared.model.PeriodSettings
import com.jvillada.movi.shared.model.RecurringRule
import com.jvillada.movi.shared.model.SubscriptionsResult
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.UpcomingPayment
import com.jvillada.movi.theme.MoviTheme
import com.jvillada.movi.ui.recurrentes.ResumenRecurrentes
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
 * # Sin salario no hay flujo libre que mostrar (vuelta de diseño 22)
 *
 * La revisión: «Ingresos recurrentes $0 · Flujo libre −$1.875.159». Sin un ingreso que se repita, el
 * flujo libre son los gastos con signo menos: asusta y no dice nada. En su lugar van solo los gastos.
 *
 * **Vuelta 78:** la tarjeta ya no invita a agregar el salario («Agrega tu salario… Agregar mi salario»):
 * esa invitación vive en un solo lugar de Pagos, la línea de la caja (`PagosHastaQueTePaguenTest`).
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h1400dp-xhdpi")
class ElFlujoLibrePideElSalarioTest {

    @get:Rule val composeRule = createComposeRule()

    private val bancolombia = Account("acc-banco", "Bancolombia", AccountType.SAVINGS, 1_000_000L, "COP")
    private val arriendo = RecurringRule(
        id = "rr_arriendo", name = "Arriendo", category = "Vivienda",
        amount = 1_875_159L, dayOfMonth = 5, type = TransactionType.EXPENSE,
    )
    private val salario = RecurringRule(
        id = "rr_salario", name = "Salario", category = "Ingreso",
        amount = 9_000_000L, dayOfMonth = 25, type = TransactionType.INCOME,
    )

    private fun pendiente(regla: RecurringRule) =
        UpcomingPayment(regla, dueDate = "2026-11-05", daysUntil = 9, status = PaymentStatus.UPCOMING)

    private fun montar(reglas: List<RecurringRule>) {
        RecurringOfferGate.clear()
        Repositories.sustitutoDePrueba = object : RepositorioDePrueba() {
            override suspend fun getAccounts(): List<Account> = listOf(bancolombia)
            override suspend fun getEventsByDay(): List<EventDay> = emptyList()
            override suspend fun getCardPaymentCandidates(): List<FinancialEvent> = emptyList()
            override suspend fun getRecurringRules(): List<RecurringRule> = reglas
            override suspend fun getSubscriptions(): SubscriptionsResult = SubscriptionsResult(emptyList(), monthlyTotalCop = 0L)
            override suspend fun getUpcomingPayments(): List<UpcomingPayment> = reglas.map(::pendiente)
            override suspend fun getOccurrenceStates(): List<OccurrenceState> = emptyList()
        }
        composeRule.setContent {
            MoviTheme { Box(Modifier.fillMaxSize()) { TableroDeRecurrentesDePrueba(ajustesDelPeriodo = PeriodSettings(), onNavigate = {}) } }
        }
        composeRule.waitUntil(timeoutMillis = 5_000) { hay("Gastos recurrentes") }
    }

    @After
    fun limpiar() {
        Repositories.sustitutoDePrueba = null
        RecurringOfferGate.clear()
    }

    private fun hay(texto: String) =
        composeRule.onAllNodesWithText(texto, substring = true, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun `sin ingresos recurrentes van solo los gastos, sin otra invitacion al salario`() {
        montar(listOf(arriendo))

        assertEquals(1, composeRule.onAllNodesWithTag(TAG_SOLO_LOS_GASTOS_RECURRENTES, useUnmergedTree = true).fetchSemanticsNodes().size)
        assertFalse(hay("Agrega tu salario"), "la invitación vive en la caja")
        assertFalse(hay("Agregar mi salario"))
        // Ni el flujo libre en negativo ni «Ingresos recurrentes $0».
        assertFalse(hay("−\$1.875.159"), "sin un flujo libre negativo")
        assertFalse(hay("Ingresos recurrentes"))
        // Los gastos recurrentes sí son un dato, y se siguen viendo.
        assertTrue(hay("\$1.875.159"))
    }

    @Test
    fun `con un salario el flujo libre se ve como siempre`() {
        montar(listOf(arriendo, salario))
        assertTrue(hay("Flujo libre"))
        assertEquals(0, composeRule.onAllNodesWithTag(TAG_SOLO_LOS_GASTOS_RECURRENTES, useUnmergedTree = true).fetchSemanticsNodes().size)
    }

    @Test
    fun `la regla es no tener ingresos recurrentes`() {
        fun resumen(ingresos: Long) = ResumenRecurrentes(
            items = emptyList(), ingresos = ingresos, gastos = 100_000L,
            gastosDeSuscripciones = 0L, sinConvertir = 0, hayMonedaExtranjera = false,
        )
        assertTrue(pideElSalario(resumen(0L)))
        assertFalse(pideElSalario(resumen(5_000_000L)))
    }

    /** Vuelta 78: «$3.625.338» y el «de $3.662.500» de «Falta por pagar» dicen qué es cada una. */
    @Test
    fun `las dos cifras se dicen en una frase, y solo si son distintas`() {
        assertEquals(
            "\$3.625.338 al mes · \$3.662.500 este período, con lo que de verdad pagaste y el mínimo de tus tarjetas",
            lineaDeLasDosCifras(3_625_338L, 3_662_500L),
        )
        kotlin.test.assertNull(lineaDeLasDosCifras(3_625_338L, 3_625_338L))
        kotlin.test.assertNull(lineaDeLasDosCifras(3_625_338L, null))
        kotlin.test.assertNull(lineaDeLasDosCifras(3_625_338L, 0L))
    }
}
