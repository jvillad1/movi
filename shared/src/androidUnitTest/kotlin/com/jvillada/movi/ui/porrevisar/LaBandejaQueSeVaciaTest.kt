package com.jvillada.movi.ui.porrevisar

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.RepositorioDePrueba
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.CARD_PAYMENT_CATEGORY
import com.jvillada.movi.shared.model.EventDay
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.ParsedSms
import com.jvillada.movi.shared.model.PropuestaDelAviso
import com.jvillada.movi.shared.model.PropuestasDeLaBandeja
import com.jvillada.movi.shared.model.SMS_STATE_PENDING
import com.jvillada.movi.shared.model.SmsMessage
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.UserProfile
import com.jvillada.movi.theme.MoviTheme
import com.jvillada.movi.ui.sms.TAG_CONFIRMAR_EN_LA_FILA
import com.jvillada.movi.ui.sms.TAG_LINEA_DE_LO_ENTENDIDO
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * # La bandeja que se vacía sola, montada de verdad
 *
 * Cuatro avisos: dos que Movi entendió (el café y el mercado, con la categoría de su memoria y la
 * cuenta por el número) y dos con dudas («¿Ya lo anotaste?» y un pago de tarjeta). «Confirmar» sale
 * solo en los dos primeros, «Ignorar» en los cuatro, y el lote confirma solo los dos —y si uno falla,
 * el otro sigue y se dice cuál quedó—.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h2400dp-xhdpi")
class LaBandejaQueSeVaciaTest {

    @get:Rule val composeRule = createComposeRule()

    private fun aviso(id: String, texto: String, hora: String) =
        SmsMessage(id = id, time = "2026-10-10 $hora", bank = "85540", text = texto, state = SMS_STATE_PENDING, det = "")

    private val cafe = aviso("cafe", "Bancolombia: Compraste \$46.000,00 en TOSTAO CAFE con tu T.Deb *8133, el 10/10/2026 a las 08:10.", "08:10")
    private val mercado = aviso("mercado", "Bancolombia: Compraste \$180.000,00 en CARULLA con tu T.Deb *8133, el 10/10/2026 a las 09:00.", "09:00")
    private val exito = aviso("exito", "Bancolombia: Compraste \$80.000,00 en EXITO con tu T.Deb *8133, el 10/10/2026 a las 10:00.", "10:00")
    private val tarjeta = aviso("tarjeta", "Bancolombia: Pagaste \$386.902 en la tarjeta de credito *3684 desde la cuenta *8133 el 10/10/2026.", "11:00")

    private val ahorros = Account("acc-8133", "Bancolombia Ahorros 8133", AccountType.SAVINGS, 5_000_000L, "COP")
    private val yaAnotado = FinancialEvent(
        id = "ev-exito", accountId = "acc-8133", type = TransactionType.EXPENSE, amount = 80_000L,
        category = "Mercado", description = "Éxito", timestamp = 0L,
    )

    private val propuestas = PropuestasDeLaBandeja(
        propuestas = listOf(
            PropuestaDelAviso("cafe", leido = ParsedSms(46_000.0, "TOSTAO CAFE", TransactionType.EXPENSE, "Comida", aprendidoDe = "Así lo anotaste 4 veces")),
            PropuestaDelAviso("mercado", leido = ParsedSms(180_000.0, "CARULLA", TransactionType.EXPENSE, "Mercado", aprendidoDe = "Así lo anotaste la última vez")),
            PropuestaDelAviso("exito", leido = ParsedSms(80_000.0, "EXITO", TransactionType.EXPENSE, "Mercado", aprendidoDe = "x"), coincidencias = listOf(yaAnotado)),
            PropuestaDelAviso("tarjeta", leido = ParsedSms(386_902.0, "Pago de tarjeta", TransactionType.EXPENSE, CARD_PAYMENT_CATEGORY)),
        ),
        categoriasDelDueno = listOf("Comida", "Mercado"),
    )

    private val publicados = mutableListOf<FinancialEvent>()
    private val confirmados = mutableListOf<Pair<String, String>>()
    private val ignorados = mutableListOf<String>()
    private var fallaElMercado = false

