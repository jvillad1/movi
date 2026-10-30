package com.jvillada.movi.ui.plan

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.text.TextLayoutResult
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
import com.jvillada.movi.shared.model.EventDay
import com.jvillada.movi.shared.model.FinanceSummary
import com.jvillada.movi.shared.model.OccurrenceState
import com.jvillada.movi.shared.model.PaymentStatus
import com.jvillada.movi.shared.model.RecurringRule
import com.jvillada.movi.shared.model.ResumenDePeriodo
import com.jvillada.movi.shared.model.Scope
import com.jvillada.movi.shared.model.SubscriptionsResult
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.UpcomingPayment
import com.jvillada.movi.shared.model.UserProfile
import com.jvillada.movi.shared.time.epochMillisToAppDate
import com.jvillada.movi.theme.Medida
import com.jvillada.movi.theme.MoviTheme
import com.jvillada.movi.ui.budgets.PresupuestosScreen
import com.jvillada.movi.ui.components.ds.TAG_CIFRA
import com.jvillada.movi.ui.components.ds.TAG_FILA
import com.jvillada.movi.ui.components.ds.TAG_PILDORA
import com.jvillada.movi.ui.components.ds.TAG_SECCION
import com.jvillada.movi.ui.credits.CreditosScreen
import com.jvillada.movi.ui.accounts.AccountDetailScreen
import com.jvillada.movi.shared.model.AccountGroup
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.ui.periodos.PeriodosScreen
import kotlinx.datetime.Clock
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
 * **Las pantallas de Plan usan el sistema** (ola R4b): Plan, Presupuestos, Tus períodos y Créditos,
 * montadas con datos a 360 dp —el teléfono más angosto—. En cada una:
 *
 * - los títulos de sección son [com.jvillada.movi.ui.components.ds.Seccion] y van como se escriben,
 *   nunca en MAYÚSCULAS («PATRIMONIO NETO» era un rótulo suelto);
 * - toda [com.jvillada.movi.ui.components.ds.Fila] mide lo del sistema (56, o más con la letra);
 * - ninguna cifra se recorta: cada monto se dibuja entero en el ancho que tiene.
 *
 * Modelo: `NadaSeRecortaEnElInicioTest`. `sdk = [34]` y `NATIVE` para medir el texto de verdad.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h3200dp-xhdpi")
class LasPantallasDePlanUsanElSistemaTest {

    @get:Rule val composeRule = createComposeRule()

    private val hoy = epochMillisToAppDate(Clock.System.now().toEpochMilliseconds()).toString()

    private val arriendo = UpcomingPayment(
        rule = RecurringRule(
            id = "rr_arriendo", name = "Arriendo del apartamento", category = "Vivienda",
            amount = 12_392_100, dayOfMonth = 1, type = TransactionType.EXPENSE,
        ),
        dueDate = hoy, daysUntil = 0, status = PaymentStatus.UPCOMING,
    )

    private val libreInversion = CreditSummary(
        account = Account("acc_9695", "Libre inversión 9695", AccountType.LOAN, balance = 40_104_518L),
        terms = CreditTerms(
            accountId = "acc_9695", bank = "Bancolombia", principal = 80_000_000L, rateEa = 11.27,
            termMonths = 105, installment = 1_204_064L, dayOfMonth = 15, startDate = "2021-06-15",
        ),
        paidPct = 0.5,
    )

    /** La cuota la paga otra persona: la [com.jvillada.movi.ui.components.ds.Pildora] lo dice. */
    private val hipotecario = CreditSummary(
        account = Account("acc_2334", "Hipotecario 2334", AccountType.LOAN, balance = 204_183_376L),
        terms = CreditTerms(
            accountId = "acc_2334", bank = "Davibank", principal = 200_000_000L, rateEa = 15.23,
            termMonths = 240, installment = 2_613_714L, dayOfMonth = 7, startDate = "2026-07-07",
            paidBy = "Skandia",
        ),
        paidPct = 0.0,
    )

