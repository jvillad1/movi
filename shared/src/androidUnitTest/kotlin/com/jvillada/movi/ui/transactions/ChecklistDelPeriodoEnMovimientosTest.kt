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
import androidx.compose.ui.test.onFirst
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
import com.jvillada.movi.shared.model.EventSource
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
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * El checklist del período, montado de verdad dentro de [TransactionsScreen] bajo el chip
 * «Recurrentes» — que es donde aterriza el «Ver todos» del Inicio.
 *
 * Lo que prueba no es que un texto aparezca: es que **la casilla dejó de ser un control**, y que lo
 * que la reemplazó hace lo que dice. El dueño lo pidió así —*«no me debería dejar hacer check sin
 * que el movimiento asociado exista, y esto debería ser read only»*— porque un tilde sin evidencia
 * apaga el aviso de una deuda que puede seguir viva, y después no queda nada en pantalla que
 * permita notarlo.
 *
 * Tres cosas se afirman acá y no se pueden afirmar sin montar la pantalla: que la fila **no acepta
 * un toque**, que «Anotar el movimiento» abre la hoja de Agregar con los datos del recurrente, y
 * que un sello viejo hecho a mano ofrece quitarse.
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

    private val movimientoDelArriendo = FinancialEvent(
        id = "ev_arriendo",
        accountId = "acc-banco",
        type = TransactionType.EXPENSE,
        amount = 1_800_000L,
        category = "Vivienda",
        description = "Arriendo",
        source = EventSource.MANUAL,
        timestamp = Clock.System.now().toEpochMilliseconds(),
    )

    private var estadoDelArriendo = OccurrenceState(
        ruleId = "rr_arriendo", period = periodoDelSello, dueDate = vence.toString(),
        occurred = false, candidates = emptyList(),
    )

    private var marcadas = 0
    private var desmarcadas = 0
    private val rechazados = mutableListOf<Pair<String, String>>()
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

        override suspend fun rechazarOcurrencia(ruleId: String, eventId: String) {
            rechazados += ruleId to eventId
            estadoDelArriendo = estadoDelArriendo.copy(
                occurred = false, eventId = null, automatica = false,
                derivadaDeUnMovimiento = false, candidates = emptyList(),
            )
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
        rechazados.clear()
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
     * Tocar el primer botón que diga [texto].
     *
     * `performSemanticsAction` y no `performClick`: la tarjeta queda más abajo de lo que mide la
     * pantalla de prueba y un click por coordenadas no llega. `onFirst()` porque el checklist va
     * ARRIBA de «Próximos», y las dos secciones ofrecen los mismos rótulos sobre la misma regla.
     */
    private fun tocar(texto: String) {
        composeRule.onAllNodes(
            hasClickAction() and hasAnyDescendant(hasText(texto)),
            useUnmergedTree = true,
        ).onFirst().performSemanticsAction(SemanticsActions.OnClick)
    }

    /** Cuántos nodos clickeables contienen ese renglón. Cero = la fila es de solo lectura. */
    private fun clickeablesCon(renglon: String): Int =
        composeRule.onAllNodes(
            hasClickAction() and hasAnyDescendant(hasText(renglon)),
            useUnmergedTree = true,
        ).fetchSemanticsNodes().size

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
     * **La fila no acepta un toque, y no hay ninguna forma de sellar el período sin movimiento.**
     *
     * Es la regresión de la ola entera. Antes la fila era clickeable y tildarla mandaba
     * `markOccurrence(ruleId, period, null)` — un sello con la nada adentro. Ahora no hay nodo
     * clickeable que contenga ese renglón, y nada se manda.
     */
    @Test
    fun `la fila del checklist es de solo lectura`() {
        montar()
        esperarTexto(subtitulo)

        assertEquals(0, clickeablesCon(subtitulo), "la fila dejó de ser un control")
        assertEquals(0, marcadas, "y nadie selló nada")
    }

    /**
     * **Sin un movimiento que emparejar, la salida es anotarlo** — con los datos del recurrente ya
     * puestos. No es comodidad: la categoría y la cuenta son justo lo que el server compara para
     * reconocer después ese movimiento y tildar la fila solo.
     */
    @Test
    fun `sin movimiento la fila ofrece anotarlo y abre la hoja prellenada`() {
        montar()
        esperarTexto(subtitulo)
        esperarTexto("Sin movimiento")

        tocar("Anotar el movimiento")

        val hoja = assertIs<Screen.QuickAdd>(navegoA)
        assertEquals("Arriendo", hoja.presetNota)
        assertEquals(1_800_000L, hoja.presetMonto)
        assertEquals("Vivienda", hoja.presetCategoria)
        assertEquals(vence.toString(), hoja.presetFecha, "la fecha del vencimiento, no hoy")
        assertEquals(0, marcadas, "anotar no sella nada: lo sella el movimiento cuando exista")
    }

    /**
     * **Con un candidato, la fila pregunta** — y «Sí, fue este» sella el período ANCLADO a ese
     * movimiento, nunca con `null`.
     */
    @Test
    fun `con un candidato la fila pregunta y se confirma con el movimiento`() {
        estadoDelArriendo = estadoDelArriendo.copy(candidates = listOf(movimientoDelArriendo))
        montar()
        esperarTexto("¿Ya pagaste")

        tocar("Sí, fue este")

        composeRule.waitUntil(timeoutMillis = 5_000) { marcadas == 1 }
        assertEquals(periodoDelSello, ultimoPeriodoMarcado, "el período del vencimiento, no el de hoy")
        assertEquals("ev_arriendo", ultimoEventoMarcado, "sellado contra el movimiento, no contra la nada")
    }

    /**
     * **«No fue este» se guarda.** Mientras el rechazo vivía solo en la pantalla, el
     * emparejamiento automático volvía a dar por ocurrido lo mismo en la siguiente lectura.
     */
    @Test
    fun `lo que Movi emparejo solo se puede rechazar y el no se persiste`() {
        estadoDelArriendo = estadoDelArriendo.copy(
            occurred = true, eventId = "ev_arriendo", automatica = true,
            derivadaDeUnMovimiento = true, montoDelPago = 1_800_000L, monedaDelPago = "COP",
        )
        montar()
        esperarTexto("Movi lo emparejó")

        tocar("No fue este")

        composeRule.waitUntil(timeoutMillis = 5_000) { rechazados.isNotEmpty() }
        assertEquals(listOf("rr_arriendo" to "ev_arriendo"), rechazados)
        assertEquals(0, desmarcadas, "lo automático no escribe ningún sello, así que no hay qué borrar")
    }

    /**
     * **Un sello viejo hecho a mano dice lo que es y se puede quitar.** No se pueden crear más,
     * pero en la base del dueño hay varios: dejarlos sin salida sería dejar tildado para siempre
     * algo que no tiene nada detrás.
     */
    @Test
    fun `un sello sin movimiento se puede quitar`() {
        estadoDelArriendo = estadoDelArriendo.copy(occurred = true, eventId = null)
        montar()
        esperarTexto("marcado a mano, sin movimiento")

        tocar("Quitar la marca")

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
        // Y «Próximos» ofrece lo mismo que el checklist, no un segundo mecanismo de sellado: el
        // «Ya lo pagué» que sellaba sin movimiento se fue de las dos a la vez.
        assertTrue(
            composeRule.onAllNodesWithText("Anotar el movimiento", useUnmergedTree = true)
                .fetchSemanticsNodes().size >= 2,
        )
        assertTrue(
            composeRule.onAllNodesWithText("Ya lo pagué", useUnmergedTree = true)
                .fetchSemanticsNodes().isEmpty(),
        )
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
