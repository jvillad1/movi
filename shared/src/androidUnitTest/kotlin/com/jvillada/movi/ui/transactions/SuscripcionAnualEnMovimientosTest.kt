package com.jvillada.movi.ui.transactions

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
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
import com.jvillada.movi.shared.model.MANUAL_SUB_PREFIX
import com.jvillada.movi.shared.model.PeriodicidadDeCobro
import com.jvillada.movi.shared.model.RecurringRule
import com.jvillada.movi.shared.model.SubConfidence
import com.jvillada.movi.shared.model.SubStatus
import com.jvillada.movi.shared.model.Subscription
import com.jvillada.movi.shared.model.SubscriptionsResult
import com.jvillada.movi.theme.MoviTheme
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Ola 16 — **un cobro anual pintado de verdad**, con [TransactionsScreen] montada y el chip
 * «Recurrentes» activo.
 *
 * Lo que una función pura no alcanza a probar es justamente lo que más importa acá: que las tres
 * cifras que la pantalla muestra a la vez —el cobro real de la fila, lo que esa fila aporta, y el
 * total de arriba— sean coherentes ENTRE SÍ y estén todas dichas. Un «$369.900» suelto al lado de
 * un «Gastos recurrentes» de $110.000 es una pantalla que se contradice sola, y el dueño no tiene
 * forma de saber cuál de los dos números está mal.
 *
 * Los montos son los cobros reales que él está por cargar: NBA League Pass ($112.900 al año), HBO
 * Max Platinum ($369.900 al año) y Google One ($79.000 al mes).
 *
 * Mismo patrón de montaje y misma altura de ventana que [SuscripcionesActivasEnMovimientosTest]:
 * la sección va debajo del «Flujo libre» y en 731dp la última fila queda bajo el pliegue.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h1200dp-xhdpi")
class SuscripcionAnualEnMovimientosTest {

    @get:Rule val composeRule = createComposeRule()

    private val bancolombia = Account("acc-banco", "Bancolombia", AccountType.SAVINGS, 1_000_000L, "COP")

    private fun sub(
        id: String,
        nombre: String,
        monto: Long,
        dia: Int,
        periodicidad: PeriodicidadDeCobro,
    ) = Subscription(
        id = id, merchantKey = MANUAL_SUB_PREFIX + id, displayName = nombre, amount = monto,
        currency = "COP", dayOfMonth = dia, status = SubStatus.CONFIRMED,
        confidence = SubConfidence.HIGH, firstSeen = 0, lastSeen = 0, occurrences = 0,
        periodicidad = periodicidad,
    )

    private val suscripciones = listOf(
        sub("nba", "NBA League Pass", 112_900L, 4, PeriodicidadDeCobro.ANUAL),
        sub("google_one", "Google One", 79_000L, 15, PeriodicidadDeCobro.MENSUAL),
        sub("hbo_max", "HBO Max Platinum", 369_900L, 28, PeriodicidadDeCobro.ANUAL),
    )

    /** 9.409 (NBA) + 79.000 (Google One) + 30.825 (HBO): lo que ya prorrateó el server. */
    private val totalProrrateado = 119_234L

    private inner class Repo : RepositorioDePrueba() {
        override suspend fun getAccounts(): List<Account> = listOf(bancolombia)
        override suspend fun getEventsByDay(): List<EventDay> = emptyList()
        override suspend fun getCardPaymentCandidates(): List<FinancialEvent> = emptyList()
        override suspend fun getRecurringRules(): List<RecurringRule> = emptyList()
        override suspend fun getSubscriptions(): SubscriptionsResult =
            SubscriptionsResult(suscripciones, monthlyTotalCop = totalProrrateado, usdToCop = 0.0)
    }

    @Before
    fun montar() {
        RecurringOfferGate.clear()
        Repositories.sustitutoDePrueba = Repo()
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

    /**
     * La fila muestra el **cobro real** —el número que el dueño puede buscar en el extracto— y
     * NUNCA el prorrateado. Lo que impide leerlo como un gasto del mes son las dos palabras del
     * final, no una cifra distinta.
     */
    @Test
    fun `la fila de un cobro anual muestra el cobro real y dice que es al ano`() {
        composeRule.onNodeWithText("−$369.900 al año", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText("−$112.900 al año", useUnmergedTree = true).assertExists()
    }

    /** Y una mensual sigue viéndose exactamente como se veía: sin ninguna coletilla. */
    @Test
    fun `la fila de un cobro mensual no dice nada de periodicidad`() {
        composeRule.onNodeWithText("−$79.000", useUnmergedTree = true).assertExists()
    }

    /**
     * La línea que cierra la distancia entre los $369.900 de la fila y los $30.825 que esa fila
     * aporta al total de arriba. Sin ella, el total no es la suma de lo que se ve y no hay forma
     * de saber por qué.
     */
    @Test
    fun `cada cobro anual dice cuanto aporta al total del mes`() {
        composeRule.onNodeWithText("Entra al total como $30.825 al mes", useUnmergedTree = true)
            .assertExists()
        composeRule.onNodeWithText("Entra al total como $9.409 al mes", useUnmergedTree = true)
            .assertExists()
    }

    /**
     * **El «Flujo libre» usa el prorrateado, no el cobro entero.** Es la cifra que este cambio
     * vino a arreglar: sin periodicidad, «Gastos recurrentes» habría dicho $561.800 —los $482.800
     * de los dos anuales más los $79.000 mensuales— sobre una plata que el dueño no gasta.
     */
    @Test
    fun `el flujo libre cuenta el prorrateado y no el cobro entero`() {
        composeRule.onNodeWithText("$119.234", useUnmergedTree = true)
            .assertExists()
        composeRule.onAllNodesWithText("$561.800", useUnmergedTree = true)
            .assertCountEquals(0)
    }

    /** Y el card explica de dónde sale ese total, en vez de dejarlo sin cuadrar contra la lista. */
    @Test
    fun `el card explica que los cobros anuales entran repartidos`() {
        composeRule.onNodeWithText("dividimos el cobro en 12", substring = true, useUnmergedTree = true)
            .assertExists()
    }
}