    private inner class Repo : RepositorioDePrueba() {
        override suspend fun getFinanceSummary(scope: Scope): FinanceSummary =
            FinanceSummary(scope = Scope.SELF, balance = 0, ingresos = 15_000_000, egresos = 2_000_000)
        override suspend fun getDashboardSummary(scope: Scope): DashboardSummary =
            DashboardSummary(spentByCategory = mapOf("Comida" to 1_200_000L, "Mercado" to 392_100L))
        override suspend fun getUserProfile(): UserProfile =
            UserProfile(id = "u", email = "u@local", name = "U", avatarColor = "#000000", periodCutoffDay = 25)
        override suspend fun getUpcomingPayments(): List<UpcomingPayment> = listOf(arriendo)
        override suspend fun getOccurrenceStates(): List<OccurrenceState> = emptyList()
        override suspend fun getRecurringRules(): List<RecurringRule> = listOf(arriendo.rule)
        override suspend fun getSubscriptions(): SubscriptionsResult = SubscriptionsResult(emptyList(), monthlyTotalCop = 0)
        override suspend fun getBudgets(): List<Budget> = listOf(Budget("Comida", 1_000_000L), Budget("Mercado", 12_000_000L))
        override suspend fun getEventsByDay(): List<EventDay> = emptyList()
        override suspend fun getCredits(): List<CreditSummary> = listOf(libreInversion, hipotecario)
        override suspend fun getCards(): List<CardSummary> = emptyList()
        override suspend fun getAccount(id: String): Account = hipotecario.account
        override suspend fun getEvents(accountId: String?): List<FinancialEvent> = emptyList()
        override suspend fun getPeriodos(): List<ResumenDePeriodo> = listOf(
            ResumenDePeriodo(
                id = "2026-10", nombre = "Octubre 2026", desde = "2026-09-25", hasta = "2026-10-24",
                enCurso = true, entradas = 12_392_100L, salidas = 11_845_800L, movimientos = 40,
            ),
            ResumenDePeriodo(
                id = "2026-09", nombre = "Septiembre 2026", desde = "2026-08-25", hasta = "2026-09-24",
                inicioPropio = true, entradas = 9_000_000L, salidas = 10_500_000L, movimientos = 31,
            ),
        )
    }

    @Before
    fun preparar() {
        RecurringOfferGate.clear()
        Repositories.sustitutoDePrueba = Repo()
    }

    @After
    fun limpiar() {
        Repositories.sustitutoDePrueba = null
        RecurringOfferGate.clear()
    }

    private fun montar(pantalla: @Composable () -> Unit) {
        composeRule.setContent { MoviTheme { Box(Modifier.fillMaxSize()) { pantalla() } } }
        composeRule.waitForIdle()
    }

    private fun nodos(tag: String): List<SemanticsNode> =
        composeRule.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes()

    private fun texto(nodo: SemanticsNode): String =
        nodo.config.getOrNull(SemanticsProperties.Text)?.joinToString("") { it.text }.orEmpty()

    private fun textosDentro(nodo: SemanticsNode): List<String> =
        listOf(texto(nodo)).filter { it.isNotEmpty() } + nodo.children.flatMap(::textosDentro)

    private fun disposicion(nodo: SemanticsNode): TextLayoutResult {
        val resultados = mutableListOf<TextLayoutResult>()
        nodo.config[SemanticsActions.GetTextLayoutResult].action?.invoke(resultados)
        return resultados.single()
    }

    /** El texto se dibuja entero: sin «…» y cada renglón cabe en el ancho del nodo. */
    private fun seVeEntero(nodo: SemanticsNode): Boolean {
        val d = disposicion(nodo)
        val ancho = nodo.size.width + 0.5f
        return (0 until d.lineCount).all { linea ->
            !d.isLineEllipsized(linea) && d.getLineRight(linea) - d.getLineLeft(linea) <= ancho
        } && !d.multiParagraph.didExceedMaxLines
    }

