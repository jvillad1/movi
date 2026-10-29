package com.jvillada.movi.ui.saldos

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import com.jvillada.movi.data.RecurringOfferGate
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.RepositorioDePrueba
import com.jvillada.movi.data.SaldosOcultosStore
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.Anomalia
import com.jvillada.movi.shared.model.Budget
import com.jvillada.movi.shared.model.DashboardSummary
import com.jvillada.movi.shared.model.EventDay
import com.jvillada.movi.shared.model.EvidenciaDeAnomalia
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.GastoDelDiaADia
import com.jvillada.movi.shared.model.OccurrenceState
import com.jvillada.movi.shared.model.PaymentStatus
import com.jvillada.movi.shared.model.PeriodSettings
import com.jvillada.movi.shared.model.PeriodoFinanciero
import com.jvillada.movi.shared.model.RecurringRule
import com.jvillada.movi.shared.model.Scope
import com.jvillada.movi.shared.model.ScreenSection
import com.jvillada.movi.shared.model.SubscriptionsResult
import com.jvillada.movi.shared.model.TipoDeAnomalia
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.UpcomingPayment
import com.jvillada.movi.shared.model.UserProfile
import com.jvillada.movi.theme.MoviTheme
import com.jvillada.movi.ui.accounts.VoidEventSheet
import com.jvillada.movi.ui.budgets.PresupuestosScreen
import com.jvillada.movi.ui.components.PLATA_OCULTA
import com.jvillada.movi.ui.dashboard.CajaEnHoy
import com.jvillada.movi.ui.dashboard.DashboardData
import com.jvillada.movi.ui.plan.TableroDeRecurrentesDePrueba
import com.jvillada.movi.ui.sdui.ParaRevisarSection
import com.jvillada.movi.ui.sdui.PatrimonioSection
import com.jvillada.movi.ui.transactions.CardPaymentCandidatesSheet
import kotlinx.datetime.LocalDate
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * # Las cifras que pasaron a la `Cifra` del sistema se ocultan (vuelta de diseño 19)
 *
 * Eran `Text` con el monto ya escrito, o la `Cifra(texto, estilo)` vieja: el flujo libre de Plan,
 * Presupuestos, la hoja de anular, los candidatos a pago de tarjeta y, en Hoy, «Tu patrimonio»,
 * «Para revisar» (con la evidencia abierta) y «Tu día más justo». Con «Ocultar los saldos» encendido
 * ninguna de esas pantallas deja un «$» seguido de un dígito en el árbol —texto ni descripción—; y
 * cada escenario tiene cifras: la prueba falla si la pantalla no llegó a pintarlas.
 *
 * El modelo es `SaldosOcultosEnPantallaTest`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h2400dp-xhdpi")
class LasCifrasDelSistemaSeOcultanTest {

    @get:Rule val composeRule = createComposeRule()

    @After
    fun limpiar() {
        Repositories.sustitutoDePrueba = null
        RecurringOfferGate.clear()
    }

    // ── Lo que hay en el árbol ───────────────────────────────────────────────

    private fun textosALaVista(): List<String> {
        val textos = mutableListOf<String>()
        fun recorrer(nodo: SemanticsNode) {
            nodo.config.getOrNull(SemanticsProperties.Text)?.forEach { textos += it.text }
            nodo.config.getOrNull(SemanticsProperties.EditableText)?.let { textos += it.text }
            nodo.config.getOrNull(SemanticsProperties.ContentDescription)?.let { textos += it }
            nodo.children.forEach(::recorrer)
        }
        recorrer(composeRule.onRoot(useUnmergedTree = true).fetchSemanticsNode())
        return textos
    }

    private val cifraDePlata = Regex("\\$\\s?\\d")

    private fun cifrasALaVista(): List<String> = textosALaVista().filter { cifraDePlata.containsMatchIn(it) }

