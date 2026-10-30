package com.jvillada.movi.ui.credits

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import com.jvillada.movi.data.RepositorioDePrueba
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.SessionManager
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.AiChatRequest
import com.jvillada.movi.shared.model.AiChatResponse
import com.jvillada.movi.shared.model.Budget
import com.jvillada.movi.shared.model.CardSummary
import com.jvillada.movi.shared.model.CreditSummary
import com.jvillada.movi.shared.model.CreditTerms
import com.jvillada.movi.shared.model.FinanceSummary
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.HistoriaDelPatrimonio
import com.jvillada.movi.shared.model.PeriodoFinanciero
import com.jvillada.movi.shared.model.PreguntaSugerida
import com.jvillada.movi.shared.model.PuntoDelPatrimonio
import com.jvillada.movi.shared.model.Scope
import com.jvillada.movi.shared.model.UserProfile
import com.jvillada.movi.shared.model.group
import com.jvillada.movi.shared.model.nombreDelMesDe
import com.jvillada.movi.shared.model.periodoSiguiente
import com.jvillada.movi.theme.MoviTheme
import com.jvillada.movi.ui.Screen
import com.jvillada.movi.ui.accounts.AccountDetailScreen
import com.jvillada.movi.ui.accounts.CurvaDelPatrimonio
import com.jvillada.movi.ui.accounts.TAG_LEYENDA_DE_LA_CURVA
import com.jvillada.movi.ui.accounts.TAG_NOTA_DE_LOS_AJUSTES
import com.jvillada.movi.ui.ai.AIChatScreen
import com.jvillada.movi.ui.dashboard.DashboardData
import com.jvillada.movi.ui.dashboard.DashboardDataCache
import com.jvillada.movi.ui.fecha.hoyEnAppZone
import com.jvillada.movi.ui.quickadd.PestanaDeAgregar
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * # Vuelta 77 · una deuda, una cifra, en pantalla
 *
 * La ficha de un préstamo sin saldo dice lo que da su plan con «Usar este» (que abre la hoja del ajuste
 * de siempre, ya llena) y tiene UNA sola acción de saldo; la de un préstamo con saldo abre con «Pagar la cuota de …»
 * (o dice que está pagada); el plan de salida compara los dos órdenes; la curva del patrimonio usa un solo
 * verbo; y una conversación nueva de Movi AI ofrece primero las tres preguntas que no gastan IA. El abono
 * en modo Pagar lo vigilan `ElExtractoYElAbonoEnPantallaTest` y `ElAbonoQueSeTildaEnPantallaTest`, y
 * Créditos a 1.280, `ElHeroeDeCreditosVaJuntoTest`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w390dp-h2400dp-mdpi")
class Vuelta77EnPantallaTest {

    @get:Rule val composeRule = createComposeRule()

    @After
    fun limpiar() {
        Repositories.sustitutoDePrueba = null
        DashboardDataCache.data = null
        SessionManager.clear()
    }

    private val hoy = hoyEnAppZone()
    private val esteMes = PeriodoFinanciero(hoy.year, hoy.monthNumber)

    private fun terminos(id: String) = CreditTerms(
        accountId = id, bank = "Occidente", principal = 40_000_000L, rateEa = 18.0, termMonths = 60,
        installment = 980_000L, dayOfMonth = 5, startDate = "2026-01-10",
    )

    private val vehiculoSinSaldo = CreditSummary(
        account = Account("acc_veh", "Vehículo 4083", AccountType.LOAN, -980_000L),
        terms = terminos("acc_veh"),
        paidPct = null,
        cuotasPagadas = emptyList(),
    )

    private fun libre(pagadas: List<String>) = CreditSummary(
        account = Account("acc_lib", "Libre Inversión R6", AccountType.LOAN, 18_500_000L),
        terms = terminos("acc_lib").copy(bank = "Bancolombia", rateEa = 21.0, installment = 770_000L, dayOfMonth = 1, startDate = "2025-06-01"),
        paidPct = 0.26,
        cuotasPagadas = pagadas,
    )