    /** Las tres reglas, sobre lo que está montado. [minimoDeSecciones]: cuántas tiene que haber. */
    private fun usaElSistema(pantalla: String, minimoDeSecciones: Int) {
        val secciones = nodos(TAG_SECCION)
        assertTrue(secciones.size >= minimoDeSecciones, "$pantalla: ${secciones.size} secciones, se esperaban $minimoDeSecciones o más")
        secciones.flatMap(::textosDentro).filter { t -> t.any { it.isLetter() } }.forEach { titulo ->
            assertTrue(titulo != titulo.uppercase(), "$pantalla: «$titulo» es un título de sección en MAYÚSCULAS")
        }

        val filas = nodos(TAG_FILA)
        assertTrue(filas.isNotEmpty(), "$pantalla: no hay ninguna Fila del sistema")
        val densidad = composeRule.density.density
        filas.forEach { fila ->
            val alto = fila.size.height / densidad
            assertTrue(alto >= Medida.altoDeFila.value - 0.5f, "$pantalla: una fila mide $alto dp, menos que ${Medida.altoDeFila}")
        }

        val cifras = nodos(TAG_CIFRA)
        assertTrue(cifras.isNotEmpty(), "$pantalla: no hay ninguna Cifra")
        cifras.forEach { cifra ->
            assertTrue(seVeEntero(cifra), "$pantalla: «${texto(cifra)}» se recorta a 360 dp")
        }
    }

    private fun hay(texto: String): Boolean =
        composeRule.onAllNodesWithText(texto, substring = true, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun `Plan usa el sistema, y los pagos del periodo llevan su pildora`() {
        montar { PlanScreen(onNavigate = {}) }
        composeRule.waitUntil(timeoutMillis = 5_000) { hay("Arriendo del apartamento") }
        usaElSistema("Plan", minimoDeSecciones = 2)
        // La fila del pago vence hoy: lo dice su píldora, no solo un color.
        // Vuelta 40: la pastilla de vencimiento dice «Vence hoy».
        assertTrue(nodos(TAG_PILDORA).any { "Vence hoy" in textosDentro(it) }, "el pago de hoy lleva la píldora «Vence hoy»")
        assertTrue(!hay(TITULO_RECORDATORIOS_SIN_CANAL), "sin recordatorios pedidos, no hay propuesta")
    }

    @Test
    fun `Presupuestos usa el sistema`() {
        montar { PresupuestosScreen(onNavigate = {}) }
        composeRule.waitUntil(timeoutMillis = 5_000) { hay("Mercado") }
        usaElSistema("Presupuestos", minimoDeSecciones = 1)
        assertTrue(nodos(TAG_PILDORA).any { "Sobrepasados · 1" in textosDentro(it) })
    }

    @Test
    fun `Tus periodos usa el sistema`() {
        montar { PeriodosScreen(onNavigate = {}) }
        composeRule.waitUntil(timeoutMillis = 5_000) { hay("25 ago – 24 sep") } // vuelta 75: el nombre único del período
        // La lista no tiene secciones: es una sola. Las marcas del período son píldoras.
        assertTrue(nodos(TAG_PILDORA).any { "En curso" in textosDentro(it) })
        assertTrue(nodos(TAG_SECCION).flatMap(::textosDentro).none { t -> t.any(Char::isLetter) && t == t.uppercase() })
    }

    @Test
    fun `Creditos usa el sistema`() {
        montar { CreditosScreen(onNavigate = {}) }
        composeRule.waitUntil(timeoutMillis = 5_000) { hay("Hipotecario 2334") }
        usaElSistema("Créditos", minimoDeSecciones = 1)
    }

    /** Vuelta de diseño 4: quién paga la cuota lo dice el detalle de la deuda, en una píldora. */
    @Test
    fun `el detalle de la deuda usa el sistema, con la pildora de quien paga`() {
        montar { AccountDetailScreen(onNavigate = {}, accountId = hipotecario.account.id, group = AccountGroup.DEUDA) }
        composeRule.waitUntil(timeoutMillis = 5_000) { hay("La paga Skandia") }
        usaElSistema("Detalle de la deuda", minimoDeSecciones = 2)
        assertTrue(nodos(TAG_PILDORA).any { "La paga Skandia" in textosDentro(it) }, "«la paga Skandia» va en una píldora")
    }
}
