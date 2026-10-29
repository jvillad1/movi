package com.jvillada.movi.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import com.jvillada.movi.data.DiasPlegadosStore
import com.jvillada.movi.data.RecurringOfferGate
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.RepositorioDePrueba
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.Budget
import com.jvillada.movi.shared.model.CardSummary
import com.jvillada.movi.shared.model.CreditSummary
import com.jvillada.movi.shared.model.CreditTerms
import com.jvillada.movi.shared.model.DashboardSummary
import com.jvillada.movi.shared.model.DestinoConocido
import com.jvillada.movi.shared.model.DetalleDelEspacio
import com.jvillada.movi.shared.model.Espacio
import com.jvillada.movi.shared.model.EventDay
import com.jvillada.movi.shared.model.FinanceSummary
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.GastoDelEspacio
import com.jvillada.movi.shared.model.MIEMBRO_YO
import com.jvillada.movi.shared.model.MiembroDelEspacio
import com.jvillada.movi.shared.model.OccurrenceState
import com.jvillada.movi.shared.model.PaymentStatus
import com.jvillada.movi.shared.model.PeriodSettings
import com.jvillada.movi.shared.model.PeriodoFinanciero
import com.jvillada.movi.shared.model.ReconciliationStatus
import com.jvillada.movi.shared.model.RecurringRule
import com.jvillada.movi.shared.model.ResumenDePeriodo
import com.jvillada.movi.shared.model.Scope
import com.jvillada.movi.shared.model.SubscriptionsResult
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.UpcomingPayment
import com.jvillada.movi.shared.model.UserProfile
import com.jvillada.movi.shared.model.defaultDashboardDefinition
import com.jvillada.movi.shared.model.partesDelGasto
import com.jvillada.movi.shared.model.saldoDelEspacio
import com.jvillada.movi.shared.time.epochMillisToAppDate
import com.jvillada.movi.theme.MoviTheme
import com.jvillada.movi.ui.accounts.AccountsScreen
import com.jvillada.movi.ui.components.ds.VarianteDeCifra
import com.jvillada.movi.ui.components.ds.VarianteDeCifraKey
import com.jvillada.movi.ui.credits.CreditosScreen
import com.jvillada.movi.ui.dashboard.DashboardData
import com.jvillada.movi.ui.espacios.EspaciosScreen
import com.jvillada.movi.ui.plan.PlanScreen
import com.jvillada.movi.ui.plan.SEGMENTO_PAGOS
import com.jvillada.movi.ui.plan.SEGMENTO_PRESUPUESTOS
import com.jvillada.movi.ui.sdui.SduiRenderer
import com.jvillada.movi.ui.transactions.TransactionsScreen
import kotlinx.datetime.Clock
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
 * # Una sola `Cifra` héroe por pantalla (vuelta de diseño 14)
 *
 * En Plan → Presupuestos se veían «Disponible del período $12.536.800» y, más abajo, «Gastado en
 * octubre $530.650» en la letra de la héroe —más grande todavía—: dos cifras gigantes compitiendo.
 * La regla de `docs/diseno/sistema.md` §6 («`Heroe`: una por pantalla») ahora se vigila acá, en las
 * pantallas principales montadas con datos: Hoy, Plan en sus dos segmentos, Patrimonio, Créditos,
 * Movimientos y Espacios.
 *
 * Se cuenta por la semántica que pone la propia `Cifra` (`VarianteDeCifraKey`; también la marca
 * `CifraProtagonista`, la héroe ya escrita), no por un texto ni por un tamaño de letra. Donde la
 * pantalla tiene su protagonista (Hoy, Patrimonio, Créditos) se exige **exactamente una**: así la
 * prueba también demuestra que el conteo encuentra a la héroe cuando está.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w390dp-h3200dp-xhdpi")
class UnaSolaCifraHeroePorPantallaTest {

    @get:Rule val composeRule = createComposeRule()

    private val hoy = epochMillisToAppDate(Clock.System.now().toEpochMilliseconds()).toString()

    private val cuentas = listOf(
        Account("acc-bc", "Bancolombia Ahorros", AccountType.SAVINGS, 13_845_800L),
        Account("acc-nu", "Nu", AccountType.SAVINGS, 2_890_000L),
        Account("acc-9695", "Libre inversión 9695", AccountType.LOAN, 40_104_518L),
    )

