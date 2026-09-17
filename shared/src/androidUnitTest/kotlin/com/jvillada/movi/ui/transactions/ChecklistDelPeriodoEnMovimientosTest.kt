package com.jvillada.movi.ui.transactions

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import com.jvillada.movi.data.RecurringOfferGate
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.RepositorioDePrueba
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.EventDay
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.OccurrenceState
import com.jvillada.movi.shared.model.PaymentStatus
import com.jvillada.movi.shared.model.RecurringOccurrence
import com.jvillada.movi.shared.model.RecurringRule
import com.jvillada.movi.shared.model.SubscriptionsResult
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.UpcomingPayment
import com.jvillada.movi.theme.MoviTheme
import com.jvillada.movi.ui.Screen
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

/**
 * El checklist del período, montado de verdad dentro de [TransactionsScreen] bajo el chip
 * «Recurrentes» — que es donde aterriza el «Ver todos» del Inicio.
 *
 * Lo que prueba no es que un texto aparezca: es que **tildar una fila selle el período y destildarla
 * lo borre**, por el mismo endpoint que el «Ya lo pagué» de siempre. Una casilla que se pinta
 * marcada sin que el sello haya salido es peor que no tener casilla: apaga el aviso de una deuda
 * real en la pantalla y en ningún lado más.
 *
 * **Las fechas se calculan desde hoy, no se escriben.** El checklist filtra por el período EN CURSO
 * —lo decide el reloj de la máquina, no el fixture— así que un `"2026-09-05"` fijo haría pasar esta
 * prueba en septiembre y la dejaría vacía en octubre. Mismo motivo por el que el vencimiento se
 * ancla al día 1: con corte 1 (el perfil no se lee en las pruebas) ese día siempre cae en el mes
 * que se está viviendo.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h731dp-xhdpi")
class ChecklistDelPeriodoEnMovimientosTest {

    @get:Rule val composeRule = createComposeRule()

    private val hoy: LocalDate = Clock.System.todayIn(TimeZone.of("America/Bogota"))

    /** El primero de este mes, o hoy si hoy ES el primero. Siempre dentro del período en curso. */
    private val vence: LocalDate =
        if (hoy.dayOfMonth >= 2) LocalDate(hoy.year, hoy.monthNumber, 1) else hoy
    private val diasVencido = hoy.dayOfMonth - vence.dayOfMonth
    private val periodoDelSello = vence.toString().take(7)

    private val mesEnPalabras = listOf(
        "enero", "febrero", "marzo", "abril", "mayo", "junio",
        "julio", "agosto", "septiembre", "octubre", "noviembre", "diciembre",
    )[vence.monthNumber - 1]

    /** «1 de septiembre · venció hace 16 días» — el renglón que solo pinta el checklist. */
    private val subtitulo: String = buildString {
        append("${vence.dayOfMonth} de $mesEnPalabras · ")
        append(
            when {
                diasVencido == 0 -> "vence hoy"
                diasVencido == 1 -> "venció ayer"
                else -> "venció hace $diasVencido días"
            },
        )
    }

    private val bancolombia = Account("acc-banco", "Bancolombia", AccountType.SAVINGS, 1_000_000L, "COP")
    private val arriendo = RecurringRule(
        id = "rr_arriendo", name = "Arriendo", category = "Vivienda",
        amount = 1_800_000L, dayOfMonth = vence.dayOfMonth, type = TransactionType.EXPENSE,
    )

    private var estadoDelArriendo = OccurrenceState(
        ruleId = "rr_arriendo", period = periodoDelSello, dueDate = vence.toString(),
        occurred = false, candidates = emptyList(),
    )

    private var marcadas = 0
    private var desmarcadas = 0
    private var ultimoPeriodoMarcado: String? = null
    private var ultimoEventoMarcado: String? = "sin-tocar"

    private inner class Repo : RepositorioDePrueba() {
        override suspend fun getAccounts(): List<Account> = listOf(bancolombia)
        override suspend fun getEventsByDay(): List<EventDay> = emptyList()
        override suspend fun getCardPaymentCandidates(): List<FinancialEvent> = emptyList()
        override suspend fun getRecurringRules(): List<RecurringRule> = listOf(arriendo)
        override suspend fun getSubscriptions(): SubscriptionsResult =
            SubscriptionsResult(emptyList(), monthlyTotalCop = 0L)

        override suspend fun getUpcomingPayments(): List<UpcomingPayment> = listOf(
            UpcomingPayment(
                rule = arriendo,
                dueDate = vence.toString(),
                daysUntil = -diasVencido,
                status = if (diasVencido > 0) PaymentStatus.OVERDUE else PaymentStatus.DUE_TODAY,
            ),
        )

        override suspend fun getOccurrenceStates(): List<OccurrenceState> = listOf(estadoDelArriendo)

        override suspend fun markOccurrence(ruleId: String, period: String, eventId: String?): RecurringOccurrence {
            marcadas++
            ultimoPeriodoMarcado = period
            ultimoEventoMarcado = eventId
            estadoDelArriendo = estadoDelArriendo.copy(occurred = true, eventId = eventId, candidates = emptyList())
            return RecurringOccurrence(ruleId = ruleId, period = period, eventId = eventId)
        }

        override suspend fun unmarkOccurrence(ruleId: String, period: String) {
            desmarcadas++
            estadoDelArriendo = estadoDelArriendo.copy(occurred = false, eventId = null)
        }
    }

    private var navegoA: Screen? = null

    @Before
    fun preparar() {
        RecurringOfferGate.clear()
        Repositories.sustitutoDePrueba = Repo()
        navegoA = null
        marcadas = 0
        desmarcadas = 0
        ultimoPeriodoMarcado = null
        ultimoEventoMarcado = "sin-tocar"
        estadoDelArriendo = OccurrenceState(
            ruleId = "rr_arriendo", period = periodoDelSello, dueDate = vence.toString(),
            occurred = false, candidates = emptyList(),
        )
    }

    @After
    fun limpiar() {
        Repositories.sustitutoDePrueba = null
        RecurringOfferGate.clear()
    }

    private fun montar() {
        composeRule.setContent {
            MoviTheme {
                Box(Modifier.fillMaxSize()) {
                    TransactionsScreen(onNavigate = { navegoA = it }, chipInicial = CHIP_RECURRENTES)
                }
            }
        }
    }

    private fun esperarTexto(texto: String) {
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText(texto, substring = true, useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
    }

    /**
     * Tildar la fila del checklist.
     *
     * `performSemanticsAction` y no `performClick`: la tarjeta queda más abajo de lo que mide la
     * pantalla de prueba y un click por coordenadas no llega. `onLast()` toma el nodo clickeable
     * más interno de los que contienen ese renglón — la fila, no el contenedor que la envuelve.
     */
    private fun tildarLaFila(renglon: String = subtitulo) {
        composeRule.onAllNodes(
            hasClickAction() and hasAnyDescendant(hasText(renglon)),
            useUnmergedTree = true,
        ).onLast().performSemanticsAction(SemanticsActions.OnClick)
    }

    @Test
    fun `el checklist del periodo encabeza el chip Recurrentes, con su fecha y su monto`() {
        montar()

        esperarTexto("CHECKLIST DEL PERÍODO")
        composeRule.onNodeWithText("Falta por pagar", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText(subtitulo, useUnmergedTree = true).assertIsDisplayed()
        // El monto va entero, no abreviado: es la lista donde se compara lo que se debe. Hay más de
        // un «$1.800.000» en pantalla (el chip también lo pinta en «Próximos» y en el flujo libre),
        // así que se pide que exista, no que sea el único.
        assertEquals(
            true,
            composeRule.onAllNodesWithText("\$1.800.000", useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty(),
        )
    }

    /**
     * Tildar sella el período **sin movimiento que emparejar** — que es todo lo que una casilla
     * puede prometer — y la fila se muda al grupo de lo ya marcado.
     */
    @Test
    fun `tildar una fila sella el periodo por el mismo camino que Ya lo pague`() {
        montar()
        esperarTexto(subtitulo)

        tildarLaFila()

        esperarTexto("Ya marcados")
        assertEquals(1, marcadas)
        assertEquals(periodoDelSello, ultimoPeriodoMarcado, "el período del vencimiento, no el de hoy")
        assertEquals(null, ultimoEventoMarcado, "una casilla no emparejó ningún movimiento")
        composeRule.onNodeWithText("Marcaste el único pago de este período", useUnmergedTree = true)
            .assertIsDisplayed()
    }

    /**
     * Y destildarla lo deshace. La fila ya tildada dice otra cosa —«1 de septiembre · pagado»— y por
     * ahí se la vuelve a encontrar: si el renglón cambiara y este texto dejara de existir, la prueba
     * fallaría en vez de tocar cualquier otra cosa clickeable de la pantalla.
     */
    @Test
    fun `destildarla lo deshace`() {
        montar()
        esperarTexto(subtitulo)
        tildarLaFila()
        esperarTexto("Ya marcados")

        tildarLaFila("${vence.dayOfMonth} de $mesEnPalabras · pagado")

        composeRule.waitUntil(timeoutMillis = 5_000) { desmarcadas == 1 }
        assertEquals(1, desmarcadas)
    }

    /** El chip sigue siendo el de siempre: el checklist se suma arriba, no reemplaza nada. */
    @Test
    fun `las secciones de siempre siguen debajo del checklist`() {
        montar()
        esperarTexto("CHECKLIST DEL PERÍODO")

        composeRule.onNodeWithText("PRÓXIMOS", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("Flujo libre", useUnmergedTree = true).assertIsDisplayed()
        // Y la propuesta de emparejar un movimiento, que el checklist no reemplaza.
        composeRule.onAllNodesWithText("Ya lo pagué", useUnmergedTree = true)
            .fetchSemanticsNodes().isNotEmpty().let { assertEquals(true, it) }
    }

    /** Tocar el chip «Todo» apaga todo esto: es el resumen de un filtro, no una caja suelta. */
    @Test
    fun `con otro chip el checklist no se pinta`() {
        montar()
        esperarTexto("CHECKLIST DEL PERÍODO")

        composeRule.onNodeWithText("Todo", useUnmergedTree = true).performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("CHECKLIST DEL PERÍODO", useUnmergedTree = true)
                .fetchSemanticsNodes().isEmpty()
        }
    }
}
