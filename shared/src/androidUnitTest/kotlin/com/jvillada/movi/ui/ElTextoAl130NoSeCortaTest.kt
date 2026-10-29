package com.jvillada.movi.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import com.jvillada.movi.data.DiasPlegadosStore
import com.jvillada.movi.data.RecurringOfferGate
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.RepositorioDePrueba
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
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
import com.jvillada.movi.theme.MoviTheme
import com.jvillada.movi.ui.components.ds.TAG_CUADRICULA_DE_ACCIONES
import com.jvillada.movi.ui.dashboard.DashboardData
import com.jvillada.movi.ui.plan.PlanScreen
import com.jvillada.movi.ui.plan.SEGMENTO_PAGOS
import com.jvillada.movi.ui.porrevisar.PorRevisarScreen
import com.jvillada.movi.ui.quickadd.QuickAddScreen
import com.jvillada.movi.ui.recurrentes.ETIQUETA_ANOTAR
import com.jvillada.movi.ui.recurrentes.TAG_FILA_DE_FACTURA
import com.jvillada.movi.ui.sdui.SduiRenderer
import com.jvillada.movi.ui.sdui.TAG_HEROE_EN_TARJETA
import com.jvillada.movi.ui.sdui.TAG_PROXIMO_PAGO
import com.jvillada.movi.ui.sms.TAG_LO_QUE_DICE_EL_BANCO
import com.jvillada.movi.ui.sms.TAG_TARJETA_DE_AVISO
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
 * # Con la letra al 130 % nada se corta (vuelta 55, accesibilidad)
 *
 * La revisión del 28-oct no pudo probar la letra grande en la web (no toma la escala del sistema).
 * Acá se prueba en Android, a 360 dp —el teléfono más angosto— con `fontScale = 1.3`, en lo que ya
 * iba al límite con la letra normal: el héroe, las ocho acciones y «Próximo pago» de Inicio; la fila
 * de una factura con «Anotar pago» en Pagos; la tarjeta de un aviso en Por revisar; y Agregar en Gasto.
 *
 * «Nada se corta» es: ningún renglón con «…», ningún texto con más renglones de los que le caben,
 * ningún renglón más ancho que su caja y ninguna caja fuera de la pantalla.
 *
 * `sdk = [34]` y `NATIVE`: hace falta el motor de texto real para medir.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h3200dp-xhdpi")
class ElTextoAl130NoSeCortaTest {

    private fun hasClickLabel(rotulo: String) = SemanticsMatcher("onClickLabel = $rotulo") {
        it.config.getOrNull(SemanticsActions.OnClick)?.label == rotulo
    }

    @get:Rule val composeRule = createComposeRule()

    private val escala = 1.3f

    private val hoyMs = Clock.System.now().toEpochMilliseconds()
    private val hoy = epochMillisToAppDate(hoyMs)

    private val ahorros = Account("acc-bc", "Bancolombia Ahorros", AccountType.SAVINGS, 13_845_800L)

    private fun pago(id: String, nombre: String, monto: Long, dias: Int) = UpcomingPayment(
        rule = RecurringRule(id = id, name = nombre, category = "Servicios", amount = monto, dayOfMonth = 1, type = TransactionType.EXPENSE),
        dueDate = LocalDate.fromEpochDays(hoy.toEpochDays() + dias).toString(),
        daysUntil = dias,
        status = if (dias < 0) PaymentStatus.OVERDUE else if (dias == 0) PaymentStatus.DUE_TODAY else PaymentStatus.UPCOMING,
    )

    /** El nombre largo de una factura de verdad: el que más empuja a «Anotar pago». */
    private val factura = pago("rr_admin", "Administración del conjunto", 1_245_900, -12)
    private val celular = pago("rr_cel", "Celular", 53_000, 3)

    private val aviso = SmsMessage(
        id = "s1", time = "${LocalDate.fromEpochDays(hoy.toEpochDays() - 1)} 11:02", bank = "Bancolombia",
        text = "Compra aprobada por \$1.242.500 en RESTAURANTE EL CIELO el 02/09/2026.", state = SMS_STATE_PENDING,
        det = "Restaurante El Cielo",
    )

    private inner class Repo : RepositorioDePrueba() {
        override suspend fun getAccounts(): List<Account> = listOf(ahorros)
        override suspend fun getUserProfile(): UserProfile =
            UserProfile(id = "u", email = "u@local", name = "Juan", avatarColor = "#000000", periodCutoffDay = 1)
        override suspend fun getUpcomingPayments(): List<UpcomingPayment> = listOf(factura, celular)
        override suspend fun getOccurrenceStates(): List<OccurrenceState> = emptyList()
        override suspend fun getRecurringRules(): List<RecurringRule> = listOf(factura.rule, celular.rule)
        override suspend fun getSubscriptions(): SubscriptionsResult = SubscriptionsResult(emptyList(), monthlyTotalCop = 0)
        override suspend fun getEventsByDay(): List<EventDay> = emptyList()
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

    private fun montar(esperar: String?, pantalla: @Composable () -> Unit) {
        composeRule.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(base.density, escala)) {
                MoviTheme { Box(Modifier.fillMaxSize()) { pantalla() } }
            }
        }
        if (esperar != null) {
            composeRule.waitUntil(timeoutMillis = 5_000) {
                composeRule.onAllNodesWithText(esperar, substring = true, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
            }
        }
        composeRule.waitForIdle()
    }

    private fun texto(nodo: SemanticsNode): String =
        nodo.config.getOrNull(SemanticsProperties.Text)?.joinToString("") { it.text }.orEmpty()

