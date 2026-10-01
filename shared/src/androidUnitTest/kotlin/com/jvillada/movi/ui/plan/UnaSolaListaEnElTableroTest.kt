package com.jvillada.movi.ui.plan

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import com.jvillada.movi.data.RecurringOfferGate
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.RepositorioDePrueba
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.EventDay
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.OccurrenceState
import com.jvillada.movi.shared.model.RecurringOccurrence
import com.jvillada.movi.shared.model.RecurringRule
import com.jvillada.movi.shared.model.SubscriptionsResult
import com.jvillada.movi.shared.model.UpcomingPayment
import com.jvillada.movi.theme.MoviTheme
import com.jvillada.movi.ui.Screen
import com.jvillada.movi.ui.recurrentes.CasoDeOctubreDelDueno as Caso
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
 * **Plan · Pagos del mes, con el caso del dueño, montado de verdad** (ver `CasoDeOctubreDelDueno`).
 *
 * El pedido (27-sep): *«que sea muy claro qué me falta por pagar y qué ya pagué por cada período,
 * con eso eliminamos la ambigüedad de ya ocurrieron o tener varias listas contando información
 * similar»*. Lo que se afirma acá:
 *
 * - En el período de octubre, Celular y Cotrafa (pagados en septiembre) salen **una sola vez**, en
 *   «Falta por pagar», y en ningún lado dice que ya se pagaron ni «ya ocurrió».
 * - Crediágil (pagado el 27-sep) sale una sola vez, en «Ya pagaste», con el día y el pago.
 * - «Próximos», «Sin confirmar» y «Ya ocurrieron» ya no existen.
 * - Las acciones que esas secciones ofrecían siguen en la fila: «Anotar este pago», «No fue este»,
 *   «Quitar la marca», «Sí, fue este» (de un período anterior) y abrir la regla (una cuota lleva a
 *   Créditos).
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h731dp-xhdpi")
class UnaSolaListaEnElTableroTest {

    @get:Rule val composeRule = createComposeRule()

    private val bancolombia = Account("acc-banco", "Bancolombia", AccountType.SAVINGS, 1_000_000L, "COP")

    private var upcoming: List<UpcomingPayment> = Caso.upcoming
    private var ocurrencias: List<OccurrenceState> = Caso.ocurrencias
    private val marcadas = mutableListOf<Triple<String, String, String?>>()
    private val desmarcadas = mutableListOf<Pair<String, String>>()
    private val rechazados = mutableListOf<Pair<String, String>>()
    private var navegoA: Screen? = null

    private inner class Repo : RepositorioDePrueba() {
        override suspend fun getAccounts(): List<Account> = listOf(bancolombia)
        override suspend fun getEventsByDay(): List<EventDay> = emptyList()
        override suspend fun getCardPaymentCandidates(): List<FinancialEvent> = emptyList()
        override suspend fun getRecurringRules(): List<RecurringRule> = upcoming.map { it.rule }
        override suspend fun getSubscriptions(): SubscriptionsResult = SubscriptionsResult(emptyList(), monthlyTotalCop = 0L)
        override suspend fun getUpcomingPayments(): List<UpcomingPayment> = upcoming
        override suspend fun getOccurrenceStates(): List<OccurrenceState> = ocurrencias

        override suspend fun markOccurrence(ruleId: String, period: String, eventId: String?): RecurringOccurrence {
            marcadas += Triple(ruleId, period, eventId)
            return RecurringOccurrence(ruleId = ruleId, period = period, eventId = eventId)
        }

        override suspend fun unmarkOccurrence(ruleId: String, period: String) {
            desmarcadas += ruleId to period
        }

        override suspend fun rechazarOcurrencia(ruleId: String, eventId: String) {
            rechazados += ruleId to eventId
        }
    }

