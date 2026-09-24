package com.jvillada.movi.ui.plan

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.jvillada.movi.data.RecurringOfferGate
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.RepositorioDePrueba
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.EventDay
import com.jvillada.movi.shared.model.OccurrenceState
import com.jvillada.movi.shared.model.PaymentStatus
import com.jvillada.movi.shared.model.PeriodSettings
import com.jvillada.movi.shared.model.RecurringRule
import com.jvillada.movi.shared.model.SubConfidence
import com.jvillada.movi.shared.model.SubStatus
import com.jvillada.movi.shared.model.Subscription
import com.jvillada.movi.shared.model.SubscriptionsResult
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.UpcomingPayment
import com.jvillada.movi.theme.MoviTheme
import com.jvillada.movi.ui.recurrentes.TITULO_CHECKLIST_DEL_PERIODO
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * [TableroDeRecurrentes] montado **solo**, sin Movimientos alrededor.
 *
 * Hasta la ola C el tablero solo existía adentro de `TransactionsScreen`, colgado de SU carga: el
 * aviso de error, el «Reintentar», las cuentas y el período eran de la pantalla. Las pruebas que ya
 * había (`*EnMovimientosTest`) lo siguen probando ahí; esta fija lo que la pestaña «Plan» va a
 * necesitar de él: que **se basta solo** — lee lo suyo, se equivoca en voz alta, se recupera con su
 * propio «Reintentar» y sus acciones escriben — y que para eso no le pide los movimientos a nadie.
 *
 * Mismo cuidado con las fechas que `ChecklistDelPeriodoEnMovimientosTest`: el vencimiento se ancla
 * al día 1 del mes en curso, porque el checklist solo enumera el período de hoy.
 */
@RunWith(RobolectricTestRunner::class)
// Alta a propósito, como `SuscripcionesActivasEnMovimientosTest`: las suscripciones van al final
// del tablero y lo que se prueba es la carga, no el scroll.
@Config(qualifiers = "w411dp-h1200dp-xhdpi")
class TableroDeRecurrentesSoloTest {

    @get:Rule val composeRule = createComposeRule()

    private val hoy: LocalDate = Clock.System.todayIn(TimeZone.of("America/Bogota"))
    private val vence: LocalDate =
        if (hoy.dayOfMonth >= 2) LocalDate(hoy.year, hoy.monthNumber, 1) else hoy

    private val bancolombia = Account("acc-banco", "Bancolombia", AccountType.SAVINGS, 1_000_000L, "COP")
    private val arriendo = RecurringRule(
        id = "rr_arriendo", name = "Arriendo", category = "Vivienda",
        amount = 1_800_000L, dayOfMonth = vence.dayOfMonth, type = TransactionType.EXPENSE,
    )

    private fun sub(id: String, nombre: String, monto: Long, dia: Int, estado: SubStatus) = Subscription(
        id = id, merchantKey = nombre.lowercase(), displayName = nombre, amount = monto, currency = "COP",
        dayOfMonth = dia, status = estado, confidence = SubConfidence.HIGH,
        firstSeen = 0, lastSeen = 0, occurrences = 4,
    )

    private var suscripciones = listOf<Subscription>()
    private var lecturasDeVencimientos = 0
    private var fallanLosVencimientos = 0
    private var leyoMovimientos = false
    private var actualizada: Subscription? = null

    private inner class Repo : RepositorioDePrueba() {
        override suspend fun getAccounts(): List<Account> = listOf(bancolombia)
        override suspend fun getRecurringRules(): List<RecurringRule> = listOf(arriendo)
        // El total al mes lo calcula el server (y el tablero lo usa tal cual cuando ninguna regla
        // tapa una suscripción): acá, las activas en pesos, que es lo que él sumaría.
        override suspend fun getSubscriptions(): SubscriptionsResult = SubscriptionsResult(
            suscripciones,
            monthlyTotalCop = suscripciones
                .filter { it.status == SubStatus.AUTO || it.status == SubStatus.CONFIRMED }
                .sumOf { it.amount },
        )

        override suspend fun getUpcomingPayments(): List<UpcomingPayment> {
            lecturasDeVencimientos++
            if (fallanLosVencimientos > 0) {
                fallanLosVencimientos--
                error("se cayó la red")
            }
            return listOf(
                UpcomingPayment(
                    rule = arriendo,
                    dueDate = vence.toString(),
                    daysUntil = vence.dayOfMonth - hoy.dayOfMonth,
                    status = if (vence < hoy) PaymentStatus.OVERDUE else PaymentStatus.DUE_TODAY,
                ),
            )
        }

        override suspend fun getOccurrenceStates(): List<OccurrenceState> = listOf(
            OccurrenceState(
                ruleId = arriendo.id, period = vence.toString().take(7), dueDate = vence.toString(),
                occurred = false, candidates = emptyList(),
            ),
        )

        /** El tablero no los necesita: si los pide, es que volvió a colgarse de Movimientos. */
        override suspend fun getEventsByDay(): List<EventDay> {
            leyoMovimientos = true
            return emptyList()
        }

        override suspend fun updateSubscription(id: String, subscription: Subscription): Subscription {
            actualizada = subscription
            suscripciones = suscripciones.map { if (it.id == id) subscription else it }
            return subscription
        }
    }

