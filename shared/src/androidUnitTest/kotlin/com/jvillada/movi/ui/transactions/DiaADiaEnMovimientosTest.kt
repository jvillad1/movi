package com.jvillada.movi.ui.transactions

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.unit.Density
import com.jvillada.movi.data.DiasPlegadosStore
import com.jvillada.movi.data.InvalidaElInicioAlEscribir
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.RepositorioDePrueba
import com.jvillada.movi.data.SessionManager
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.Budget
import com.jvillada.movi.shared.model.RecurringRule
import com.jvillada.movi.shared.model.SubscriptionsResult
import com.jvillada.movi.shared.model.VoidEvent
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.DashboardSummary
import com.jvillada.movi.shared.model.EventDay
import com.jvillada.movi.shared.model.FinanceSummary
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.OccurrenceState
import com.jvillada.movi.shared.model.PeriodSettings
import com.jvillada.movi.shared.model.ReconciliationStatus
import com.jvillada.movi.shared.model.Scope
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.UpcomingPayment
import com.jvillada.movi.shared.model.UserProfile
import com.jvillada.movi.shared.model.inicioDelPeriodo
import com.jvillada.movi.shared.model.periodoActual
import com.jvillada.movi.shared.model.periodoAnterior
import com.jvillada.movi.shared.model.periodoSiguiente
import com.jvillada.movi.shared.time.epochMillisToAppDate
import com.jvillada.movi.theme.Movi
import com.jvillada.movi.theme.MoviTheme
import com.jvillada.movi.ui.dashboard.DashboardData
import com.jvillada.movi.ui.dashboard.DashboardDataCache
import com.jvillada.movi.ui.plan.PlanScreen
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.daysUntil
import kotlinx.datetime.minus
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * # «Día a día» ya no está en Movimientos (ola R4)
 *
 * Hasta la R4, debajo del «Flujo del día» iba cuánto se gastó ese día de lo que se podía (la meta
 * diaria del Disponible de Plan). Esa comparación es de Plan —«¿cuánto puedo gastar?»—, así que
 * Movimientos la soltó: el encabezado de cada día es un renglón (la fecha y el flujo) y la pantalla ya
 * no pide el resumen del Inicio para armarla. Montado como en la app: teléfono de 390 dp con la escala
 * de letra ×1,12.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w390dp-h2400dp-xhdpi")
class DiaADiaEnMovimientosTest {

    @get:Rule val composeRule = createComposeRule()

    private val ahora = Clock.System.now().toEpochMilliseconds()
    private val hoy = epochMillisToAppDate(ahora)

    /** Un corte que deja el inicio del período unos días atrás, con hoy y los dos días anteriores adentro. */
    private val offset = (4..20).first { hoy.minus(it, DateTimeUnit.DAY).dayOfMonth in 1..28 }
    private val corte = hoy.minus(offset, DateTimeUnit.DAY).dayOfMonth
    private val ajustes = PeriodSettings(cutoffDay = corte)
    private val periodo = periodoActual(ahora, ajustes)
    private val inicio = inicioDelPeriodo(periodo, ajustes)
    private val diasDelPeriodo = inicio.daysUntil(inicioDelPeriodo(periodoSiguiente(periodo), ajustes))
    private val meta = 40_000L

    private val banco = Account("acc-banco", "Bancolombia", AccountType.SAVINGS, 1_000_000L, "COP")
    private val ayer = hoy.minus(1, DateTimeUnit.DAY)
    private val anteayer = hoy.minus(2, DateTimeUnit.DAY)
    private val delAnterior = inicio.minus(3, DateTimeUnit.DAY)

    private fun evento(id: String, tipo: TransactionType, monto: Long, nombre: String, dia: kotlinx.datetime.LocalDate) =
        FinancialEvent(
            id = id, accountId = banco.id, type = tipo, amount = monto, category = "Comida", description = nombre,
            timestamp = ahora - hoy.daysUntil(dia).let { -it } * 24L * 3600 * 1000,
            reconciliationStatus = ReconciliationStatus.RECONCILED, countsAsCashFlow = true,
        )

    private val dias = listOf(
        EventDay(hoy.toString(), 1_000_000L, listOf(evento("e1", TransactionType.INCOME, 1_000_000L, "Sueldo de hoy", hoy))),
        EventDay(ayer.toString(), -60_000L, listOf(evento("e2", TransactionType.EXPENSE, 60_000L, "Cena de ayer", ayer))),
        // El flujo de este día es −$510.000 (un pago fijo de $500.000 y un tinto); el gasto variable, $10.000.
        EventDay(
            anteayer.toString(), -510_000L,
            listOf(
                evento("e3", TransactionType.EXPENSE, 10_000L, "Tinto de antier", anteayer),
                evento("e5", TransactionType.EXPENSE, 500_000L, "Arriendo del mes", anteayer),
            ),
        ),
        EventDay(delAnterior.toString(), -5_000L, listOf(evento("e4", TransactionType.EXPENSE, 5_000L, "Pan del período anterior", delAnterior))),
    )