    @Before
    fun preparar() {
        RecurringOfferGate.clear()
        Repositories.sustitutoDePrueba = Repo()
        upcoming = Caso.upcoming
        ocurrencias = Caso.ocurrencias
        marcadas.clear()
        desmarcadas.clear()
        rechazados.clear()
        navegoA = null
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
                    TableroDeRecurrentesDePrueba(
                        ajustesDelPeriodo = Caso.ajustes,
                        periodoFijo = Caso.octubre,
                        onNavigate = { navegoA = it },
                    )
                }
            }
        }
        esperarTexto("PAGOS DEL PERÍODO")
        esperarTexto("Falta por pagar · ")
    }

    private fun nodos(texto: String, substring: Boolean = false) =
        composeRule.onAllNodesWithText(texto, substring = substring, useUnmergedTree = true).fetchSemanticsNodes()

    private fun esperarTexto(texto: String) {
        composeRule.waitUntil(timeoutMillis = 5_000) { nodos(texto, substring = true).isNotEmpty() }
    }

    /** Dónde empieza (en y) el texto exacto [texto]: para saber en qué grupo quedó una fila. */
    private fun arriba(texto: String): Float =
        composeRule.onNodeWithText(texto, useUnmergedTree = true).fetchSemanticsNode().positionInRoot.y

    /**
     * Toca el botón [etiqueta] de la fila de [nombre]. Por la acción semántica: la lista es más
     * larga que la pantalla de prueba y un toque por coordenadas no llega.
     */
    private fun tocarEnLaFila(etiqueta: String) {
        composeRule.onAllNodes(hasClickAction() and hasAnyDescendant(hasText(etiqueta)), useUnmergedTree = true)
            .onFirst().performSemanticsAction(SemanticsActions.OnClick)
    }

    @Test
    fun `celular y cotrafa salen una vez, en falta por pagar, y crediagil una vez en ya pagaste`() {
        montar()

        for (nombre in listOf("Celular", "Cotrafa", "Crediágil", "Gimnasio Caro", "Mercado", "Internet")) {
            assertEquals(1, nodos(nombre).size, "«$nombre» tiene que salir exactamente una vez")
        }

        val yaPagaste = arriba("Ya pagaste · 3")
        assertTrue(arriba("Falta por pagar · 3") < arriba("Celular"))
        assertTrue(arriba("Celular") < yaPagaste, "Celular está en «Falta por pagar»")
        assertTrue(arriba("Cotrafa") < yaPagaste, "Cotrafa está en «Falta por pagar»")
        assertTrue(arriba("Crediágil") > yaPagaste, "Crediágil está en «Ya pagaste»")

        // Las dos de octubre dicen cuándo vencen, no que ya se pagaron.
        assertEquals(2, nodos("vence el 22 de octubre").size)
        // Crediágil: con qué se sabe.
        assertEquals(1, nodos("pagado el 27 de septiembre").size)
        assertEquals(1, nodos("Lo prueba un pago de \$1.204.064").size)
    }

    @Test
    fun `ningun texto dice ya ocurrio y las otras listas no existen`() {
        montar()

        assertTrue(nodos("ocurri", substring = true).isEmpty(), "ni «Ya ocurrió» ni «Ya ocurrieron»")
        for (seccion in listOf("PRÓXIMOS", "SIN CONFIRMAR", "YA OCURRIERON")) {
            assertTrue(nodos(seccion, substring = true).isEmpty(), "«$seccion» se fue de Plan")
        }
        assertTrue(nodos("Del período de", substring = true).isEmpty(), "no hay nada abierto de antes")
    }

    @Test
    fun `anotar este pago abre la hoja prellenada y no sella nada`() {
        montar()

        tocarEnLaFila("Anotar este pago")

        val hoja = assertIs<Screen.QuickAdd>(navegoA)
        assertEquals("Gimnasio Caro", hoja.presetNota)
        assertEquals(180_000L, hoja.presetMonto)
        assertEquals(null, hoja.presetFecha, "la fecha es hoy, no el vencimiento")
        assertTrue(marcadas.isEmpty())
    }

    @Test
    fun `no fue este sobre lo que emparejo Movi guarda el rechazo`() {
        montar()

        tocarEnLaFila("No fue este")

        composeRule.waitUntil(timeoutMillis = 5_000) { rechazados.isNotEmpty() }
        assertEquals(listOf("rr_mercado" to "ev_mercado"), rechazados)
        assertTrue(desmarcadas.isEmpty(), "lo automático no tiene sello que borrar")
    }

    @Test
    fun `quitar la marca borra el sello viejo hecho a mano`() {
        montar()

        tocarEnLaFila("Quitar la marca")

        composeRule.waitUntil(timeoutMillis = 5_000) { desmarcadas.isNotEmpty() }
        assertEquals(listOf("rr_internet" to "2026-09"), desmarcadas)
    }

    /** El toque de la fila (el de «Próximos») sigue: una cuota se gestiona en Créditos. */
    @Test
    fun `tocar una cuota lleva a creditos`() {
        montar()

        tocarEnLaFila("pagado el 27 de septiembre")

        assertEquals(Screen.Credits, navegoA)
        assertTrue(marcadas.isEmpty(), "abrir no sella nada")
    }

    /**
     * **Lo que era «Sin confirmar»**: la ocurrencia de septiembre de Coomeva quedó abierta. Sale
     * aparte, al final, diciendo de qué período es, con su pregunta y su «Sí, fue este», que sella
     * SEPTIEMBRE anclado al movimiento.
     */
    @Test
    fun `lo abierto de septiembre va aparte, al final, y se confirma con su movimiento`() {
        upcoming = Caso.upcoming + Caso.coomevaDeOctubre
        ocurrencias = Caso.ocurrencias + Caso.coomevaDeSeptiembre
        montar()
        esperarTexto("Del período de septiembre")

        assertTrue(arriba("Del período de septiembre · 1") > arriba("Ya pagaste · 3"), "va al final, después de lo pagado")
        assertEquals(1, nodos("¿Ya pagaste el de septiembre?").size)

        tocarEnLaFila("Sí, fue este")

        composeRule.waitUntil(timeoutMillis = 5_000) { marcadas.isNotEmpty() }
        assertEquals(listOf<Triple<String, String, String?>>(Triple("rr_coomeva", "2026-09", "ev_coomeva")), marcadas.toList())
    }
}