    @Before
    fun preparar() {
        RecurringOfferGate.clear()
        Repositories.sustitutoDePrueba = Repo()
        suscripciones = listOf(
            sub("s_netflix", "Netflix", 44_900L, 20, SubStatus.CONFIRMED),
            sub("s_spotify", "Spotify", 16_900L, 8, SubStatus.CANDIDATE),
        )
        lecturasDeVencimientos = 0
        fallanLosVencimientos = 0
        leyoMovimientos = false
        actualizada = null
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
                    TableroDeRecurrentes(ajustesDelPeriodo = PeriodSettings(cutoffDay = 1), onNavigate = {})
                }
            }
        }
    }

    private fun esperarTexto(texto: String) {
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText(texto, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun esperarQueDesaparezca(texto: String) {
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText(texto, useUnmergedTree = true).fetchSemanticsNodes().isEmpty()
        }
    }

    @Test
    fun `solo, lee lo suyo y pinta el tablero entero sin pedir los movimientos`() {
        montar()
        // La cifra plegada de las suscripciones es lo último que se pinta (espera a los
        // vencimientos Y a las suscripciones): cuando está, todo lo demás ya llegó.
        esperarTexto("1 cobro · $44.900 al mes")

        composeRule.onNodeWithText(TITULO_CHECKLIST_DEL_PERIODO.uppercase(), useUnmergedTree = true).assertExists()
        composeRule.onAllNodesWithText("Arriendo", useUnmergedTree = true).onLast().assertExists()
        composeRule.onNodeWithText("DETECTADAS · POR CONFIRMAR", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText("Spotify", useUnmergedTree = true).assertExists()
        // «Gastos recurrentes» del «Flujo libre»: el arriendo más Netflix — la candidata no suma.
        composeRule.onNodeWithText("$1.844.900", useUnmergedTree = true).assertExists()
        assertFalse(leyoMovimientos, "el tablero no tiene por qué leer los movimientos")
    }

    @Test
    fun `sus acciones escriben y vuelven a leer, sin nadie alrededor`() {
        montar()
        esperarTexto("Spotify")

        composeRule.onNodeWithText("Confirmar", useUnmergedTree = true).performClick()

        esperarQueDesaparezca("DETECTADAS · POR CONFIRMAR")
        assertEquals(SubStatus.CONFIRMED, actualizada?.status)
        // Confirmada, suma: la relectura la trae como activa.
        esperarTexto("2 cobros · $61.800 al mes")
    }

    @Test
    fun `una lectura caida lo dice, no inventa la cifra, y su propio Reintentar la vuelve a pedir`() {
        fallanLosVencimientos = 1
        montar()
        esperarTexto("No se pudo leer el checklist de este período")
        // Sin vencimientos no hay cifra: guion, no un total optimista.
        composeRule.onNodeWithText("$1.844.900", useUnmergedTree = true).assertDoesNotExist()

        // El «Reintentar» del aviso de abajo — el último que se compone, después de la lista.
        esperarTexto("Reintentar")
        composeRule.onAllNodesWithText("Reintentar", useUnmergedTree = true).onLast().performClick()

        esperarTexto("$1.844.900")
        composeRule.onNodeWithText("No se pudo leer el checklist de este período", useUnmergedTree = true)
            .assertDoesNotExist()
        assertEquals(2, lecturasDeVencimientos)
    }
}
