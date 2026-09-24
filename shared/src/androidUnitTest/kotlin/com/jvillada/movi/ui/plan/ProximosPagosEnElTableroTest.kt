package com.jvillada.movi.ui.plan

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
import com.jvillada.movi.shared.model.PeriodSettings
import com.jvillada.movi.theme.MoviTheme
import com.jvillada.movi.ui.Screen
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * PR 3 del rediseño de Recurrentes (2026-09): «Próximos pagos» y el flujo de «¿esto ya ocurrió?»
 * montados de verdad en el tablero de Recurrentes ([TableroDeRecurrentes]; ola C: Plan · Pagos del
 * mes, antes el chip «Recurrentes» de Movimientos).
 *
 * Lo que prueba no es que un texto aparezca: es que **lo que sella un periodo llegue al
 * repositorio y que la pantalla refleje lo que quedó**. Sellar algo que no ocurrió apaga el aviso
 * de una deuda real, y eso cuesta plata; por eso también se prueba el camino de vuelta
 * («Deshacer»), que en la pantalla vieja vivía en un inventario que no se mudó.
 *
 * **Y desde esta ola, que la tercera salida ya no selle nada.** Era «Ya lo pagué» / «Ya me llegó»
 * y cerraba el periodo con `eventId = null`, o sea sin ninguna evidencia. El dueño pidió cerrar esa
 * puerta en el checklist, y dejarla viva acá la habría movido un toque más allá en vez de
 * cerrarla: ahora lo que se ofrece es **anotar el movimiento que falta**.
 *
 * Mismo patrón de montaje que [ResumenRecurrentesEnElTableroTest].
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h731dp-xhdpi")
class ProximosPagosEnElTableroTest {

    @get:Rule val composeRule = createComposeRule()

    private val bancolombia = Account("acc-banco", "Bancolombia", AccountType.SAVINGS, 1_000_000L, "COP")
    private val arriendo = RecurringRule(
        id = "rr_arriendo", name = "Arriendo", category = "Vivienda",
        amount = 1_800_000L, dayOfMonth = 5, type = TransactionType.EXPENSE,
    )

    /** El estado del periodo en juego. Mutable: sellarlo y deshacerlo lo reescriben. */
    private var estadoDelArriendo = OccurrenceState(
        ruleId = "rr_arriendo", period = "2026-09", dueDate = "2026-09-05",
        occurred = false, candidates = emptyList(),
    )

    private var marcadas = 0
    private var desmarcadas = 0
    private var rechazados = 0

    private inner class Repo : RepositorioDePrueba() {
        override suspend fun getAccounts(): List<Account> = listOf(bancolombia)
        override suspend fun getEventsByDay(): List<EventDay> = emptyList()
        override suspend fun getCardPaymentCandidates(): List<FinancialEvent> = emptyList()
        override suspend fun getRecurringRules(): List<RecurringRule> = listOf(arriendo)
        override suspend fun getSubscriptions(): SubscriptionsResult =
            SubscriptionsResult(emptyList(), monthlyTotalCop = 0L)

        override suspend fun getUpcomingPayments(): List<UpcomingPayment> = listOf(
            UpcomingPayment(
                rule = arriendo, dueDate = "2026-09-05",
                // Vencido: `proximosQueUrgen` deja fuera lo que todavía es UPCOMING.
                daysUntil = -2, status = PaymentStatus.OVERDUE,
            ),
        )

        override suspend fun getOccurrenceStates(): List<OccurrenceState> = listOf(estadoDelArriendo)

        override suspend fun markOccurrence(ruleId: String, period: String, eventId: String?): RecurringOccurrence {
            marcadas++
            estadoDelArriendo = estadoDelArriendo.copy(occurred = true, eventId = eventId, candidates = emptyList())
            return RecurringOccurrence(ruleId = ruleId, period = period, eventId = eventId)
        }

        override suspend fun unmarkOccurrence(ruleId: String, period: String) {
            desmarcadas++
            estadoDelArriendo = estadoDelArriendo.copy(occurred = false, eventId = null)
        }

        override suspend fun rechazarOcurrencia(ruleId: String, eventId: String) {
            rechazados++
        }
    }