    /** El repositorio de la ficha, que anota los ajustes de saldo pedidos. */
    private inner class Repo(private val creditos: List<CreditSummary>) : RepositorioDePrueba() {
        val ajustes = mutableListOf<Pair<String, Long>>()
        override suspend fun getCredits(): List<CreditSummary> = creditos
        override suspend fun getCards(): List<CardSummary> = emptyList()
        override suspend fun getAccount(id: String): Account = creditos.map { it.account }.first { it.id == id }
        override suspend fun getEvents(accountId: String?): List<FinancialEvent> = emptyList()
        override suspend fun getUserProfile(): UserProfile = error("sin perfil: el período es el mes de calendario")
        override suspend fun adjustCreditBalance(accountId: String, targetBalance: Long): CreditSummary {
            ajustes += accountId to targetBalance
            return creditos.first { it.account.id == accountId }
        }
    }

    private fun montarLaFicha(credito: CreditSummary, repo: Repo, navegado: MutableList<Screen> = mutableListOf()) {
        Repositories.sustitutoDePrueba = repo
        composeRule.setContent {
            MoviTheme {
                Box(Modifier.fillMaxSize()) {
                    AccountDetailScreen(onNavigate = { navegado += it }, accountId = credito.account.id, group = credito.account.type.group)
                }
            }
        }
        composeRule.waitUntil(5_000) { hay("Qué puedes hacer") }
        composeRule.waitForIdle()
    }

