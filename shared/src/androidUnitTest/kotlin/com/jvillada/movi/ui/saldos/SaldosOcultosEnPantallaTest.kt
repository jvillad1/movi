package com.jvillada.movi.ui.saldos

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.dp
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.RepositorioDePrueba
import com.jvillada.movi.data.RepositorioDePruebaDeMovimientos
import com.jvillada.movi.data.SaldosOcultosStore
import com.jvillada.movi.data.SessionManager
import com.jvillada.movi.shared.model.ADJUSTMENT_CATEGORY
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.Anomalia
import com.jvillada.movi.shared.model.CardSummary
import com.jvillada.movi.shared.model.CierreDelPeriodo
import com.jvillada.movi.shared.model.CifrasDelAnterior
import com.jvillada.movi.shared.model.CreditSummary
import com.jvillada.movi.shared.model.DashboardSummary
import com.jvillada.movi.shared.model.EventDay
import com.jvillada.movi.shared.model.EvidenciaDeAnomalia
import com.jvillada.movi.shared.model.FinanceSummary
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.ReconciliationStatus
import com.jvillada.movi.shared.model.Scope
import com.jvillada.movi.shared.model.ScreenDefinition
import com.jvillada.movi.shared.model.TipoDeAnomalia
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.UpcomingPayment
import com.jvillada.movi.shared.time.epochMillisToAppDate
import com.jvillada.movi.theme.MoviTheme
import com.jvillada.movi.ui.components.PLATA_OCULTA
import com.jvillada.movi.ui.components.ROTULO_MOSTRAR_LOS_SALDOS
import com.jvillada.movi.ui.components.ROTULO_OCULTAR_LOS_SALDOS
import com.jvillada.movi.ui.components.TAG_OJO_DE_LOS_SALDOS
import com.jvillada.movi.ui.dashboard.DURACION_DE_LA_ENTRADA_MS
import com.jvillada.movi.ui.dashboard.DashboardScreen
import com.jvillada.movi.ui.dashboard.InstantaneaDelInicio
import com.jvillada.movi.ui.dashboard.instantaneaEnMemoria
import com.jvillada.movi.ui.quickadd.QuickAddScreen
import com.jvillada.movi.ui.transactions.TransactionsScreen
import kotlinx.datetime.Clock
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
 * # «Ocultar los saldos», en pantalla
 *
 * Lo que una prueba de las funciones de formato no puede afirmar: que en Hoy y en Movimientos,
 * montados enteros, **no queda ni una cifra de plata a la vista** —ni las que formatea la UI ni las
 * que llegan armadas de `:core` (el cierre del período, lo que se sale de lo normal) ni la que va
 * en la descripción de un ajuste—; que el ojo alterna; y que el teclado de Agregar sigue mostrando
 * lo que el dueño escribe.
 *
 * «Una cifra de plata» es un «$» seguido de un dígito, en cualquier texto, campo o descripción de
 * accesibilidad del árbol.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h2400dp-xhdpi")
class SaldosOcultosEnPantallaTest {

    @get:Rule val composeRule = createComposeRule()