    private inner class Repo : RepositorioDePrueba() {
        override suspend fun getSmsMessages(): List<SmsMessage> = listOf(tarjeta, exito, mercado, cafe)
        override suspend fun getEventsByDay(): List<EventDay> = emptyList()
        override suspend fun getCardPaymentCandidates(): List<FinancialEvent> = emptyList()
        override suspend fun getAccounts(): List<Account> = listOf(ahorros)
        override suspend fun getDestinos(): List<com.jvillada.movi.shared.model.DestinoConocido> = emptyList()
        override suspend fun getUserProfile(): UserProfile =
            UserProfile(id = "u1", email = "juan@movi.test", name = "Juan", avatarColor = "#4F7CFF")
        override suspend fun getPropuestasDeLaBandeja(): PropuestasDeLaBandeja = propuestas
        override suspend fun postEvent(event: FinancialEvent): FinancialEvent {
            if (fallaElMercado && event.amount == 180_000L) throw IllegalStateException("Sin conexión")
            publicados += event
            return event
        }
        override suspend fun confirmSmsCon(id: String, eventoId: String) {
            confirmados += id to eventoId
        }
        override suspend fun ignoreSms(id: String) {
            ignorados += id
        }
    }

    @After
    fun limpiar() {
        Repositories.sustitutoDePrueba = null
    }

    private fun cuantos(texto: String) =
        composeRule.onAllNodesWithText(texto, substring = true, useUnmergedTree = true).fetchSemanticsNodes().size

    private fun conTag(tag: String) = composeRule.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().size

    private fun esperar(condicion: () -> Boolean) {
        composeRule.waitUntil(timeoutMillis = 5_000) { condicion() }
        composeRule.waitForIdle()
    }

    private fun tocar(texto: String) {
        composeRule.onNodeWithText(texto).performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
    }

    private fun montar() {
        Repositories.sustitutoDePrueba = Repo()
        composeRule.setContent {
            MoviTheme { Box(Modifier.fillMaxSize()) { PorRevisarScreen(onNavigate = {}) } }
        }
        esperar { conTag(TAG_CONFIRMAR_EN_LA_FILA) > 0 }
    }

    @Test
    fun `Confirmar sale solo en lo que Movi entendio, e Ignorar en todas`() {
        montar()

        assertEquals(2, conTag(TAG_CONFIRMAR_EN_LA_FILA))
        assertEquals(2, conTag(TAG_LINEA_DE_LO_ENTENDIDO))
        assertEquals(1, cuantos("Se anota: Comida · Bancolombia Ahorros 8133 · \$46.000"))
        assertEquals(1, cuantos("Se anota: Mercado · Bancolombia Ahorros 8133 · \$180.000"))
        // «Ignorar» va en las cuatro tarjetas; «Revisar» también (en las dos con dudas es el principal).
        assertEquals(4, cuantos("Ignorar"))
        assertEquals(4, cuantos("Revisar"))
    }

    @Test
    fun `Confirmar en la fila anota por el camino de Reconciliar`() {
        montar()

        composeRule.onAllNodesWithTag(TAG_CONFIRMAR_EN_LA_FILA)[0].performSemanticsAction(SemanticsActions.OnClick)
        esperar { confirmados.isNotEmpty() }

        // La primera tarjeta es la del mercado (la más reciente de las dos entendidas).
        val evento = publicados.single()
        assertEquals(180_000L, evento.amount)
        assertEquals("acc-8133", evento.accountId)
        assertEquals("Mercado", evento.category)
        assertEquals(mercado.text, evento.rawPayload)
        assertEquals(listOf("mercado" to evento.id), confirmados)
        esperar { conTag(TAG_CONFIRMAR_EN_LA_FILA) == 1 }
    }

    @Test
    fun `Ignorar en la fila ignora el aviso`() {
        montar()
        composeRule.onAllNodesWithText("Ignorar")[3].performSemanticsAction(SemanticsActions.OnClick)
        esperar { ignorados.isNotEmpty() }
        assertEquals(listOf("cafe"), ignorados)
        assertTrue(publicados.isEmpty())
    }
}