    private fun textosDentroDe(raiz: SemanticsNode): List<SemanticsNode> = textosConDesplazamiento(raiz).map { it.first }

    /**
     * Cada texto con si vive en una fila que se desplaza de lado (los chips de categorías de Agregar):
     * esa puede seguir fuera de la pantalla a propósito, así que a esa no se le pide estar adentro.
     */
    private fun textosConDesplazamiento(raiz: SemanticsNode): List<Pair<SemanticsNode, Boolean>> {
        fun bajar(n: SemanticsNode, deLado: Boolean): List<Pair<SemanticsNode, Boolean>> {
            val ahora = deLado || n.config.getOrNull(SemanticsProperties.HorizontalScrollAxisRange) != null
            return (if (n.config.getOrNull(SemanticsActions.GetTextLayoutResult) != null) listOf(n to ahora) else emptyList()) +
                n.children.flatMap { bajar(it, ahora) }
        }
        return bajar(raiz, false)
    }

    /** Lo que se corta o se sale, dentro de las raíces dadas. */
    private fun cortados(raices: List<SemanticsNode>): List<String> {
        val anchoDePantalla = composeRule.onRoot(useUnmergedTree = true).fetchSemanticsNode().size.width + 0.5f
        return raices.flatMap(::textosConDesplazamiento).mapNotNull { (nodo, deLado) ->
            val r = mutableListOf<TextLayoutResult>()
            nodo.config[SemanticsActions.GetTextLayoutResult].action?.invoke(r)
            val d = r.firstOrNull() ?: return@mapNotNull null
            // El texto del banco plegado es una muestra a propósito: «Ver el mensaje entero» lo abre
            // (la prueba de la tarjeta del aviso vigila que esté). No cuenta como cortado.
            if (nodo.config.getOrNull(SemanticsProperties.TestTag) == TAG_LO_QUE_DICE_EL_BANCO) return@mapNotNull null
            val ancho = nodo.size.width + 0.5f
            val limites = nodo.boundsInRoot
            val problema = when {
                (0 until d.lineCount).any { d.isLineEllipsized(it) } -> "se corta con «…»"
                d.multiParagraph.didExceedMaxLines -> "no le caben los renglones"
                (0 until d.lineCount).any { d.getLineRight(it) - d.getLineLeft(it) > ancho } -> "es más ancho que su caja"
                !deLado && (limites.left < -0.5f || limites.right > anchoDePantalla) -> "se sale de la pantalla"
                else -> null
            }
            problema?.let { "«${texto(nodo)}» $it" }
        }
    }

    private fun conTag(tag: String): List<SemanticsNode> =
        composeRule.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes()

    private fun nadaSeCorta(donde: String, raices: List<SemanticsNode>) {
        assertTrue(raices.isNotEmpty(), "No encontré $donde")
        val fallas = cortados(raices)
        assertTrue(fallas.isEmpty(), "Con la letra al 130 % a 360 dp, en $donde:\n" + fallas.joinToString("\n") { "  $it" })
    }

    @Test
    fun `Inicio, el heroe, las acciones y el proximo pago`() {
        val datos = DashboardData(
            summary = FinanceSummary(scope = Scope.SELF, balance = 0, ingresos = 10_000_000, egresos = 4_000_000),
            accounts = listOf(ahorros),
            credits = emptyList(),
            cards = emptyList(),
            upcoming = listOf(factura, celular),
            ocurrencias = emptyList(),
            periodoActual = PeriodoFinanciero(2026, 10),
            ajustesDePeriodo = PeriodSettings(),
            spentByCategory = mapOf("Mercado" to 392_100L),
            pendingSms = 3,
            movimientosPorConfirmar = 1,
        )
        montar(null) {
            SduiRenderer(definition = defaultDashboardDefinition(), data = datos, modifier = Modifier.fillMaxSize(), onNavigate = {}, conBanda = true)
        }
        nadaSeCorta("el héroe", conTag(TAG_HEROE_EN_TARJETA))
        val acciones = conTag(TAG_CUADRICULA_DE_ACCIONES)
        assertTrue(acciones.flatMap(::textosDentroDe).size >= 8, "las ocho acciones: ${acciones.flatMap(::textosDentroDe).map(::texto)}")
        nadaSeCorta("las acciones", acciones)
        nadaSeCorta("el próximo pago", conTag(TAG_PROXIMO_PAGO))
    }

    @Test
    fun `Pagos, la fila de una factura con Anotar pago`() {
        montar(ETIQUETA_ANOTAR) { PlanScreen(onNavigate = {}, segmento = SEGMENTO_PAGOS) }
        nadaSeCorta("la fila de la factura", conTag(TAG_FILA_DE_FACTURA))
    }

    @Test
    fun `Por revisar, la tarjeta de un aviso`() {
        montar("Restaurante El Cielo") { PorRevisarScreen(onNavigate = {}) }
        nadaSeCorta("la tarjeta del aviso", conTag(TAG_TARJETA_DE_AVISO))
        // Lo único que se muestra plegado, el texto del banco, se puede abrir entero.
        assertTrue(
            composeRule.onAllNodes(hasClickLabel("Ver el mensaje entero"), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty(),
            "el texto del banco plegado no ofrece «Ver el mensaje entero»",
        )
    }

    @Test
    fun `Agregar, en Gasto`() {
        montar(ahorros.name) { QuickAddScreen(onDismiss = {}) }
        nadaSeCorta("Agregar", listOf(composeRule.onRoot(useUnmergedTree = true).fetchSemanticsNode()))
    }
}
