package com.jvillada.movi.ui.porrevisar

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.text.TextLayoutResult
import com.jvillada.movi.data.DiasPlegadosStore
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.RepositorioDePruebaDeMovimientos
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.CardSummary
import com.jvillada.movi.shared.model.CreditSummary
import com.jvillada.movi.shared.model.EventDay
import com.jvillada.movi.shared.model.FinanceSummary
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.GrupoQueSaldaElAviso
import com.jvillada.movi.shared.model.ParsedSms
import com.jvillada.movi.shared.model.PeriodSettings
import com.jvillada.movi.shared.model.PeriodoFinanciero
import com.jvillada.movi.shared.model.PropuestaDelAviso
import com.jvillada.movi.shared.model.PropuestasDeLaBandeja
import com.jvillada.movi.shared.model.SMS_STATE_PENDING
import com.jvillada.movi.shared.model.Scope
import com.jvillada.movi.shared.model.SmsMessage
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.UserProfile
import com.jvillada.movi.shared.model.defaultDashboardDefinition
import com.jvillada.movi.theme.MoviTheme
import com.jvillada.movi.ui.dashboard.DashboardData
import com.jvillada.movi.ui.fecha.hoyEnAppZone
import com.jvillada.movi.ui.sdui.SduiRenderer
import com.jvillada.movi.ui.sdui.TAG_FRANJA_POR_CONFIRMAR
import com.jvillada.movi.ui.sms.TAG_CONFIRMAR_EN_LA_FILA
import com.jvillada.movi.ui.sms.TAG_GRUPO_QUE_SALDA
import com.jvillada.movi.ui.transactions.TAG_TARJETA_DE_AVISOS
import com.jvillada.movi.ui.transactions.TransactionsScreen
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * # Vuelta 85 en pantalla: «Por confirmar» con signo, el lote sin ingresos y «¿Salda Casa con Andrés?»
 *
 * Los avisos de la revisión 9 a 360 dp: dos gastos (Juan Valdez, Carulla), el giro de Andrés —que
 * saldaría Casa con Andrés— y un ingreso de origen desconocido. Todos entendidos por Movi.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h2400dp-xhdpi")
class Vuelta85EnPantallaTest {

    @get:Rule val composeRule = createComposeRule()

    private val hoy = hoyEnAppZone().toString()
    private val ahorros = Account("acc-4057", "Bancolombia Ahorros 4057", AccountType.SAVINGS, 3_000_000L, "COP")

    private fun aviso(id: String, hora: String, texto: String) =
        SmsMessage(id = id, time = "$hoy $hora", bank = "85540", state = SMS_STATE_PENDING, det = "", text = texto)

    private val deAndres = aviso("andres", "13:05", "Bancolombia: Recibiste \$539.625 de la cuenta *31234567890 en tu cuenta *4057 el 30/10/2026 a las 13:05.")
    private val cafe = aviso("cafe", "12:10", "Bancolombia: Compraste \$32.000,00 en JUAN VALDEZ CAFE con tu T.Deb *4057, el 30/10/2026 a las 12:10.")
    private val carulla = aviso("carulla", "11:00", "Bancolombia: Compraste \$262.400,00 en CARULLA con tu T.Deb *4057, el 30/10/2026 a las 11:00.")
    private val desconocido = aviso("desconocido", "10:00", "Bancolombia: Recibiste \$480.000 de la cuenta *77700012345 en tu cuenta *4057 el 30/10/2026 a las 10:00.")

    private val casa = GrupoQueSaldaElAviso("esp_casa", "Casa con Andrés", "Andrés Rojas", debe = 539_625L)

