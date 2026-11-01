package com.jvillada.movi.ui.dashboard

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.RepositorioDePrueba
import com.jvillada.movi.data.SessionManager
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.Anomalia
import com.jvillada.movi.shared.model.CambioDeCategoriaDeUnRecurrente
import com.jvillada.movi.shared.model.CardSummary
import com.jvillada.movi.shared.model.CreditSummary
import com.jvillada.movi.shared.model.DashboardSummary
import com.jvillada.movi.shared.model.FinanceSummary
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.Scope
import com.jvillada.movi.shared.model.ScreenDefinition
import com.jvillada.movi.shared.model.ScreenSection
import com.jvillada.movi.shared.model.TipoDeAnomalia
import com.jvillada.movi.shared.model.UpcomingPayment
import com.jvillada.movi.theme.MoviTheme
import com.jvillada.movi.ui.sdui.ParaRevisarSection
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
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
 * # Vuelta 89 · **«Está bien» se va en el mismo toque y no vuelve** (reporte del dueño, 31-oct)
 *
 * En su teléfono, «Está bien» en la única tarjeta de «Movi propone hoy» («La deuda de Hipoteca 1254
 * creció…») no hacía nada, aunque el server SÍ guardaba el descarte. Las pruebas de antes pasaban porque
 * su repositorio falso contestaba **sin suspender**; contra la red de verdad la respuesta tarda, y en ese
 * rato la tarjeta (y con ella su sección) ya había salido de la composición, llevándose el alcance de
 * corrutinas donde viajaba el pedido.
 *
 * Estas pruebas montan el Inicio entero (`DashboardScreen` → `SduiRenderer` con la definición del Inicio)
 * —y «Para revisar», la otra sección con el mismo descarte— con un repositorio que **tarda** como la red:
 * la tarjeta tiene que irse en el mismo toque, el pedido tiene que terminar (no cancelarse) y la tarjeta
 * no puede volver cuando el server contesta.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w390dp-h2400dp-xhdpi")
class EstaBienSeVaYNoVuelveTest {

    @get:Rule val composeRule = createComposeRule()

    private val deuda = Anomalia(
        huella = "deuda:acc_1254:2026-10",
        tipo = TipoDeAnomalia.DEUDA_QUE_CRECE,
        titulo = "La deuda de Hipoteca 1254 creció \$590.558 sin compras",
        detalle = "Al cierre del período pasó de \$763.274.195 a \$763.864.753, sin compras en el período.",
        evidencia = emptyList(),
    )

    private val cambio = CambioDeCategoriaDeUnRecurrente(
        eventoId = "ev_netflix",
        nombre = "Netflix",
        monto = 44_900,
        categoria = "Tecnología",
        categoriaAnterior = "Suscripciones",
        periodoAnterior = "2026-09",
        eventoAnteriorId = "ev_netflix_sep",
    )

    private val recurrente = Anomalia(
        huella = cambio.huella,
        tipo = TipoDeAnomalia.RECURRENTE_CAMBIO_DE_CATEGORIA,
        titulo = "Netflix cambió de categoría",
        detalle = "El período pasado iba en Suscripciones; este, en Tecnología.",
        evidencia = emptyList(),
        cambioDeCategoria = cambio,
    )

    /** La respuesta del server, que llega cuando la prueba la suelta. */
    private val respuesta = CompletableDeferred<Unit>()
    private val pedidos = mutableListOf<String>()
    private var cancelado = false
    private var termino = false

    /** Lo que hace la red: anota el pedido y espera a [respuesta]; si lo cortan, lo dice. */
    private suspend fun comoLaRed(pedido: String) {
        pedidos += pedido
        try {
            respuesta.await()
            termino = true
        } catch (e: CancellationException) {
            cancelado = true
            throw e
        }
    }

    private fun repositorio(anomalias: List<Anomalia>) = object : RepositorioDePrueba() {
        override suspend fun getScreen(slug: String, cachedVersion: Int?): ScreenDefinition? = null
        override suspend fun getFinanceSummary(scope: Scope) =
            FinanceSummary(scope = Scope.SELF, balance = 0, ingresos = 0, egresos = 0, eventCount = 12)
        override suspend fun getAccounts(): List<Account> = listOf(Account("a1", "Ahorros", AccountType.SAVINGS, 1_000_000))
        override suspend fun getCredits(): List<CreditSummary> = emptyList()
        override suspend fun getCards(): List<CardSummary> = emptyList()
        override suspend fun getUpcomingPayments(): List<UpcomingPayment> = emptyList()
        override suspend fun getDashboardSummary(scope: Scope) = DashboardSummary()
        override suspend fun getAnomalias(): List<Anomalia> = anomalias
        override suspend fun descartarAnomalia(huella: String) = comoLaRed("descartar:$huella")
        override suspend fun updateEventCategory(id: String, category: String): FinancialEvent {
            comoLaRed("categoria:$id:$category")
            return FinancialEvent(id = id, accountId = "a1", type = com.jvillada.movi.shared.model.TransactionType.EXPENSE, amount = 44_900, category = category, description = "Netflix", timestamp = 0L)
        }
    }

