package com.jvillada.movi.ui.transactions

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.jvillada.movi.data.CacheDeLecturas
import com.jvillada.movi.data.ClaveDeLectura
import com.jvillada.movi.data.InvalidaElInicioAlEscribir
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.RepositorioDePrueba
import com.jvillada.movi.data.SessionManager
import com.jvillada.movi.shared.model.EventDay
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.ReconciliationStatus
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.UserProfile
import com.jvillada.movi.shared.model.VoidEvent
import com.jvillada.movi.shared.time.epochMillisToAppDate
import com.jvillada.movi.theme.MoviTheme
import com.jvillada.movi.ui.components.TAG_FILA_DE_LISTA_ESQUELETO
import com.jvillada.movi.ui.components.TEXTO_ACTUALIZANDO
import com.jvillada.movi.ui.components.TEXTO_NO_PUDIMOS_ACTUALIZAR
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * # Volver a Movimientos no vuelve a cargar todo
 *
 * Solo la pantalla actual está compuesta, así que ir a Hoy y volver tiraba la historia leída y
 * Movimientos arrancaba con su esqueleto cada vez. Con [CacheDeLecturas] la segunda visita pinta
 * la lista al primer cuadro, con «Actualizando…» en la cabecera hasta que la lectura nueva
 * contesta. Y lo que no puede pasar: que eso se muestre cuando es viejo (una escritura en el
 * medio), ajeno (otro usuario) o se haga pasar por actual cuando la lectura falla.
 *
 * Las visitas se simulan sacando y volviendo a poner la pantalla en la composición, que es lo que
 * hace `App.kt` al navegar.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w390dp-h2400dp-xhdpi")
class MovimientosRecuerdaLoUltimoTest {

    @get:Rule val composeRule = createComposeRule()

    private val ahora = Clock.System.now().toEpochMilliseconds()
    private val almuerzo = FinancialEvent(
        id = "e1", accountId = "a1", type = TransactionType.EXPENSE, amount = 25_000L,
        category = "Comida", description = "Almuerzo", timestamp = ahora,
        reconciliationStatus = ReconciliationStatus.RECONCILED,
    )
    private val dias = listOf(EventDay(date = epochMillisToAppDate(ahora).toString(), total = 25_000L, items = listOf(almuerzo)))
    private val perfil = UserProfile(id = "u1", email = "juan@ejemplo.com", name = "Juan", avatarColor = "morado")

    /** Cómo contesta `getEventsByDay` en cada visita; por defecto, enseguida y con [dias]. */
    private var eventos: suspend () -> List<EventDay> = { dias }
    private var pedidosDeEventos = 0

    private val repositorio = object : RepositorioDePrueba() {
        override suspend fun getUserProfile(): UserProfile = perfil
        override suspend fun getEventsByDay(): List<EventDay> { pedidosDeEventos++; return eventos() }
        override suspend fun getAccounts(): List<com.jvillada.movi.shared.model.Account> = emptyList()
        override suspend fun voidEvent(id: String, reason: String?): VoidEvent =
            VoidEvent(id = "v1", originalEventId = id, reason = reason, timestamp = ahora)
    }

    private var enPantalla by mutableStateOf(true)

    @Before
    fun entrar() {
        SessionManager.save(token = "tok", userId = "u1", name = "Juan", email = "juan@ejemplo.com")
        // Envuelto como en la app, para que una escritura vacíe lo recordado de verdad.
        Repositories.sustitutoDePrueba = InvalidaElInicioAlEscribir(repositorio)
    }

    @After
    fun salir() {
        Repositories.sustitutoDePrueba = null
    }

    private fun montar() = composeRule.setContent {
        MoviTheme { Box(Modifier.fillMaxSize()) { if (enPantalla) TransactionsScreen(onNavigate = {}) } }
    }

    private fun cuantas(texto: String): Int =
        composeRule.onAllNodesWithText(texto, useUnmergedTree = true).fetchSemanticsNodes().size

    private fun filasEsqueleto(): Int =
        composeRule.onAllNodesWithTag(TAG_FILA_DE_LISTA_ESQUELETO).fetchSemanticsNodes().size