    private fun montar() {
        composeRule.setContent {
            MoviTheme {
                Box(Modifier.fillMaxSize()) {
                    TableroDeRecurrentes(ajustesDelPeriodo = PeriodSettings(), onNavigate = { navegoA = it })
                }
            }
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
        rechazados = 0
        estadoDelArriendo = OccurrenceState(
            ruleId = "rr_arriendo", period = "2026-09", dueDate = "2026-09-05",
            occurred = false, candidates = emptyList(),
        )
    }

    @After
    fun limpiar() {
        Repositories.sustitutoDePrueba = null
        RecurringOfferGate.clear()
    }

    private fun esperarTexto(texto: String) {
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText(texto, substring = true, useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun esperarQueDesaparezca(texto: String) {
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText(texto, substring = true, useUnmergedTree = true)
                .fetchSemanticsNodes().isEmpty()
        }
    }

    @Test
    fun `el vencimiento y su propuesta abierta aparecen en el tablero`() {
        montar()

        esperarTexto("Arriendo")
        // MinSectionHeader pinta el título en mayúsculas.
        composeRule.onNodeWithText("PRÓXIMOS", useUnmergedTree = true).assertIsDisplayed()
        // Con `substring`: el vencimiento va en el mismo texto que la categoría («Vencido hace 2 días
        // · Vivienda»), para que no se repartan un renglón y la categoría quede de una letra de ancho.
        composeRule.onNodeWithText("Vencido hace 2 días", substring = true, useUnmergedTree = true).assertIsDisplayed()
        // La propuesta, con el mes que nombra. Y sin ningún candidato, su única salida: anotar el
        // movimiento que falta. El «Ya lo pagué» que sellaba sin evidencia ya no existe.
        composeRule.onNodeWithText("¿Ya pagaste el de septiembre?", useUnmergedTree = true).assertIsDisplayed()
        assertEquals(
            true,
            composeRule.onAllNodesWithText("Anotar el movimiento", useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty(),
        )
        assertEquals(
            true,
            composeRule.onAllNodesWithText("Ya lo pagué", useUnmergedTree = true)
                .fetchSemanticsNodes().isEmpty(),
        )
    }

    /**
     * **Pagó en efectivo, o desde una cuenta que Movi no lleva, o el banco nunca avisó.** La salida
     * ya no es sellar el periodo a ciegas: es anotar el movimiento que falta, con los datos del
     * recurrente puestos. Nada se sella hasta que ese movimiento exista.
     */
    @Test
    fun `Anotar el movimiento abre la hoja prellenada y no sella nada`() {
        montar()
        esperarTexto("Anotar el movimiento")

        // `onLast()`: el checklist va arriba y ofrece el mismo rótulo sobre la misma regla; el de
        // abajo es el de «Próximos», que es el que esta prueba mira.
        composeRule.onAllNodesWithText("Anotar el movimiento", useUnmergedTree = true)
            .onLast().performClick()

        val hoja = assertIs<Screen.QuickAdd>(navegoA)
        assertEquals("Arriendo", hoja.presetNota)
        assertEquals(1_800_000L, hoja.presetMonto)
        assertEquals("Vivienda", hoja.presetCategoria)
        assertEquals("2026-09-05", hoja.presetFecha)
        assertEquals(0, marcadas, "ningún periodo se selló: todavía no hay movimiento que lo pruebe")
    }

    /**
     * «Deshacer» sobre un sello que ya existe. Se arranca con el periodo sellado en el fixture y no
     * sellándolo desde la pantalla: la única puerta que hacía eso sin movimiento se cerró, y
     * reconstruirla acá para poder probar el camino de vuelta sería probar algo que ya no existe.
     */
    @Test
    fun `Deshacer revierte el sello y la pregunta vuelve`() {
        estadoDelArriendo = estadoDelArriendo.copy(occurred = true, eventId = "ev_1")
        montar()
        esperarTexto("YA OCURRIERON")

        composeRule.onNodeWithText("Deshacer", useUnmergedTree = true).performClick()

        esperarQueDesaparezca("YA OCURRIERON")
        assertEquals(1, desmarcadas)
        esperarTexto("¿Ya pagaste el de septiembre?")
    }
}