    private fun entrarAlInicio(anomalias: List<Anomalia>) {
        InstantaneaDelInicio.sustitutoDePrueba = instantaneaEnMemoria(mutableMapOf())
        SessionManager.save(token = "tok", userId = "u1", name = "Juan", email = "juan@ejemplo.com")
        Repositories.sustitutoDePrueba = repositorio(anomalias)
        composeRule.setContent { MoviTheme { DashboardScreen(onNavigate = {}) } }
        composeRule.waitForIdle()
    }

    @After
    fun salir() {
        if (!respuesta.isCompleted) respuesta.complete(Unit)
        Repositories.sustitutoDePrueba = null
        InstantaneaDelInicio.sustitutoDePrueba = null
    }

    private fun cuantas(titulo: String): Int =
        composeRule.onAllNodesWithText(titulo, useUnmergedTree = true).fetchSemanticsNodes().size

    /** El server contesta (tarde, como la red): el pedido no se cortó y lo descartado sigue fuera. */
    private fun elServerContestaYNoVuelve(titulo: String, huella: String) {
        respuesta.complete(Unit)
        composeRule.waitForIdle()
        assertFalse(cancelado, "que la tarjeta salga de la composición no puede cortar el pedido")
        assertTrue(termino, "el pedido llegó al final")
        assertTrue(huella in AnomaliasDescartadasEnLaSesion.huellas)
        assertEquals(0, cuantas(titulo), "no vuelve cuando el server contesta")
    }

    @Test
    fun `esta bien en la unica propuesta del Inicio la saca, el pedido termina y no vuelve`() {
        entrarAlInicio(listOf(deuda))
        assertEquals(1, cuantas(deuda.titulo), "la anomalía está en «Movi propone hoy»")

        composeRule.onNodeWithText("Está bien", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()
        assertEquals(0, cuantas(deuda.titulo), "se va en el mismo toque")
        assertEquals(listOf("descartar:${deuda.huella}"), pedidos)

        elServerContestaYNoVuelve(deuda.titulo, deuda.huella)
    }

    @Test
    fun `si cambiar en la unica propuesta del Inicio la saca, el cambio termina y no vuelve`() {
        entrarAlInicio(listOf(recurrente))
        assertEquals(1, cuantas(recurrente.titulo))

        composeRule.onNodeWithText(SI_CAMBIAR, useUnmergedTree = true).performClick()
        composeRule.waitForIdle()
        assertEquals(0, cuantas(recurrente.titulo), "se va en el mismo toque")
        assertEquals(listOf("categoria:ev_netflix:Suscripciones"), pedidos)

        elServerContestaYNoVuelve(recurrente.titulo, recurrente.huella)
    }

    @Test
    fun `esta bien en el ultimo aviso de Para revisar lo saca, el pedido termina y no vuelve`() {
        Repositories.sustitutoDePrueba = repositorio(emptyList())
        val data = DashboardData(anomalias = listOf(deuda))
        composeRule.setContent {
            MoviTheme { ParaRevisarSection(ScreenSection(type = "ALERTS", title = "Para revisar"), data) {} }
        }
        assertEquals(1, cuantas(deuda.titulo))

        composeRule.onNodeWithText("Está bien", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()
        assertEquals(0, cuantas(deuda.titulo), "se va en el mismo toque")
        assertEquals(listOf("descartar:${deuda.huella}"), pedidos)

        elServerContestaYNoVuelve(deuda.titulo, deuda.huella)
    }

    @Test
    fun `si el server de verdad rechaza el descarte, el aviso vuelve`() {
        Repositories.sustitutoDePrueba = object : RepositorioDePrueba() {
            override suspend fun descartarAnomalia(huella: String) {
                respuesta.await()
                error("sin red")
            }
        }
        val data = DashboardData(anomalias = listOf(deuda))
        composeRule.setContent {
            MoviTheme { ParaRevisarSection(ScreenSection(type = "ALERTS", title = "Para revisar"), data) {} }
        }
        composeRule.onNodeWithText("Está bien", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()
        assertEquals(0, cuantas(deuda.titulo))

        respuesta.complete(Unit)
        composeRule.waitForIdle()
        assertFalse(deuda.huella in AnomaliasDescartadasEnLaSesion.huellas)
        assertEquals(1, cuantas(deuda.titulo), "no se finge un descarte que no quedó")
    }
}