    private val propuestas = PropuestasDeLaBandeja(
        propuestas = listOf(
            PropuestaDelAviso(
                "andres",
                leido = ParsedSms(539_625.0, "Transferencia de Andrés Rojas", TransactionType.INCOME, "Transferencia", aprendidoDe = "x"),
                saldaUnGrupo = casa,
            ),
            PropuestaDelAviso("cafe", leido = ParsedSms(32_000.0, "JUAN VALDEZ CAFE", TransactionType.EXPENSE, "Restaurantes", aprendidoDe = "x")),
            PropuestaDelAviso("carulla", leido = ParsedSms(262_400.0, "CARULLA", TransactionType.EXPENSE, "Mercado", aprendidoDe = "x")),
            PropuestaDelAviso(
                "desconocido",
                leido = ParsedSms(480_000.0, "Transferencia de *2345", TransactionType.INCOME, "Transferencia", aprendidoDe = "x"),
            ),
        ),
        categoriasDelDueno = listOf("Restaurantes", "Mercado", "Transferencia"),
    )

    private val avisos = listOf(deAndres, cafe, carulla, desconocido)

    private inner class Repo : RepositorioDePruebaDeMovimientos() {
        override suspend fun getSmsMessages(): List<SmsMessage> = avisos
        override suspend fun getEventsByDay(): List<EventDay> = emptyList()
        override suspend fun getCardPaymentCandidates(): List<FinancialEvent> = emptyList()
        override suspend fun getAccounts(): List<Account> = listOf(ahorros)
        override suspend fun getDestinos(): List<com.jvillada.movi.shared.model.DestinoConocido> = emptyList()
        override suspend fun getUserProfile(): UserProfile =
            UserProfile(id = "u1", email = "laura@movi.test", name = "Laura", avatarColor = "#4F7CFF", periodCutoffDay = 1)
        override suspend fun getPropuestasDeLaBandeja(): PropuestasDeLaBandeja = propuestas
    }

    @After
    fun limpiar() {
        Repositories.sustitutoDePrueba = null
        DiasPlegadosStore.clear()
    }

