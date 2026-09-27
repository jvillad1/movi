package com.jvillada.movi.ui.porrevisar

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import com.jvillada.movi.data.InvalidaElInicioAlEscribir
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.RepositorioDePrueba
import com.jvillada.movi.data.SessionManager
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.EventDay
import com.jvillada.movi.shared.model.EventSource
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.ReconciliationStatus
import com.jvillada.movi.shared.model.SMS_STATE_PENDING
import com.jvillada.movi.shared.model.SmsMessage
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.UserProfile
import com.jvillada.movi.ui.VisitasDePrueba
import com.jvillada.movi.ui.components.TEXTO_ACTUALIZANDO
import com.jvillada.movi.ui.components.TEXTO_NO_PUDIMOS_ACTUALIZAR
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * # Volver a «Por revisar» no vuelve a cargar todo
 *
 * Mismo contrato que `MovimientosRecuerdaLoUltimoTest`, sobre las tres fuentes de la bandeja (la
 * historia es la misma entrada que lee Movimientos). Y «Todo al día» —un vacío— no se afirma con
 * una lectura de esta visita caída.
 */
@RunWith(RobolectricTestRunner::class)
class PorRevisarRecuerdaLoUltimoTest {

    @get:Rule val composeRule = createComposeRule()

    private val banco = Account("acc-banco", "Bancolombia Ahorros", AccountType.SAVINGS, 853_037L, "COP")
    private val porConfirmar = FinancialEvent(
        id = "e-solo", accountId = banco.id, type = TransactionType.EXPENSE, amount = 18_500L,
        category = "Comida", description = "Compra Exito", timestamp = 1_757_000_000_000L,
        source = EventSource.SMS, reconciliationStatus = ReconciliationStatus.UNCONFIRMED, countsAsCashFlow = true,
    )
    private val sms = SmsMessage(
        id = "s1", time = "2026-09-03 07:15", bank = "Bancolombia",
        text = "Compra aprobada \$28.500 en Uber BV.", state = SMS_STATE_PENDING, det = "Uber",
    )

    private var mensajes: suspend () -> List<SmsMessage> = { listOf(sms) }
    private var eventos: suspend () -> List<EventDay> = { listOf(EventDay(date = "2025-09-08", total = 0L, items = listOf(porConfirmar))) }

    private val repositorio = object : RepositorioDePrueba() {
        override suspend fun getSmsMessages(): List<SmsMessage> = mensajes()
        override suspend fun getEventsByDay(): List<EventDay> = eventos()
        override suspend fun getCardPaymentCandidates(): List<FinancialEvent> = emptyList()
        override suspend fun getAccounts(): List<Account> = listOf(banco)
        override suspend fun getUserProfile(): UserProfile =
            UserProfile(id = "u1", email = "juan@movi.test", name = "Juan", avatarColor = "#4F7CFF")
        override suspend fun deleteDestino(id: String) = Unit
    }

    private val visitas = VisitasDePrueba(composeRule) { PorRevisarScreen(onNavigate = {}) }

    @Before
    fun entrar() {
        SessionManager.save(token = "tok", userId = "u1", name = "Juan", email = "juan@ejemplo.com")
        Repositories.sustitutoDePrueba = InvalidaElInicioAlEscribir(repositorio)
    }

    @After
    fun salir() {
        Repositories.sustitutoDePrueba = null
    }

    private fun esqueleto() = visitas.cuantasConTag(TAG_ESQUELETO_DE_POR_REVISAR)

    @Test
    fun `la segunda visita pinta la bandeja en el primer cuadro con Actualizando, y al contestar lo quita`() {
        visitas.primeraYSalir()
        val puerta = CompletableDeferred<List<SmsMessage>>()
        mensajes = { puerta.await() }

        visitas.volverAlPrimerCuadro()

        assertEquals(1, visitas.cuantas("Compra Exito"))
        assertTrue(visitas.cuantas("Uber", substring = true) > 0)
        assertEquals(1, visitas.cuantas(TEXTO_ACTUALIZANDO))
        assertEquals(0, esqueleto())

        visitas.seguir()
        puerta.complete(listOf(sms))
        composeRule.waitForIdle()
        assertEquals(0, visitas.cuantas(TEXTO_ACTUALIZANDO))
    }

    @Test
    fun `si una lectura falla con la bandeja a la vista, se dice y se queda`() {
        visitas.primeraYSalir()
        eventos = { error("sin red") }

        visitas.volver()

        assertEquals(1, visitas.cuantas("Compra Exito"))
        assertEquals(1, visitas.cuantas(TEXTO_NO_PUDIMOS_ACTUALIZAR))
        assertEquals(0, visitas.cuantas("No pudimos cargar tus movimientos"))

        eventos = { listOf(EventDay(date = "2025-09-08", total = 0L, items = listOf(porConfirmar))) }
        composeRule.onAllNodesWithText("Reintentar", useUnmergedTree = true).onFirst().performClick()
        composeRule.waitForIdle()
        assertEquals(0, visitas.cuantas(TEXTO_NO_PUDIMOS_ACTUALIZAR))
    }

    @Test
    fun `con todo al dia recordado y una lectura caida, no se afirma Todo al dia`() {
        mensajes = { emptyList() }
        eventos = { emptyList() }
        visitas.primeraYSalir()
        mensajes = { error("sin red") }

        visitas.volver()

        assertEquals(1, visitas.cuantas(TEXTO_NO_PUDIMOS_ACTUALIZAR))
        assertEquals(0, visitas.cuantas(TODO_AL_DIA))
    }

    @Test
    fun `una escritura entre visitas deja la segunda con su esqueleto`() {
        visitas.primeraYSalir()
        val puerta = CompletableDeferred<List<SmsMessage>>()
        mensajes = { puerta.await() }
        runBlocking { Repositories.wallets.deleteDestino("d-otra") }

        visitas.volver()

        assertEquals(0, visitas.cuantas("Compra Exito"))
        assertTrue(esqueleto() > 0)
        puerta.complete(emptyList())
    }

    @Test
    fun `otra persona en el mismo aparato ve su esqueleto`() {
        visitas.primeraYSalir()
        val puerta = CompletableDeferred<List<SmsMessage>>()
        mensajes = { puerta.await() }
        SessionManager.save(token = "tok2", userId = "u2", name = "Otra", email = "otra@ejemplo.com")

        visitas.volver()

        assertEquals(0, visitas.cuantas("Compra Exito"))
        assertTrue(esqueleto() > 0)
        puerta.complete(emptyList())
    }
}
