package com.jvillada.movi.ui.transactions

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onParent
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import com.jvillada.movi.data.DiasPlegadosStore
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.RepositorioDePruebaDeMovimientos
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.EventDay
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.ParsedSms
import com.jvillada.movi.shared.model.PropuestaDelAviso
import com.jvillada.movi.shared.model.PropuestasDeLaBandeja
import com.jvillada.movi.shared.model.ReconciliationStatus
import com.jvillada.movi.shared.model.SMS_STATE_CONFIRMED
import com.jvillada.movi.shared.model.SMS_STATE_PENDING
import com.jvillada.movi.shared.model.SmsMessage
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.UserProfile
import com.jvillada.movi.shared.time.epochMillisToAppDate
import com.jvillada.movi.theme.MoviTheme
import com.jvillada.movi.ui.Screen
import kotlinx.datetime.Clock
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * # Actividad por dentro, montada (vuelta 40)
 *
 * La tarjeta de los avisos del banco aparece con avisos y no ocupa nada sin ellos; «Confirmar los N»
 * pregunta con la lista corta y confirma **solo** lo que la fila de «Por revisar» confirmaría (por el
 * mismo `confirmarLoEntendido`), y lo demás se queda; «Revisar uno por uno» abre la bandeja. El
 * encabezado de cada día dice «Gastaste $X», y el chip «Personas» deja solo lo repartido.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h1600dp-xhdpi")
class ActividadPorDentroEnPantallaTest {

    @get:Rule val composeRule = createComposeRule()

    private val hoy = epochMillisToAppDate(Clock.System.now().toEpochMilliseconds()).toString()
    private val ahorros = Account("acc-8133", "Bancolombia Ahorros 8133", AccountType.SAVINGS, 5_000_000L, "COP")

    private fun aviso(id: String, texto: String, estado: String = SMS_STATE_PENDING) =
        SmsMessage(id = id, time = "2026-10-10 08:10", bank = "85540", text = texto, state = estado, det = "")

    private val cafe = aviso("cafe", "Bancolombia: Compraste \$46.000,00 en TOSTAO CAFE con tu T.Deb *8133, el 10/10/2026 a las 08:10.")
    private val pan = aviso("pan", "Bancolombia: Compraste \$9.000,00 en PANADERIA con tu T.Deb *8133, el 10/10/2026 a las 09:10.")
    private val exito = aviso("exito", "Bancolombia: Compraste \$80.000,00 en EXITO con tu T.Deb *8133, el 10/10/2026 a las 10:00.")
    private val yaAnotado = FinancialEvent(
        id = "ev-exito", accountId = ahorros.id, type = TransactionType.EXPENSE, amount = 80_000L,
        category = "Mercado", description = "Éxito", timestamp = 0L,
    )
    private val propuestas = PropuestasDeLaBandeja(
        propuestas = listOf(
            PropuestaDelAviso("cafe", leido = ParsedSms(46_000.0, "TOSTAO CAFE", TransactionType.EXPENSE, "Comida", aprendidoDe = "x")),
            PropuestaDelAviso("pan", leido = ParsedSms(9_000.0, "PANADERIA", TransactionType.EXPENSE, "Comida", aprendidoDe = "x")),
            PropuestaDelAviso("exito", leido = ParsedSms(80_000.0, "EXITO", TransactionType.EXPENSE, "Mercado", aprendidoDe = "x"), coincidencias = listOf(yaAnotado)),
        ),
        categoriasDelDueno = listOf("Comida", "Mercado"),
    )

    private fun gasto(id: String, descripcion: String, monto: Long, espacioId: String? = null, parteAjena: Long = 0L) = FinancialEvent(
        id = id, accountId = ahorros.id, type = TransactionType.EXPENSE, amount = monto, category = "Comida",
        description = descripcion, timestamp = Clock.System.now().toEpochMilliseconds(),
        reconciliationStatus = ReconciliationStatus.RECONCILED, countsAsCashFlow = true,
        espacioId = espacioId, parteAjena = parteAjena,
    )

    private val almuerzo = gasto("e-almuerzo", "Señor Gol", 46_489L)
    private val mercado = gasto("e-mercado", "Mercado Éxito", 88_600L, espacioId = "esp_casa", parteAjena = 44_300L)

    private val navegaciones = mutableListOf<Screen>()
    private val anotados = mutableListOf<FinancialEvent>()
    private val confirmados = mutableListOf<String>()