    private fun hay(texto: String, substring: Boolean = true): Boolean =
        composeRule.onAllNodesWithText(texto, substring = substring, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun conTag(tag: String) = composeRule.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes()

    private fun texto(nodo: SemanticsNode): String =
        nodo.config.getOrNull(SemanticsProperties.Text)?.joinToString("") { it.text }.orEmpty()

    /** Los textos dentro de [raiz] que se cortan con «…» o no les caben los renglones. */
    private fun cortados(raiz: SemanticsNode): List<String> {
        fun textos(n: SemanticsNode): List<SemanticsNode> =
            (if (n.config.getOrNull(SemanticsActions.GetTextLayoutResult) != null) listOf(n) else emptyList()) + n.children.flatMap(::textos)
        return textos(raiz).mapNotNull { nodo ->
            val r = mutableListOf<TextLayoutResult>()
            nodo.config[SemanticsActions.GetTextLayoutResult].action?.invoke(r)
            val d = r.firstOrNull() ?: return@mapNotNull null
            when {
                (0 until d.lineCount).any { d.isLineEllipsized(it) } -> "«${texto(nodo)}» se corta con «…»"
                d.multiParagraph.didExceedMaxLines -> "«${texto(nodo)}» no le caben los renglones"
                else -> null
            }
        }
    }

    // ── Inicio ───────────────────────────────────────────────────────────────────

    /** Lo que manda el server con los cuatro avisos: $294.400 que salen en 2, $1.019.625 que entran en 2. */
    private val inicio = DashboardData(
        summary = FinanceSummary(scope = Scope.SELF, balance = 0, ingresos = 10_000_000, egresos = 4_000_000),
        accounts = listOf(ahorros),
        credits = emptyList<CreditSummary>(),
        cards = emptyList<CardSummary>(),
        upcoming = emptyList(),
        ocurrencias = emptyList(),
        periodoActual = PeriodoFinanciero(2026, 10),
        ajustesDePeriodo = PeriodSettings(),
        pendingSms = 4,
        porConfirmarSuman = 1_314_025L,
        porConfirmarSalen = 294_400L,
        porConfirmarEntran = 1_019_625L,
        avisosQueSalen = 2,
        avisosQueEntran = 2,
    )

    @Test
    fun `la franja del Inicio dice lo que sale y lo que entra con su signo, sin cortarse a 360`() {
        composeRule.setContent {
            MoviTheme {
                Box(Modifier.fillMaxSize()) {
                    SduiRenderer(definition = defaultDashboardDefinition(), data = inicio, modifier = Modifier.fillMaxSize(), onNavigate = {}, conBanda = true)
                }
            }
        }
        composeRule.waitForIdle()
        assertTrue(hay("−\$294.400 en 2 salidas · +\$1.019.625 en 2 entradas", substring = false))
        assertTrue(!hay("\$1.314.025"), "la suma que mezclaba entradas con salidas ya no se dice")
        val fallas = cortados(conTag(TAG_FRANJA_POR_CONFIRMAR).single())
        assertTrue(fallas.isEmpty(), fallas.joinToString("\n"))
    }

    @Test
    fun `con un server viejo la franja dice la suma de siempre`() {
        composeRule.setContent {
            MoviTheme {
                Box(Modifier.fillMaxSize()) {
                    SduiRenderer(
                        definition = defaultDashboardDefinition(),
                        data = inicio.copy(porConfirmarSalen = null, porConfirmarEntran = null, avisosQueSalen = 0, avisosQueEntran = 0),
                        modifier = Modifier.fillMaxSize(), onNavigate = {}, conBanda = true,
                    )
                }
            }
        }
        composeRule.waitForIdle()
        assertTrue(hay("\$1.314.025 en 4 avisos", substring = false))
    }

    // ── Por revisar ──────────────────────────────────────────────────────────────

    @Test
    fun `el lote no lleva ingresos, cada uno sigue con su Confirmar, y el de Andres dice que saldaria Casa`() {
        Repositories.sustitutoDePrueba = Repo()
        composeRule.setContent { MoviTheme { Box(Modifier.fillMaxSize()) { PorRevisarScreen(onNavigate = {}) } } }
        composeRule.waitUntil(timeoutMillis = 5_000) { conTag(TAG_CONFIRMAR_EN_LA_FILA).size == 4 }
        composeRule.waitForIdle()

        // Los cuatro tienen su «Confirmar» en la fila; el lote, solo los dos gastos.
        assertTrue(hay("Confirmar 2", substring = false), "el lote es de los gastos")
        assertTrue(!hay("Confirmar 4", substring = false) && !hay("Confirmar 3", substring = false))
        // «¿Salda Casa con Andrés?» antes de confirmar, en la tarjeta del giro.
        val linea = conTag(TAG_GRUPO_QUE_SALDA).single()
        assertEquals(
            "¿Salda Casa con Andrés? Andrés Rojas te debe \$539.625. Al confirmarlo, Movi te lo pregunta.",
            texto(linea),
        )
        assertTrue(hay("Transferencia de Andrés Rojas"))
        // Ningún título con la cola del aviso (la evidencia del banco sí la trae, y se queda).
        assertTrue(!hay("La Cuenta 31234567890"), "ningún título con la cola del aviso")
        assertTrue(hay("Transferencia de *2345"), "el desconocido, por sus últimos cuatro")
    }

    // ── Actividad ────────────────────────────────────────────────────────────────

    @Test
    fun `la tarjeta de Actividad dice lo mismo con signo y confirma solo los gastos`() {
        DiasPlegadosStore.clear()
        Repositories.sustitutoDePrueba = Repo()
        composeRule.setContent { MoviTheme { Box(Modifier.fillMaxSize()) { TransactionsScreen(onNavigate = {}) } } }
        composeRule.waitUntil(timeoutMillis = 5_000) { hay("por revisar") && hay("Confirmar 2") }
        composeRule.waitForIdle()

        assertTrue(hay("−\$294.400 en 2 salidas · +\$1.019.625 en 2 entradas", substring = false))
        val fallas = cortados(conTag(TAG_TARJETA_DE_AVISOS).single())
        assertTrue(fallas.isEmpty(), fallas.joinToString("\n"))
    }
}