    private fun hay(texto: String): Boolean =
        composeRule.onAllNodesWithText(texto, substring = true, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun esperar(texto: String) {
        composeRule.waitUntil(timeoutMillis = 5_000) { hay(texto) }
        composeRule.waitForIdle()
    }

    private fun montar(contenido: @Composable () -> Unit) {
        composeRule.setContent { MoviTheme { Box(Modifier.fillMaxSize()) { contenido() } } }
        composeRule.waitForIdle()
    }

    /** Las dos mitades de cada escenario: oculto no deja cifras, a la vista sí las tiene. */
    private fun ocultasYALaVista(escenario: () -> Unit) {
        escenario()
        assertTrue(cifrasALaVista().isNotEmpty(), "a la vista, el escenario tiene cifras")
    }

    private fun sinCifrasEnClaro() {
        assertEquals(emptyList(), cifrasALaVista(), "ninguna cifra de plata a la vista")
        assertTrue(hay(PLATA_OCULTA), "las cifras se pintan «$PLATA_OCULTA»")
    }

    // ── Plan: el flujo libre ─────────────────────────────────────────────────

    private val sueldo = RecurringRule(
        id = "rr_sueldo", name = "Sueldo", category = "Ingresos", amount = 12_000_000L, dayOfMonth = 25,
        type = TransactionType.INCOME,
    )
    private val arriendo = RecurringRule(
        id = "rr_arriendo", name = "Arriendo", category = "Vivienda", amount = 6_093_239L, dayOfMonth = 5,
        type = TransactionType.EXPENSE,
    )

    private fun vence(rule: RecurringRule) =
        UpcomingPayment(rule = rule, dueDate = "2026-09-25", daysUntil = 16, status = PaymentStatus.UPCOMING)

    private fun montarElTablero() {
        RecurringOfferGate.clear()
        Repositories.sustitutoDePrueba = object : RepositorioDePrueba() {
            override suspend fun getAccounts(): List<Account> =
                listOf(Account("acc-banco", "Bancolombia", AccountType.SAVINGS, 1_000_000L, "COP"))
            override suspend fun getEventsByDay(): List<EventDay> = emptyList()
            override suspend fun getCardPaymentCandidates(): List<FinancialEvent> = emptyList()
            override suspend fun getRecurringRules(): List<RecurringRule> = listOf(sueldo, arriendo)
            override suspend fun getSubscriptions(): SubscriptionsResult = SubscriptionsResult(emptyList(), monthlyTotalCop = 0L)
            override suspend fun getUpcomingPayments(): List<UpcomingPayment> = listOf(vence(sueldo), vence(arriendo))
            override suspend fun getOccurrenceStates(): List<OccurrenceState> = emptyList()
        }
        montar { TableroDeRecurrentesDePrueba(ajustesDelPeriodo = PeriodSettings(), onNavigate = {}) }
        esperar("Ingresos recurrentes")
    }

    @Test
    fun `el flujo libre de Plan no muestra cifras con los saldos ocultos`() {
        SaldosOcultosStore.poner(true)
        montarElTablero()
        sinCifrasEnClaro()
    }

    @Test
    fun `el flujo libre de Plan muestra sus cifras a la vista`() = ocultasYALaVista {
        montarElTablero()
        assertTrue(hay("+\$12.000.000"), "lo que entra, con «+»: es la Cifra del sistema")
    }

    // ── Presupuestos ────────────────────────────────────────────────────────

    private fun montarPresupuestos() {
        Repositories.sustitutoDePrueba = object : RepositorioDePrueba() {
            override suspend fun getBudgets(): List<Budget> = listOf(Budget("Mercado", 1_050_000L))
            override suspend fun getEventsByDay(): List<EventDay> = emptyList()
            override suspend fun getDashboardSummary(scope: Scope): DashboardSummary =
                DashboardSummary(spentByCategory = mapOf("Mercado" to 392_100L))
            override suspend fun getUserProfile(): UserProfile =
                UserProfile(id = "u", email = "u@local", name = "U", avatarColor = "#000000", periodCutoffDay = 25)
        }
        montar { PresupuestosScreen(onNavigate = {}) }
        esperar("Mercado")
    }

    @Test
    fun `Presupuestos no muestra cifras con los saldos ocultos`() {
        SaldosOcultosStore.poner(true)
        montarPresupuestos()
        sinCifrasEnClaro()
    }

    @Test
    fun `Presupuestos muestra el tope como cifra a la vista`() = ocultasYALaVista {
        montarPresupuestos()
        assertTrue(hay("\$1.050.000"))
    }

    // ── Las hojas: anular y los candidatos a pago de tarjeta ────────────────

    private val gasto = FinancialEvent(
        id = "ev_rappi",
        accountId = "acc-banco",
        type = TransactionType.EXPENSE,
        amount = 27_500L,
        category = "Restaurantes",
        description = "Rappi",
        timestamp = 1_788_000_000_000L,
    )

    private fun montarLaHojaDeAnular() {
        Repositories.sustitutoDePrueba = object : RepositorioDePrueba() {
            override suspend fun getEvents(accountId: String?): List<FinancialEvent> = listOf(gasto)
        }
        montar {
            VoidEventSheet(
                event = gasto,
                cuentas = listOf(Account("acc-banco", "Bancolombia", AccountType.SAVINGS, 1_000_000L, "COP")),
                onDismiss = {},
                onVoided = {},
            )
        }
        esperar("Rappi")
    }

    @Test
    fun `la hoja de anular no muestra el monto con los saldos ocultos`() {
        SaldosOcultosStore.poner(true)
        montarLaHojaDeAnular()
        sinCifrasEnClaro()
    }

    @Test
    fun `la hoja de anular muestra el gasto con su signo a la vista`() = ocultasYALaVista {
        montarLaHojaDeAnular()
        assertTrue(hay("−\$27.500"))
    }

    private fun montarLosCandidatos() {
        montar {
            CardPaymentCandidatesSheet(
                candidates = listOf(gasto.copy(description = "PAGO TARJETA MASTER", amount = 1_843_014L)),
                onDismiss = {},
                onConfirmed = {},
                onDismissedCandidate = {},
            )
        }
        // Vuelta 36: la lista dice el título de la fila, lo del banco en forma de título.
        esperar("Pago Tarjeta Master")
    }

    @Test
    fun `los candidatos a pago de tarjeta no muestran el monto con los saldos ocultos`() {
        SaldosOcultosStore.poner(true)
        montarLosCandidatos()
        sinCifrasEnClaro()
    }

    @Test
    fun `los candidatos a pago de tarjeta muestran el monto a la vista`() = ocultasYALaVista {
        montarLosCandidatos()
        assertTrue(hay("\$1.843.014"))
    }

    // ── Hoy: el patrimonio, para revisar y el día más justo ─────────────────

    private val hoy = LocalDate(2026, 10, 13)

    private val duplicado = Anomalia(
        huella = "duplicado:a,b",
        tipo = TipoDeAnomalia.COBRO_DUPLICADO,
        titulo = "¿Cobro duplicado en RAPPI COLOMBIA?",
        detalle = "2 cobros de \$45.900 en Bancolombia con menos de dos días entre uno y otro.",
        evidencia = listOf(
            EvidenciaDeAnomalia("a", "2026-09-28", "RAPPI COLOMBIA", 45_900L, cuenta = "Bancolombia"),
            EvidenciaDeAnomalia("b", "2026-09-29", "RAPPI COLOMBIA", 45_900L, cuenta = "Bancolombia"),
        ),
    )

    private val datosDeHoy = DashboardData(
        accounts = listOf(
            Account("acc-banco", "Bancolombia", AccountType.SAVINGS, 2_000_000L, "COP"),
            Account("acc-nu", "Nu", AccountType.SAVINGS, 345_678L, "COP"),
            Account("acc-carro", "Vehículo 4083", AccountType.LOAN, 177_200_000L, "COP"),
        ),
        upcoming = listOf(
            UpcomingPayment(
                rule = arriendo.copy(amount = 800_000L, dayOfMonth = 20),
                dueDate = "2026-10-20",
                daysUntil = 7,
                status = PaymentStatus.UPCOMING,
            ),
        ),
        ocurrencias = emptyList(),
        anomalias = listOf(duplicado),
        ajustesDePeriodo = PeriodSettings(cutoffDay = 25),
        periodoActual = PeriodoFinanciero(2026, 10),
    )

    private fun montarHoy() {
        Repositories.sustitutoDePrueba = object : RepositorioDePrueba() {
            override suspend fun getGastoDelDiaADia(): GastoDelDiaADia = GastoDelDiaADia(porDia = 20_000L)
        }
        montar {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                PatrimonioSection(ScreenSection(type = "PATRIMONIO", title = "Tu patrimonio"), datosDeHoy) {}
                CajaEnHoy(data = datosDeHoy, hoy = hoy, onNavigate = {})
                ParaRevisarSection(ScreenSection(type = "ALERTS", title = "Para revisar"), datosDeHoy) {}
            }
        }
        esperar("Tu día más justo")
        composeRule.onAllNodesWithText("Ver los movimientos", useUnmergedTree = true)[0].performClick()
        esperar("28 de septiembre · RAPPI COLOMBIA · Bancolombia")
    }

    @Test
    fun `Hoy no muestra las cifras del patrimonio, para revisar ni el dia mas justo con los saldos ocultos`() {
        SaldosOcultosStore.poner(true)
        montarHoy()
        sinCifrasEnClaro()
    }

    @Test
    fun `Hoy muestra esas cifras enteras a la vista`() = ocultasYALaVista {
        montarHoy()
        // Enteras, como el héroe: ya no «$177,2M».
        assertTrue(hay("\$177.200.000"), "las deudas, enteras")
        assertTrue(hay("\$2.345.678"), "tu plata, entera")
        assertTrue(hay("\$45.900"), "la evidencia de lo que se sale de lo normal")
    }
}