    private val libreInversion = CreditSummary(
        account = cuentas.last(),
        terms = CreditTerms(
            accountId = "acc-9695", bank = "Bancolombia", principal = 80_000_000L, rateEa = 11.27,
            termMonths = 105, installment = 1_204_064L, dayOfMonth = 15, startDate = "2021-06-15",
        ),
        paidPct = 0.5,
    )

    private val arriendo = UpcomingPayment(
        rule = RecurringRule(
            id = "rr_arriendo", name = "Arriendo", category = "Vivienda",
            amount = 1_850_000, dayOfMonth = 1, type = TransactionType.EXPENSE,
        ),
        dueDate = hoy, daysUntil = 0, status = PaymentStatus.UPCOMING,
    )

    private val almuerzo = FinancialEvent(
        id = "e1", accountId = "acc-bc", type = TransactionType.EXPENSE, amount = 46_489L,
        category = "Comida", description = "Señor Gol", timestamp = Clock.System.now().toEpochMilliseconds(),
        reconciliationStatus = ReconciliationStatus.RECONCILED, countsAsCashFlow = true,
    )

    private val casa = Espacio(
        id = "esp_casa", nombre = "Casa con Caro",
        miembros = listOf(MiembroDelEspacio(MIEMBRO_YO, "Juan"), MiembroDelEspacio("dst_caro", "Caro")),
    )
    private val gastosDeLaCasa = listOf(
        GastoDelEspacio(
            id = "g1", espacioId = casa.id, pagadoPor = MIEMBRO_YO, monto = 2_100_000, categoria = "Mercado",
            descripcion = "Mercado Éxito", fecha = 1_761_000_000_000L, partes = partesDelGasto(2_100_000, casa, MIEMBRO_YO),
        ),
    )

    private inner class Repo : RepositorioDePrueba() {
        override suspend fun getAccounts(): List<Account> = cuentas
        override suspend fun getCredits(): List<CreditSummary> = listOf(libreInversion)
        override suspend fun getCards(): List<CardSummary> = emptyList()
        override suspend fun getDestinos(): List<DestinoConocido> = emptyList()
        override suspend fun getUserProfile(): UserProfile =
            UserProfile(id = "u", email = "u@local", name = "Juan", avatarColor = "#000000", periodCutoffDay = 25)
        override suspend fun isScreenAdmin(): Boolean = false
        override suspend fun getFinanceSummary(scope: Scope): FinanceSummary =
            FinanceSummary(scope = Scope.SELF, balance = 0, ingresos = 15_000_000, egresos = 2_000_000)
        override suspend fun getDashboardSummary(scope: Scope): DashboardSummary =
            DashboardSummary(spentByCategory = mapOf("Comida" to 1_200_000L, "Mercado" to 392_100L))
        override suspend fun getUpcomingPayments(): List<UpcomingPayment> = listOf(arriendo)
        override suspend fun getOccurrenceStates(): List<OccurrenceState> = emptyList()
        override suspend fun getRecurringRules(): List<RecurringRule> = listOf(arriendo.rule)
        override suspend fun getSubscriptions(): SubscriptionsResult = SubscriptionsResult(emptyList(), monthlyTotalCop = 0)
        override suspend fun getBudgets(): List<Budget> = listOf(Budget("Comida", 1_000_000L), Budget("Mercado", 2_000_000L))
        override suspend fun getEventsByDay(): List<EventDay> =
            listOf(EventDay(date = hoy, total = -46_489L, items = listOf(almuerzo)))
        override suspend fun getCardPaymentCandidates(): List<FinancialEvent> = emptyList()
        override suspend fun getPeriodos(): List<ResumenDePeriodo> = listOf(
            ResumenDePeriodo(
                id = "2026-10", nombre = "Octubre 2026", desde = "2026-09-25", hasta = "2026-10-24",
                enCurso = true, entradas = 12_392_100L, salidas = 11_845_800L, movimientos = 40,
            ),
        )
        override suspend fun getEspacios(): List<Espacio> = listOf(casa)
        override suspend fun getDetalleDelEspacio(id: String): DetalleDelEspacio = DetalleDelEspacio(
            espacio = casa,
            saldo = saldoDelEspacio(casa, gastosDeLaCasa, emptyList()),
            delPeriodo = saldoDelEspacio(casa, gastosDeLaCasa, emptyList()),
            gastos = gastosDeLaCasa,
        )
    }