    @After
    fun limpiar() {
        Repositories.sustitutoDePrueba = null
        InstantaneaDelInicio.sustitutoDePrueba = null
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

    // ── Hoy ──────────────────────────────────────────────────────────────────

    private val cierre = CierreDelPeriodo(
        periodo = "2026-09",
        nombre = "Septiembre 2026",
        entro = 22_500_000,
        salio = 18_200_000,
        anterior = CifrasDelAnterior("2026-08", "Agosto 2026", 21_500_000, 18_700_000),
        quedoDelDisponible = 420_000,
        diaDelPeriodoNuevo = 1,
    )

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

    private fun entrarAHoy() {
        InstantaneaDelInicio.sustitutoDePrueba = instantaneaEnMemoria(mutableMapOf())
        SessionManager.save(token = "tok", userId = "u1", name = "Juan", email = "juan@ejemplo.com")
        Repositories.sustitutoDePrueba = object : RepositorioDePrueba() {
            override suspend fun getScreen(slug: String, cachedVersion: Int?): ScreenDefinition? = null
            override suspend fun getFinanceSummary(scope: Scope) =
                FinanceSummary(scope = Scope.SELF, balance = 0, ingresos = 10_000_000, egresos = 4_000_000, eventCount = 12)
            override suspend fun getAccounts(): List<Account> = listOf(
                Account("a1", "Ahorros", AccountType.SAVINGS, 2_000_000),
                Account("a2", "Nequi", AccountType.CHECKING, 345_678),
                Account("c1", "Nu", AccountType.CREDIT_CARD, 1_250_000),
            )
            override suspend fun getCredits(): List<CreditSummary> = emptyList()
            override suspend fun getCards(): List<CardSummary> = emptyList()
            override suspend fun getUpcomingPayments(): List<UpcomingPayment> = emptyList()
            override suspend fun getDashboardSummary(scope: Scope) = DashboardSummary()
            override suspend fun getCierreDelPeriodo(): CierreDelPeriodo? = cierre
            override suspend fun getAnomalias(): List<Anomalia> = listOf(duplicado)
        }
        composeRule.setContent { MoviTheme { DashboardScreen(onNavigate = {}) } }
        composeRule.waitForIdle()
        // La cifra entra contando: se deja terminar.
        composeRule.mainClock.advanceTimeBy(DURACION_DE_LA_ENTRADA_MS.toLong() + 200L)
        composeRule.waitForIdle()
    }

    @Test
    fun `con los saldos ocultos Hoy no muestra ninguna cifra de plata`() {
        SaldosOcultosStore.poner(true)
        entrarAHoy()

        assertTrue(hay("Cerró tu período"), "el escenario llegó entero: la tarjeta del cierre está")
        assertTrue(hay("¿Cobro duplicado en RAPPI COLOMBIA?"), "y lo que se sale de lo normal")
        assertEquals(emptyList(), cifrasALaVista(), "ninguna cifra de plata a la vista")
        assertTrue(hay(PLATA_OCULTA), "las cifras se pintan «$PLATA_OCULTA»")
        composeRule.onNodeWithContentDescription(ROTULO_MOSTRAR_LOS_SALDOS, useUnmergedTree = true).assertExists()
    }

    @Test
    fun `a la vista Hoy muestra sus cifras como siempre`() {
        entrarAHoy()
        assertTrue(cifrasALaVista().isNotEmpty(), "el escenario sí tiene cifras: la prueba de arriba no pasa de vacía")
        assertFalse(hay(PLATA_OCULTA))
        assertTrue(hay("entró \$22.500.000, salió \$18.200.000"))
    }

    @Test
    fun `tocar el ojo alterna, y al mostrar todo vuelve como estaba`() {
        entrarAHoy()
        val antes = textosALaVista()
        composeRule.onNodeWithContentDescription(ROTULO_OCULTAR_LOS_SALDOS, useUnmergedTree = true).assertExists()

        composeRule.onNodeWithTag(TAG_OJO_DE_LOS_SALDOS, useUnmergedTree = true).performClick()
        composeRule.waitForIdle()
        assertTrue(SaldosOcultosStore.ocultos)
        assertEquals(emptyList(), cifrasALaVista())
        composeRule.onNodeWithContentDescription(ROTULO_MOSTRAR_LOS_SALDOS, useUnmergedTree = true).assertExists()

        composeRule.onNodeWithTag(TAG_OJO_DE_LOS_SALDOS, useUnmergedTree = true).performClick()
        composeRule.waitForIdle()
        assertFalse(SaldosOcultosStore.ocultos)
        assertEquals(antes, textosALaVista(), "al mostrar, Hoy dice exactamente lo de antes")
    }

    // ── Movimientos ──────────────────────────────────────────────────────────

    private val hoyIso = epochMillisToAppDate(Clock.System.now().toEpochMilliseconds()).toString()

    private fun movimiento(id: String, tipo: TransactionType, monto: Long, descripcion: String, categoria: String) =
        FinancialEvent(
            id = id,
            accountId = "a1",
            type = tipo,
            amount = monto,
            category = categoria,
            description = descripcion,
            timestamp = Clock.System.now().toEpochMilliseconds(),
            reconciliationStatus = ReconciliationStatus.RECONCILED,
            countsAsCashFlow = categoria != ADJUSTMENT_CATEGORY,
        )

    @Test
    fun `con los saldos ocultos Movimientos tampoco muestra cifras`() {
        SaldosOcultosStore.poner(true)
        val dia = EventDay(
            date = hoyIso,
            total = -28_500L,
            items = listOf(
                movimiento("e1", TransactionType.EXPENSE, 18_500L, "Carnes y Legumbres Santa Elena", "Comida"),
                movimiento("e2", TransactionType.INCOME, 2_500_000L, "Nómina", "Ingreso"),
                movimiento("e3", TransactionType.INCOME, 9_006L, "Ajuste al saldo del banco — quedó en \$498.547", ADJUSTMENT_CATEGORY),
            ),
        )
        Repositories.sustitutoDePrueba = object : RepositorioDePruebaDeMovimientos() {
            override suspend fun getAccounts(): List<Account> = listOf(Account("a1", "Ahorros", AccountType.SAVINGS, 853_037L))
            override suspend fun getEventsByDay(): List<EventDay> = listOf(dia)
            override suspend fun getCardPaymentCandidates(): List<FinancialEvent> = emptyList()
        }
        composeRule.setContent { MoviTheme { Box(Modifier.fillMaxSize()) { TransactionsScreen(onNavigate = {}) } } }
        composeRule.waitUntil(5_000) { hay("Carnes y Legumbres Santa Elena") }
        // El renglón de los ajustes se despliega: su descripción también lleva una cifra.
        composeRule.onAllNodesWithText("Ajustaste el saldo", substring = true, useUnmergedTree = true)
            .fetchSemanticsNodes().firstOrNull()?.let {
                composeRule.onNodeWithText("Ajustaste el saldo", substring = true, useUnmergedTree = true).performClick()
                composeRule.waitForIdle()
            }

        assertEquals(emptyList(), cifrasALaVista(), "ninguna cifra de plata a la vista")
        assertTrue(hay(PLATA_OCULTA))
        assertTrue(hay("Nómina"), "lo que no es plata sigue a la vista")
    }

    // ── El teclado de Agregar ────────────────────────────────────────────────

    @Test
    fun `el teclado de Agregar si muestra lo que se escribe`() {
        SaldosOcultosStore.poner(true)
        Repositories.sustitutoDePrueba = object : RepositorioDePrueba() {
            override suspend fun getAccounts(): List<Account> = listOf(Account("a1", "Bancolombia Ahorros", AccountType.SAVINGS, 15_534_069))
        }
        composeRule.setContent {
            MoviTheme {
                Column(Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxWidth().weight(1f)) { QuickAddScreen(onDismiss = {}, onSaved = {}) }
                    Spacer(Modifier.height(64.dp))
                }
            }
        }
        composeRule.waitForIdle()
        listOf("4", "5", "000").forEach { tecla ->
            composeRule.onNodeWithText(tecla).performSemanticsAction(SemanticsActions.OnClick)
            composeRule.waitForIdle()
        }
        assertTrue(hay("\$45.000"), "lo que el dueño está escribiendo no se tapa")
        assertFalse(hay("15.534.069"), "el saldo de la cuenta, en cambio, sí")
    }
}