    private fun hay(texto: String, substring: Boolean = true) =
        composeRule.onAllNodesWithText(texto, substring = substring, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun tocar(texto: String) {
        composeRule.onNode(hasText(texto) and hasClickAction()).performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
    }

    // ── 3 · El saldo según el plan, y una sola acción de saldo ──────────────────────────────────────

    @Test
    fun `sin saldo la ficha dice lo que da el plan y Usar este escribe el ajuste de siempre`() {
        val repo = Repo(listOf(vehiculoSinSaldo))
        montarLaFicha(vehiculoSinSaldo, repo)
        val plan = assertNotNull(saldoDelPlanEnLaFicha(vehiculoSinSaldo, hoy), "con todas sus condiciones hay plan")

        assertTrue(hay(textoDelCreditoSinSaldo(vehiculoSinSaldo.account.name)))
        assertFalse(hay(AJUSTAR_EL_SALDO, substring = false))
        val propuesta = composeRule.onNodeWithTag(TAG_SALDO_SEGUN_EL_PLAN, useUnmergedTree = true)
        propuesta.assertExists()
        assertTrue(hay(textoDelSaldoDelPlanEnLaFicha(plan)))
        assertTrue(textoDelSaldoDelPlanEnLaFicha(plan).contains(" y terminas en "), "con el plan dice cuándo terminas")
        assertTrue(repo.ajustes.isEmpty(), "nunca se escribe solo")

        // Revisión: una sola fila. «Usar este» abre la hoja de siempre ya llena con la cifra del plan; se
        // guarda con «Registrar ajuste».
        assertFalse(hay(CARGAR_EL_SALDO_DEL_CREDITO, substring = false), "«Cargarlo» y «Usar este» no conviven")
        tocar(USAR_EL_SALDO_DEL_PLAN)
        assertTrue(hay(ES_LA_CIFRA_DE_TU_PLAN), "la hoja llega con la cifra del plan")
        assertTrue(repo.ajustes.isEmpty(), "abrir la hoja no escribe nada")
        tocar("Registrar ajuste")
        composeRule.waitUntil(5_000) { repo.ajustes.isNotEmpty() }
        assertEquals(listOf("acc_veh" to plan.saldo), repo.ajustes)
    }

    @Test
    fun `una sola accion de saldo - sin plan Cargarlo, y nunca Ajustar saldo al lado`() {
        // Sin la tasa no hay plan: solo «Cargarlo».
        val sinPlan = vehiculoSinSaldo.copy(terms = vehiculoSinSaldo.terms!!.copy(rateEa = 0.0))
        montarLaFicha(sinPlan, Repo(listOf(sinPlan)))
        assertTrue(hay(CARGAR_EL_SALDO_DEL_CREDITO, substring = false))
        assertFalse(hay(USAR_EL_SALDO_DEL_PLAN, substring = false))
        assertFalse(hay(AJUSTAR_EL_SALDO, substring = false), "«Cargarlo» y «Ajustar saldo» no conviven")
    }

    @Test
    fun `con saldo la ficha ofrece Ajustar saldo y no Cargarlo`() {
        val c = libre(pagadas = emptyList())
        montarLaFicha(c, Repo(listOf(c)))
        assertTrue(hay(AJUSTAR_EL_SALDO, substring = false))
        assertFalse(hay(CARGAR_EL_SALDO_DEL_CREDITO, substring = false))
    }

    // ── 4 · «Pagar la cuota» ───────────────────────────────────────────────────────────────────────

    @Test
    fun `la accion principal es Pagar la cuota y abre Pagar con el credito y su cuota`() {
        val c = libre(pagadas = emptyList())
        val navegado = mutableListOf<Screen>()
        montarLaFicha(c, Repo(listOf(c)), navegado)
        val texto = "Pagar la cuota de ${nombreDelMesDe(esteMes)} · \$770.000"
        composeRule.onNodeWithTag(TAG_PAGAR_LA_CUOTA, useUnmergedTree = true).assertExists()
        assertTrue(hay(texto, substring = false), "falta «$texto»")

        tocar(texto)
        val hoja = assertIs<Screen.QuickAdd>(navegado.last())
        assertEquals(PestanaDeAgregar.PAGO_DE_TARJETA, hoja.presetPestana)
        assertEquals("acc_lib", hoja.presetDeudaId)
        assertEquals(770_000L, hoja.presetMonto)
        assertEquals(nombreDelMesDe(esteMes), hoja.presetMesDeLaCuota)
        assertFalse(hoja.presetAbonoExtra, "es la cuota, no un abono")
    }

    @Test
    fun `con la cuota del periodo saldada dice que esta pagada, sin el visto que Figtree no tiene`() {
        val c = libre(pagadas = listOf(esteMes.prefijo))
        montarLaFicha(c, Repo(listOf(c)))
        composeRule.onNodeWithTag(TAG_CUOTA_PAGADA, useUnmergedTree = true).assertExists()
        assertTrue(hay("Cuota de ${nombreDelMesDe(esteMes)} · pagada", substring = false))
        assertFalse(hay("Pagar la cuota de"))
        assertFalse(hay("✓"))
        // Y la próxima no se ofrece como si fuera esta.
        assertFalse(hay("Cuota de ${nombreDelMesDe(periodoSiguiente(esteMes))}"))
    }

    // ── 6 · Cómo salir de tus deudas: la comparación ──────────────────────────────────────────────

    @Test
    fun `el plan de salida dice cuanto ahorra un orden sobre el otro`() {
        val cara = CreditSummary(
            account = Account("acc_cara", "Tarjeta cara", AccountType.LOAN, 3_000_000L),
            terms = terminos("acc_cara").copy(rateEa = 30.0, installment = 150_000L, dayOfMonth = 5),
            paidPct = 0.1,
        )
        val barata = CreditSummary(
            account = Account("acc_barata", "Crédito barato", AccountType.LOAN, 1_000_000L),
            terms = terminos("acc_barata").copy(rateEa = 10.0, installment = 100_000L, dayOfMonth = 5),
            paidPct = 0.1,
        )
        Repositories.sustitutoDePrueba = Repo(listOf(cara, barata))
        composeRule.setContent { MoviTheme { Box(Modifier.fillMaxSize()) { SalidaDeDeudasScreen(onNavigate = {}) } } }
        composeRule.waitUntil(5_000) { hay("Si abonas al mes extra") }
        val campo = composeRule.onNode(hasSetTextAction(), useUnmergedTree = true)
        campo.performSemanticsAction(SemanticsActions.RequestFocus)
        campo.performTextInput("200000")
        composeRule.waitUntil(5_000) { hay("Avalancha te ahorra") }
        val texto = composeRule.onNodeWithTag(TAG_COMPARACION_DE_ESTRATEGIAS, useUnmergedTree = true)
            .fetchSemanticsNode().config[SemanticsProperties.Text].joinToString { it.text }
        assertTrue(texto.matches(Regex("""Avalancha te ahorra \$[\d.]+ más que bola de nieve""")), texto)
        assertTrue(hay("Primero la de mayor tasa: así pagas menos interés en total."))
    }

    // ── 7 · La curva con un solo verbo ─────────────────────────────────────────────────────────────

    @Test
    fun `la curva abre con la cifra sin ajustes y deja los ajustes como nota sin verbo`() {
        val historia = HistoriaDelPatrimonio(
            puntos = listOf(
                PuntoDelPatrimonio("2026-08", "2026-08-31", 20_000_000L),
                PuntoDelPatrimonio("2026-09", "2026-09-30", 2_300_000L, ajustes = -18_537_900L),
                PuntoDelPatrimonio("2026-10", "2026-10-30", 2_300_000L, esHoy = true, ajustes = -18_537_900L),
            ),
        )
        composeRule.setContent { MoviTheme { CurvaDelPatrimonio(historia) } }
        composeRule.waitForIdle()
        val leyenda = composeRule.onNodeWithTag(TAG_LEYENDA_DE_LA_CURVA, useUnmergedTree = true)
            .fetchSemanticsNode().config[SemanticsProperties.Text].joinToString { it.text }
        assertEquals("Desde ago 2026, subió \$837.900", leyenda)
        val nota = composeRule.onNodeWithTag(TAG_NOTA_DE_LOS_AJUSTES, useUnmergedTree = true)
            .fetchSemanticsNode().config[SemanticsProperties.Text].joinToString { it.text }
        assertTrue(nota.startsWith("Sin contar −\$18,5M de ajustes de saldo"), nota)
        assertFalse(nota.contains("subió") || nota.contains("bajó"), "un solo verbo: $nota")
    }

    // ── 8 · Movi AI: las tres gratis primero ───────────────────────────────────────────────────────

    @Test
    fun `una conversacion nueva ofrece primero las tres preguntas sin IA`() {
        Repositories.sustitutoDePrueba = object : RepositorioDePrueba() {
            override suspend fun chatAi(request: AiChatRequest): AiChatResponse = AiChatResponse(text = "ok")
        }
        SessionManager.save("tok", "usr", "Demo", "demo@local")
        DashboardDataCache.data = DashboardData(
            summary = FinanceSummary(Scope.SELF, balance = 0L, ingresos = 22_200_000L, egresos = 33_900_000L),
            budgets = listOf(Budget("Mercado", 400_000L)),
            spentByCategory = mapOf("Mercado" to 963_456L),
        )
        composeRule.setContent { MoviTheme { Box(Modifier.fillMaxSize()) { AIChatScreen(onNavigate = {}) } } }
        composeRule.waitUntil(5_000) { hay(PreguntaSugerida.COMO_VOY.texto) }
        fun arriba(texto: String) = composeRule.onAllNodesWithText(texto, useUnmergedTree = true)
            .fetchSemanticsNodes().first().boundsInRoot.top
        val gratis = PreguntaSugerida.entries.map { arriba(it.texto) }
        val delModelo = arriba("¿Qué hago para no pasarme en Mercado el próximo período?")
        assertTrue(gratis.zipWithNext().all { (a, b) -> a < b }, "las tres en su orden: $gratis")
        assertTrue(gratis.all { it < delModelo }, "las tres gratis van antes que las del modelo")
    }
}
