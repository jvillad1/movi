package com.jvillada.movi.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
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
import com.jvillada.movi.shared.model.DashboardSummary
import com.jvillada.movi.shared.model.DestinoConocido
import com.jvillada.movi.shared.model.EventDay
import com.jvillada.movi.shared.model.EventSource
import com.jvillada.movi.shared.model.FinanceSummary
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.OccurrenceState
import com.jvillada.movi.shared.model.PaymentStatus
import com.jvillada.movi.shared.model.PeriodSettings
import com.jvillada.movi.shared.model.PeriodoFinanciero
import com.jvillada.movi.shared.model.ReconciliationStatus
import com.jvillada.movi.shared.model.RecurringRule
import com.jvillada.movi.shared.model.SMS_STATE_PENDING
import com.jvillada.movi.shared.model.Scope
import com.jvillada.movi.shared.model.SmsMessage
import com.jvillada.movi.shared.model.SubscriptionsResult
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.UpcomingPayment
import com.jvillada.movi.shared.model.UserProfile
import com.jvillada.movi.shared.model.defaultDashboardDefinition
import com.jvillada.movi.shared.time.epochMillisToAppDate
import com.jvillada.movi.theme.Medida
import com.jvillada.movi.theme.MoviTheme
import com.jvillada.movi.ui.dashboard.DashboardData
import com.jvillada.movi.ui.plan.PlanScreen
import com.jvillada.movi.ui.plan.SEGMENTO_PAGOS
import com.jvillada.movi.ui.porrevisar.PorRevisarScreen
import com.jvillada.movi.ui.quickadd.QuickAddScreen
import com.jvillada.movi.ui.sdui.SduiRenderer
import com.jvillada.movi.ui.transactions.TransactionsScreen
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertTrue

/**
 * # Nada se toca con menos de 48 × 48 dp (vuelta 55, accesibilidad)
 *
 * La revisión del 28-oct midió a ojo los enlaces de texto —«Ahora no», «Todas ›», «Ver todo»,
 * «Editar», «y 2 más»— entre 20 y 28 dp de alto. `docs/diseno/sistema.md` dice que nada que se toque
 * mide menos de `Medida.objetivoTactil` (48). Esta guarda monta las cinco pantallas que más se tocan
 * —Inicio, Pagos, Por revisar, Actividad y Agregar— con datos, recorre **cada nodo que se toca** y
 * exige que su área de toque llegue a 48 en los dos lados.
 *
 * El área de toque es lo que se ve más la extensión al objetivo táctil que hace Compose para lo chico
 * que recibe el puntero (`touchBoundsInRoot`): un enlace de 20 de alto recibe el toque en 48 sin
 * ocupar más lugar, así que un enlace en línea puede quedarse del tamaño de su texto y tocarse en 48.
 * Esa extensión solo existe con un puntero (un `clickable`, un `pointerInput`): lo que se toca sin él
 * —un `onClick` de semántica armado a mano— se mide por lo que se ve, y eso es lo que esta guarda
 * atrapa, además de lo que quedara angosto.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h3200dp-xhdpi")
class NadaSeTocaConMenosDe48Test {

    @get:Rule val composeRule = createComposeRule()

    private val hoyMs = Clock.System.now().toEpochMilliseconds()
    private val hoy = epochMillisToAppDate(hoyMs)

    private val ahorros = Account("acc-bc", "Bancolombia Ahorros", AccountType.SAVINGS, 13_845_800L)
    private val nu = Account("acc-nu", "Nu", AccountType.SAVINGS, 2_890_000L)

    private fun pago(id: String, nombre: String, monto: Long, dias: Int) = UpcomingPayment(
        rule = RecurringRule(id = id, name = nombre, category = "Vivienda", amount = monto, dayOfMonth = 1, type = TransactionType.EXPENSE),
        dueDate = LocalDate.fromEpochDays(hoy.toEpochDays() + dias).toString(),
        daysUntil = dias,
        status = if (dias < 0) PaymentStatus.OVERDUE else if (dias == 0) PaymentStatus.DUE_TODAY else PaymentStatus.UPCOMING,
    )

    private val arriendo = pago("rr_arriendo", "Arriendo", 1_850_000, 0)
    private val celular = pago("rr_cel", "Celular", 53_000, 3)
    private val internet = pago("rr_net", "Internet", 98_000, 6)

    private fun gasto(id: String, descripcion: String, monto: Long, fuente: EventSource = EventSource.MANUAL, estado: ReconciliationStatus = ReconciliationStatus.RECONCILED) =
        FinancialEvent(
            id = id, accountId = ahorros.id, type = TransactionType.EXPENSE, amount = monto, category = "Comida",
            description = descripcion, timestamp = hoyMs, source = fuente, reconciliationStatus = estado, countsAsCashFlow = true,
        )

    private val movimientos = listOf(
        gasto("e1", "Señor Gol", 46_489),
        gasto("e2", "Panadería La Esquina", 27_500),
        gasto("e3", "Rappi", 42_500, EventSource.SMS, ReconciliationStatus.UNCONFIRMED),
    )

    private val aviso = SmsMessage(
        id = "s1", time = "${LocalDate.fromEpochDays(hoy.toEpochDays() - 1)} 11:02", bank = "Bancolombia",
        text = "Compra aprobada por \$42.500 en RAPPI el 02/09/2026.", state = SMS_STATE_PENDING, det = "Rappi",
    )

    private inner class Repo : RepositorioDePrueba() {
        override suspend fun getAccounts(): List<Account> = listOf(ahorros, nu)
        override suspend fun getCredits(): List<CreditSummary> = emptyList()
        override suspend fun getCards(): List<CardSummary> = emptyList()
        override suspend fun getDestinos(): List<DestinoConocido> = emptyList()
        override suspend fun getUserProfile(): UserProfile =
            UserProfile(id = "u", email = "u@local", name = "Juan", avatarColor = "#000000", periodCutoffDay = 1)
        override suspend fun isScreenAdmin(): Boolean = false
        override suspend fun getFinanceSummary(scope: Scope): FinanceSummary =
            FinanceSummary(scope = Scope.SELF, balance = 0, ingresos = 15_000_000, egresos = 2_000_000)
        override suspend fun getDashboardSummary(scope: Scope): DashboardSummary =
            DashboardSummary(spentByCategory = mapOf("Comida" to 1_200_000L, "Mercado" to 392_100L))
        override suspend fun getUpcomingPayments(): List<UpcomingPayment> = listOf(arriendo, celular, internet)
        override suspend fun getOccurrenceStates(): List<OccurrenceState> = emptyList()
        override suspend fun getRecurringRules(): List<RecurringRule> = listOf(arriendo.rule, celular.rule, internet.rule)
        override suspend fun getSubscriptions(): SubscriptionsResult = SubscriptionsResult(emptyList(), monthlyTotalCop = 0)
        override suspend fun getBudgets(): List<Budget> = listOf(Budget("Comida", 1_000_000L))
        override suspend fun getEventsByDay(): List<EventDay> =
            listOf(EventDay(date = hoy.toString(), total = -116_489L, items = movimientos))
        override suspend fun getCardPaymentCandidates(): List<FinancialEvent> = emptyList()
        override suspend fun getSmsMessages(): List<SmsMessage> = listOf(aviso)
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

    private fun nombre(nodo: SemanticsNode): String =
        nodo.config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text }?.takeIf { it.isNotBlank() }
            ?: nodo.config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString(" ")
            ?: nodo.config.getOrNull(SemanticsProperties.TestTag)
            ?: "(sin nombre, id ${nodo.id})"

    /** Lo que se toca con menos de 48 por algún lado, con su medida. */
    private fun chicos(): List<String> {
        val densidad = composeRule.density.density
        val minimo = Medida.objetivoTactil.value - 0.5f
        val nodos = composeRule.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.OnClick)).fetchSemanticsNodes()
        assertTrue(nodos.isNotEmpty(), "la pantalla no tiene nada que se toque: ¿cargó?")
        return nodos
            .filter { it.boundsInRoot.width > 0f && it.boundsInRoot.height > 0f }
            .mapNotNull { nodo ->
                // Compose extiende el toque de lo chico solo si tiene un puntero (un `clickable`, un
                // `pointerInput`); `touchBoundsInRoot` lo extiende siempre que haya un `OnClick`, así que
                // sin puntero se mide lo que se ve.
                val toque = if (tienePuntero(nodo)) nodo.touchBoundsInRoot else nodo.boundsInRoot
                val ancho = toque.width / densidad
                val alto = toque.height / densidad
                if (ancho >= minimo && alto >= minimo) null else "«${nombre(nodo)}» ${"%.0f".format(ancho)} × ${"%.0f".format(alto)} dp"
            }
    }

    /** ¿El nodo recibe toques de verdad? Un modifier de puntero en su mismo nodo de layout. */
    private fun tienePuntero(nodo: SemanticsNode): Boolean =
        nodo.layoutInfo.getModifierInfo().any { info ->
            val nombre = info.modifier::class.simpleName.orEmpty()
            listOf("Clickable", "Toggleable", "Selectable", "PointerInput", "Draggable").any { it in nombre }
        }

    private fun nadaChico(pantalla: String) {
        val fallas = chicos()
        assertTrue(fallas.isEmpty(), "En $pantalla se toca con menos de 48 × 48 dp:\n" + fallas.joinToString("\n") { "  $it" })
    }

    @Test
    fun `Inicio`() {
        val datos = DashboardData(
            summary = FinanceSummary(scope = Scope.SELF, balance = 0, ingresos = 10_000_000, egresos = 4_000_000),
            accounts = listOf(ahorros, nu),
            credits = emptyList(),
            cards = emptyList(),
            upcoming = listOf(arriendo, celular, internet),
            ocurrencias = emptyList(),
            periodoActual = PeriodoFinanciero(2026, 10),
            ajustesDePeriodo = PeriodSettings(),
            spentByCategory = mapOf("Mercado" to 392_100L, "Comida" to 236_000L),
            ultimosMovimientos = movimientos,
            pendingSms = 2,
        )
        composeRule.setContent {
            MoviTheme {
                Box(Modifier.fillMaxSize()) {
                    SduiRenderer(definition = defaultDashboardDefinition(), data = datos, modifier = Modifier.fillMaxSize(), onNavigate = {}, conBanda = true)
                }
            }
        }
        composeRule.waitForIdle()
        nadaChico("Inicio")
    }

    /**
     * Que la guarda tenga dientes: un toque armado a mano —solo semántica, sin puntero, así que Compose
     * no le extiende el área— de 20 × 20 se ve; uno de 20 con `clickable`, que sí se extiende, no.
     */
    @Test
    fun `el detector ve un toque chico sin extension y deja pasar uno con clickable`() {
        composeRule.setContent {
            MoviTheme {
                Column {
                    Box(Modifier.size(20.dp).semantics { contentDescription = "a mano"; onClick { true } })
                    Box(Modifier.size(20.dp).clickable { }.semantics { contentDescription = "con clickable" })
                }
            }
        }
        composeRule.waitForIdle()
        val vistos = chicos()
        assertTrue(vistos.any { "a mano" in it }, "no vio el toque chico: $vistos")
        assertTrue(vistos.none { "con clickable" in it }, "un clickable chico se extiende a 48: $vistos")
    }

    @Test
    fun `Pagos`() {
        montar("Arriendo") { PlanScreen(onNavigate = {}, segmento = SEGMENTO_PAGOS) }
        nadaChico("Plan · Pagos")
    }

    @Test
    fun `Por revisar`() {
        montar("Rappi") { PorRevisarScreen(onNavigate = {}) }
        nadaChico("Por revisar")
    }

    @Test
    fun `Actividad`() {
        montar("Señor Gol") { TransactionsScreen(onNavigate = {}) }
        nadaChico("Actividad")
    }

    @Test
    fun `Agregar`() {
        montar(ahorros.name) { QuickAddScreen(onDismiss = {}) }
        nadaChico("Agregar")
    }
}