    private fun montar(mensajes: List<SmsMessage>, esperar: String) {
        DiasPlegadosStore.clear()
        Repositories.sustitutoDePrueba = object : RepositorioDePruebaDeMovimientos() {
            override suspend fun getAccounts(): List<Account> = listOf(ahorros)
            // Con el perfil leído: sin él, el aviso «No pudimos leer el día…» ocupa el lugar del de abajo.
            override suspend fun getUserProfile(): UserProfile =
                UserProfile(id = "u", email = "u@local", name = "Juan", avatarColor = "#000000", periodCutoffDay = 1)
            override suspend fun getEventsByDay(): List<EventDay> =
                listOf(EventDay(date = hoy, total = 0L, items = listOf(almuerzo, mercado)))
            override suspend fun getCardPaymentCandidates(): List<FinancialEvent> = emptyList()
            override suspend fun getSmsMessages(): List<SmsMessage> =
                mensajes.map { if (it.id in confirmados) it.copy(state = SMS_STATE_CONFIRMED) else it }
            override suspend fun getPropuestasDeLaBandeja(): PropuestasDeLaBandeja = propuestas
            override suspend fun postEvent(event: FinancialEvent): FinancialEvent = event.also { anotados += it }
            override suspend fun confirmSmsCon(id: String, eventoId: String) { confirmados += id }
        }
        composeRule.setContent {
            MoviTheme { Box(Modifier.fillMaxSize()) { TransactionsScreen(onNavigate = { navegaciones += it }) } }
        }
        composeRule.waitUntil(timeoutMillis = 5_000) { hay(esperar) }
        composeRule.waitForIdle()
    }

    @After
    fun limpiar() {
        Repositories.sustitutoDePrueba = null
        DiasPlegadosStore.clear()
    }

    private fun hay(texto: String, substring: Boolean = true): Boolean =
        composeRule.onAllNodesWithText(texto, substring = substring, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    /** Toca el botón cuyo texto es [texto] (el botón es el padre del texto: el `Boton` del sistema). */
    private fun tocar(texto: String) {
        composeRule.onAllNodes(hasText(texto), useUnmergedTree = true).onFirst().onParent()
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
    }

    @Test
    fun `con avisos aparece la tarjeta y Confirmar confirma solo lo que Movi entendio`() {
        montar(listOf(cafe, pan, exito), esperar = "por revisar")

        assertTrue(hay("3 por revisar"))
        assertTrue(hay("3 avisos"), "de qué es el número")
        // Vuelta 80: una propuesta de un renglón, sin el renglón de la suma.
        assertTrue(!hay("Suman"))
        // Éxito tiene un «¿Ya lo anotaste?»: no entra en el lote.
        tocar("Confirmar 2")
        assertTrue(hay("¿Confirmar estos 2 avisos?"), "pregunta antes, con la lista corta")
        assertTrue(hay("Total \$55.000"))
        assertTrue(anotados.isEmpty(), "preguntar no anota nada")

        tocar("Confirmar")
        composeRule.waitUntil(timeoutMillis = 5_000) { confirmados.size == 2 || hay("no se pud") || hay("confirmado") }
        assertEquals(
            2, confirmados.size,
            "confirmados=$confirmados anotados=${anotados.map { it.id }} textos=" +
                composeRule.onAllNodesWithText("onfirm", substring = true, useUnmergedTree = true).fetchSemanticsNodes()
                    .map { nodo -> nodo.config.getOrElse(androidx.compose.ui.semantics.SemanticsProperties.Text) { emptyList() }.joinToString { it.text } },
        )
        assertEquals(setOf("cafe", "pan"), confirmados.toSet())
        assertEquals(setOf(46_000L, 9_000L), anotados.map { it.amount }.toSet())
        composeRule.waitUntil(timeoutMillis = 5_000) { hay("2 confirmados") }
        // Queda Éxito, para revisarlo uno por uno.
        composeRule.waitUntil(timeoutMillis = 5_000) { hay("1 por revisar") }
        assertTrue(!hay("Confirmar 1"), "lo que queda no se confirma de una vez")
    }

    @Test
    fun `Revisar uno por uno abre Por revisar`() {
        montar(listOf(cafe, exito), esperar = "por revisar")
        tocar(REVISAR_LOS_AVISOS)
        assertEquals(listOf<Screen>(Screen.PorRevisar), navegaciones)
    }

    @Test
    fun `sin avisos pendientes la tarjeta no esta`() {
        montar(listOf(cafe.copy(state = SMS_STATE_CONFIRMED)), esperar = "Señor Gol")
        assertTrue(composeRule.onAllNodesWithTag(TAG_TARJETA_DE_AVISOS, useUnmergedTree = true).fetchSemanticsNodes().isEmpty())
        assertTrue(!hay("por confirmar"))
    }

    @Test
    fun `el dia dice Gastaste con tu parte, y Compartidos deja solo lo repartido`() {
        montar(emptyList(), esperar = "Señor Gol")

        assertTrue(hay("Hoy · "), "«Hoy · martes 28»")
        assertTrue(hay("Gastaste", substring = false))
        // El almuerzo entero y tu parte del mercado: $46.489 + $44.300.
        assertTrue(hay("\$90.789", substring = false))

        composeRule.onNodeWithText("Compartidos", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()
        assertTrue(hay("Mercado Éxito"))
        assertTrue(!hay("Señor Gol"), "con «Compartidos», solo lo repartido")
    }
}