    /** Primera visita completa, salir, y dejar la próxima lectura de eventos como diga [siguiente]. */
    private fun primeraVisitaYSalir(siguiente: suspend () -> List<EventDay>) {
        montar()
        composeRule.waitForIdle()
        assertEquals(1, cuantas("Almuerzo"), "la primera visita no llegó a pintar la lista")
        enPantalla = false
        composeRule.waitForIdle()
        eventos = siguiente
    }

    @Test
    fun `la segunda visita pinta la lista con Actualizando y sin esqueleto, y al contestar lo quita`() {
        val puerta = CompletableDeferred<List<EventDay>>()
        primeraVisitaYSalir { puerta.await() }

        enPantalla = true
        composeRule.waitForIdle()

        assertEquals(1, cuantas("Almuerzo"), "lo último que se vio está a la vista mientras se lee")
        assertEquals(1, cuantas(TEXTO_ACTUALIZANDO))
        assertEquals(0, filasEsqueleto())
        assertEquals(2, pedidosDeEventos, "la segunda visita igual vuelve a leer")

        puerta.complete(dias)
        composeRule.waitForIdle()

        assertEquals(1, cuantas("Almuerzo"))
        assertEquals(0, cuantas(TEXTO_ACTUALIZANDO))
    }

    /**
     * Lo mismo, mirado en el PRIMER cuadro: `autoAdvance = false` congela el reloj antes de que
     * corra ningún efecto, así que lo que se ve sale solo de lo recordado.
     */
    @Test
    fun `con lo ultimo recordado, el primer cuadro ya es la lista y dice Actualizando`() {
        CacheDeLecturas.guardar(ClaveDeLectura.Perfil, perfil, "u1", ahora)
        CacheDeLecturas.guardar(ClaveDeLectura.EventosPorDia, dias, "u1", ahora)
        val puerta = CompletableDeferred<List<EventDay>>()
        eventos = { puerta.await() }
        composeRule.mainClock.autoAdvance = false

        montar()

        assertEquals(1, cuantas("Almuerzo"))
        assertEquals(1, cuantas(TEXTO_ACTUALIZANDO))
        assertEquals(0, filasEsqueleto())
        composeRule.mainClock.autoAdvance = true
    }

    @Test
    fun `si la lectura falla con la lista a la vista, se dice y la lista se queda`() {
        primeraVisitaYSalir { error("sin red") }

        enPantalla = true
        composeRule.waitForIdle()

        assertEquals(1, cuantas("Almuerzo"), "la lista no se borra porque la red falló")
        assertEquals(1, cuantas(TEXTO_NO_PUDIMOS_ACTUALIZAR))
        assertEquals(0, cuantas(TEXTO_ACTUALIZANDO))
        assertEquals(0, cuantas("No pudimos cargar tus movimientos"), "hay lista: no es el error de no tener nada")

        eventos = { dias }
        composeRule.onNodeWithText("Reintentar", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()

        assertEquals(0, cuantas(TEXTO_NO_PUDIMOS_ACTUALIZAR), "contestó: ya es lo actual")
        assertEquals(1, cuantas("Almuerzo"))
    }

    @Test
    fun `una escritura entre visitas deja la segunda con su esqueleto`() {
        val puerta = CompletableDeferred<List<EventDay>>()
        primeraVisitaYSalir { puerta.await() }
        runBlocking { Repositories.wallets.voidEvent("e1", null) }

        enPantalla = true
        composeRule.waitForIdle()

        assertEquals(0, cuantas("Almuerzo"), "lo anulado no se vuelve a pintar como lo último")
        assertTrue(filasEsqueleto() > 0)
        assertEquals(0, cuantas(TEXTO_ACTUALIZANDO))
        puerta.complete(emptyList())
    }

    @Test
    fun `otra persona en el mismo aparato ve su esqueleto, no la lista de la anterior`() {
        val puerta = CompletableDeferred<List<EventDay>>()
        primeraVisitaYSalir { puerta.await() }
        // Sin pasar por `clear()`, que ya vaciaría todo: acá se prueba la guarda por usuario sola.
        SessionManager.save(token = "tok2", userId = "u2", name = "Otra", email = "otra@ejemplo.com")

        enPantalla = true
        composeRule.waitForIdle()

        assertEquals(0, cuantas("Almuerzo"))
        assertTrue(filasEsqueleto() > 0)
        puerta.complete(emptyList())
    }
}