    @Before
    fun preparar() {
        RecurringOfferGate.clear()
        DiasPlegadosStore.clear()
        Repositories.sustitutoDePrueba = Repo()
    }

    @After
    fun limpiar() {
        Repositories.sustitutoDePrueba = null
        RecurringOfferGate.clear()
        DiasPlegadosStore.clear()
    }

    private fun montar(esperar: String, pantalla: @Composable () -> Unit) {
        composeRule.setContent { MoviTheme { Box(Modifier.fillMaxSize()) { pantalla() } } }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText(esperar, substring = true, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.waitForIdle()
    }

    /** Las cifras héroe montadas, con su texto para el mensaje. */
    private fun heroes(): List<String> =
        composeRule.onAllNodes(SemanticsMatcher.expectValue(VarianteDeCifraKey, VarianteDeCifra.Heroe), useUnmergedTree = true)
            .fetchSemanticsNodes()
            .map(::texto)

    private fun texto(nodo: SemanticsNode): String =
        nodo.config.getOrNull(SemanticsProperties.Text)?.joinToString("") { it.text }.orEmpty()

    /** A lo sumo una; con [exactamente], esa una tiene que estar. */
    private fun unaSola(pantalla: String, exactamente: Boolean = false) {
        val encontradas = heroes()
        if (exactamente) {
            assertEquals(1, encontradas.size, "$pantalla tiene que tener su cifra héroe, y una sola: $encontradas")
        } else {
            assertTrue(encontradas.size <= 1, "$pantalla tiene ${encontradas.size} cifras héroe: $encontradas")
        }
    }

    @Test
    fun `Hoy tiene una sola cifra heroe`() {
        val datos = DashboardData(
            summary = FinanceSummary(scope = Scope.SELF, balance = 0, ingresos = 10_000_000, egresos = 4_000_000),
            accounts = cuentas.take(2),
            credits = emptyList(),
            cards = emptyList(),
            upcoming = listOf(arriendo),
            ocurrencias = emptyList(),
            periodoActual = PeriodoFinanciero(2026, 10),
            ajustesDePeriodo = PeriodSettings(),
            spentByCategory = mapOf("Mercado" to 392_100L, "Comida" to 236_000L),
        )
        composeRule.setContent {
            MoviTheme {
                Box(Modifier.fillMaxSize()) {
                    SduiRenderer(definition = defaultDashboardDefinition(), data = datos, modifier = Modifier.fillMaxSize(), onNavigate = {})
                }
            }
        }
        composeRule.waitForIdle()
        unaSola("Hoy", exactamente = true)
    }

    @Test
    fun `Plan en Pagos tiene a lo sumo una cifra heroe`() {
        montar("Arriendo") { PlanScreen(onNavigate = {}, segmento = SEGMENTO_PAGOS) }
        unaSola("Plan · Pagos")
    }

    /** El caso de la vuelta: «Gastado en …» era una héroe debajo de «Disponible del período». */
    @Test
    fun `Plan en Presupuestos tiene a lo sumo una cifra heroe`() {
        montar("Fuera de presupuesto") { PlanScreen(onNavigate = {}, segmento = SEGMENTO_PRESUPUESTOS) }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("Mercado", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        unaSola("Plan · Presupuestos")
    }

    @Test
    fun `Patrimonio tiene una sola cifra heroe`() {
        montar("Bancolombia Ahorros") { AccountsScreen(onNavigate = {}) }
        unaSola("Patrimonio", exactamente = true)
    }

    @Test
    fun `Creditos tiene una sola cifra heroe`() {
        montar("Libre inversión 9695") { CreditosScreen(onNavigate = {}) }
        unaSola("Créditos", exactamente = true)
    }

    @Test
    fun `Movimientos tiene a lo sumo una cifra heroe`() {
        montar("Señor Gol") { TransactionsScreen(onNavigate = {}) }
        unaSola("Movimientos")
    }

    @Test
    fun `Espacios tiene a lo sumo una cifra heroe`() {
        montar("Casa con Caro") { EspaciosScreen(onNavigate = {}) }
        unaSola("Espacios")
    }
}