    private var resumenDelInicio: suspend () -> DashboardSummary = {
        DashboardSummary(gastoVariablePorDia = mapOf(ayer.toString() to 60_000L, anteayer.toString() to 10_000L))
    }

    /** Lo que el server dice que entró en el período; por defecto, lo que deja la meta en $40.000. */
    private var ingresosDelPeriodo: Long = diasDelPeriodo * meta
    private var pedidosDelResumen = 0

    private val repositorio = object : RepositorioDePrueba() {
        override suspend fun getUserProfile(): UserProfile = UserProfile(
            id = "u1", email = "juan@ejemplo.com", name = "Juan", avatarColor = "morado", periodCutoffDay = corte,
        )
        override suspend fun getAccounts(): List<Account> = listOf(banco)
        override suspend fun getEventsByDay(): List<EventDay> = dias
        override suspend fun getCardPaymentCandidates(): List<FinancialEvent> = emptyList()
        override suspend fun getFinanceSummary(scope: Scope): FinanceSummary =
            FinanceSummary(scope = Scope.SELF, balance = 0, ingresos = ingresosDelPeriodo, egresos = 0)
        override suspend fun getDashboardSummary(scope: Scope): DashboardSummary { pedidosDelResumen++; return resumenDelInicio() }
        override suspend fun getUpcomingPayments(): List<UpcomingPayment> = emptyList()
        override suspend fun getOccurrenceStates(): List<OccurrenceState> = emptyList()
        // Lo que además pide Plan (la prueba de ir de Plan a Movimientos).
        override suspend fun getBudgets(): List<Budget> = emptyList()
        override suspend fun getSubscriptions(): SubscriptionsResult = SubscriptionsResult(emptyList(), monthlyTotalCop = 0)
        override suspend fun getRecurringRules(): List<RecurringRule> = emptyList()
        override suspend fun voidEvent(id: String, reason: String?): VoidEvent =
            VoidEvent(id = "v1", originalEventId = id, reason = reason, timestamp = ahora)
    }

    private var colorDeSale = Color.Unspecified
    private var colorApagado = Color.Unspecified

    @Before
    fun entrar() {
        SessionManager.save(token = "tok", userId = "u1", name = "Juan", email = "juan@ejemplo.com")
        DiasPlegadosStore.clear()
        DashboardDataCache.data = null
        LecturaDelDiaADia.olvidar()
        // Envuelto como en la app, para que una escritura vacíe lo recordado de verdad.
        Repositories.sustitutoDePrueba = InvalidaElInicioAlEscribir(repositorio)
    }

    @After
    fun salir() {
        DashboardDataCache.data = null
        Repositories.sustitutoDePrueba = null
        DiasPlegadosStore.clear()
    }

    private var enPantalla by mutableStateOf(true)
    private var enPlan by mutableStateOf(false)

    private fun montar(periodoInicial: String? = null) {
        composeRule.setContent {
            if (enPantalla) MoviTheme {
                colorDeSale = Movi.colores.sale
                colorApagado = Movi.colores.textoApagado
                val base = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(base.density, base.fontScale * 1.12f)) {
                    Box(Modifier.fillMaxSize()) {
                        if (enPlan) PlanScreen(onNavigate = {}) else TransactionsScreen(onNavigate = {}, periodoInicial = periodoInicial)
                    }
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun hay(texto: String): Boolean =
        composeRule.onAllNodesWithText(texto, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun `Movimientos ya no dice el dia a dia, que es de Plan`() {
        montar()
        composeRule.waitUntil(timeoutMillis = 5_000) { hay("Cena de ayer") }
        composeRule.waitForIdle()

        assertTrue(composeRule.onAllNodesWithText("Día a día", substring = true, useUnmergedTree = true).fetchSemanticsNodes().isEmpty())
        assertTrue(composeRule.onAllNodesWithText("te pasaste", substring = true, useUnmergedTree = true).fetchSemanticsNodes().isEmpty())
        // El día sigue diciendo su flujo con la cifra sola, sin el rótulo (vuelta de diseño 14).
        assertTrue(!hay("Flujo del día"))
        assertTrue(composeRule.onAllNodesWithTag(TAG_ENCABEZADO_DE_DIA, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty())
    }

    @Test
    fun `Movimientos no pide el resumen del Inicio para armar la lista`() {
        montar()
        composeRule.waitUntil(timeoutMillis = 5_000) { hay("Cena de ayer") }
        composeRule.waitForIdle()

        assertEquals(0, pedidosDelResumen)
    }

    @Test
    fun `con un periodo cerrado a la vista la lista es la de siempre`() {
        montar(periodoInicial = periodoAnterior(periodo).prefijo)
        composeRule.waitUntil(timeoutMillis = 5_000) { hay("Pan del período anterior") }
        composeRule.waitForIdle()

        assertTrue(composeRule.onAllNodesWithText("Día a día", substring = true, useUnmergedTree = true).fetchSemanticsNodes().isEmpty())
    }
}
