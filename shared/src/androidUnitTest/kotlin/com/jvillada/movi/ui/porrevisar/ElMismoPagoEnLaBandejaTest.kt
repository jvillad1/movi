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
import com.jvillada.movi.shared.model.ConfirmarElMismoPago
import com.jvillada.movi.shared.model.EventDay
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.GrupoDeAvisos
import com.jvillada.movi.shared.model.MismoPagoConfirmado
import com.jvillada.movi.shared.model.ParsedSms
import com.jvillada.movi.shared.model.SMS_STATE_CONFIRMED
import com.jvillada.movi.shared.model.SMS_STATE_PENDING
import com.jvillada.movi.shared.model.SmsMessage
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.UserProfile
import com.jvillada.movi.theme.MoviTheme
import com.jvillada.movi.ui.Screen
import com.jvillada.movi.ui.sms.ESTE_ES_OTRO_PAGO
import com.jvillada.movi.ui.sms.NO_SON_EL_MISMO_PAGO
import com.jvillada.movi.ui.sms.SMSReconcileScreen
import com.jvillada.movi.ui.sms.TAG_TARJETA_DEL_MISMO_PAGO
import com.jvillada.movi.ui.sms.canalesDelMismoPago
import com.jvillada.movi.ui.sms.conLoResueltoEnLaBandeja
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * # Un pago, una tarjeta, montado de verdad
 *
 * El mismo pago avisado por SMS, por la app del banco y por Google Wallet (textos de ejemplo): en
 * «Por revisar» es una tarjeta, «No son el mismo pago» la separa en tres, y Reconciliar confirma
 * el pago una vez y cierra los tres avisos.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h2400dp-xhdpi")
class ElMismoPagoEnLaBandejaTest {

    @get:Rule val composeRule = createComposeRule()

    private val ids = listOf("s", "w", "n")

    private fun aviso(id: String, bank: String, texto: String, time: String = "2026-09-25 09:15") = SmsMessage(
        id = id, time = time, bank = bank, text = texto, state = SMS_STATE_PENDING, det = "",
        grupoId = "s", miembrosDelGrupo = ids,
    )

    private val delSms = aviso("s", "85540", "Bancolombia: Compraste \$12.300,00 en PANADERIA LA ESQ con tu T.Deb *1111.")
    private val deWallet = aviso("w", "Notificación · Google Wallet", "PANADERIA LA ESQUINA: COP12,300 with Debito Mastercard ••1111")
    private val deLaApp = aviso("n", "Notificación · Bancolombia", "Compraste \$12.300,00 en PANADERIA LA ESQUINA con tu T.Deb *1111.", time = "2026-09-25 09:16")
    private val suelto = SmsMessage(
        id = "otro", time = "2026-09-24 18:00", bank = "85540",
        text = "Bancolombia: Compraste \$40.000,00 en OTRA TIENDA con tu T.Deb *1111.", state = SMS_STATE_PENDING, det = "",
    )

    private val banco = Account("acc", "Ahorros 1111", AccountType.SAVINGS, 500_000L, "COP")

    private var mensajes = listOf(deLaApp, deWallet, delSms, suelto)
    private val separadosEnElServer = mutableListOf<List<String>>()
    private val confirmaciones = mutableListOf<Pair<String, ConfirmarElMismoPago>>()
    private val otrosPagos = mutableListOf<String>()
    private val navegaciones = mutableListOf<Screen>()

    private open inner class Repo : RepositorioDePrueba() {
        override suspend fun getSmsMessages(): List<SmsMessage> = mensajes
        override suspend fun getEventsByDay(): List<EventDay> = emptyList()
        override suspend fun getCardPaymentCandidates(): List<FinancialEvent> = emptyList()
        override suspend fun getAccounts(): List<Account> = listOf(banco)
        override suspend fun getDestinos(): List<com.jvillada.movi.shared.model.DestinoConocido> = emptyList()
        override suspend fun getUserProfile(): UserProfile =
            UserProfile(id = "u1", email = "juan@movi.test", name = "Juan", avatarColor = "#4F7CFF")
        override suspend fun noSonElMismoPago(grupoId: String, miembros: List<String>) {
            separadosEnElServer += miembros
            mensajes = mensajes.map { it.copy(grupoId = null, miembrosDelGrupo = emptyList()) }
        }
        // Reconciliar
        override suspend fun getSms(id: String): SmsMessage = mensajes.first { it.id == id }
        override suspend fun getAvisosDelMismoPago(grupoId: String): GrupoDeAvisos =
            GrupoDeAvisos(grupoId, listOf(delSms, deWallet, deLaApp), propuestaDe = "s")
        override suspend fun parseSms(id: String): ParsedSms =
            ParsedSms(12_300.0, "PANADERIA LA ESQ", TransactionType.EXPENSE, "Comida")
        override suspend fun getSmsCoincidencias(id: String): List<FinancialEvent> = emptyList()
        override suspend fun getEvents(accountId: String?): List<FinancialEvent> = emptyList()
        override suspend fun confirmarElMismoPago(grupoId: String, pedido: ConfirmarElMismoPago): MismoPagoConfirmado {
            confirmaciones += grupoId to pedido
            return MismoPagoConfirmado(eventoId = pedido.evento?.id, creado = true, cerrados = pedido.miembros)
        }
        override suspend fun esteEsOtroPago(id: String) {
            otrosPagos += id
        }
    }

    @After
    fun limpiar() {
        Repositories.sustitutoDePrueba = null
    }

    private fun cuantos(texto: String) =
        composeRule.onAllNodesWithText(texto, substring = true, useUnmergedTree = true).fetchSemanticsNodes().size

    private fun tarjetasDelPago() =
        composeRule.onAllNodesWithTag(TAG_TARJETA_DEL_MISMO_PAGO, useUnmergedTree = true).fetchSemanticsNodes().size

    private fun esperar(condicion: () -> Boolean) {
        composeRule.waitUntil(timeoutMillis = 5_000) { condicion() }
        composeRule.waitForIdle()
    }

    /** `performSemanticsAction`: bajo Robolectric `performClick` no llega al composable. */
    private fun tocar(texto: String) {
        composeRule.onNodeWithText(texto).performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
    }

    private fun montarLaBandeja() {
        Repositories.sustitutoDePrueba = Repo()
        composeRule.setContent {
            MoviTheme { Box(Modifier.fillMaxSize()) { PorRevisarScreen(onNavigate = { navegaciones += it }) } }
        }
        esperar { cuantos("Revisar") > 0 }
    }

    @Test
    fun `Por revisar muestra una tarjeta por pago`() {
        montarLaBandeja()

        assertEquals(1, tarjetasDelPago())
        assertEquals(1, cuantos("Mismo pago avisado 3 veces"))
        // Por dónde llegó cada aviso, en el orden en que llegaron.
        assertEquals(1, cuantos("SMS · Google Wallet · Bancolombia"))
        // Dos «Revisar»: el del pago y el de la otra compra. No cuatro.
        assertEquals(2, cuantos("Revisar"))

        // Los textos de los otros avisos van plegados.
        assertEquals(0, cuantos("COP12,300"))
        tocar("Ver los 3 avisos")
        assertEquals(1, cuantos("COP12,300"))

        // El primero es el del pago (el aviso más reciente es suyo).
        composeRule.onAllNodesWithText("Revisar")[0].performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
        assertEquals(Screen.SMSReconcile("n"), navegaciones.single())
    }

    @Test
    fun `No son el mismo pago separa los avisos en tarjetas propias`() {
        montarLaBandeja()

        tocar(NO_SON_EL_MISMO_PAGO)
        esperar { tarjetasDelPago() == 0 }

        assertEquals(listOf(ids), separadosEnElServer)
        assertEquals(4, cuantos("Revisar"))
    }

    @Test
    fun `Reconciliar confirma el pago una vez y cierra los tres avisos`() {
        Repositories.sustitutoDePrueba = Repo()
        composeRule.setContent {
            MoviTheme { Box(Modifier.fillMaxSize()) { SMSReconcileScreen(onNavigate = {}, smsId = "n") } }
        }
        // Los tres avisos, cada uno con su «Este es otro pago».
        esperar { cuantos(ESTE_ES_OTRO_PAGO) == 3 && cuantos("Revisando si ya está anotado") == 0 }

        tocar("Confirmar")
        esperar { confirmaciones.isNotEmpty() }
        // Un segundo toque mientras guarda o después no manda otro.
        runCatching { tocar("Confirmar") }

        val (grupoId, pedido) = confirmaciones.single()
        assertEquals("s", grupoId)
        assertEquals(ids.toSet(), pedido.miembros.toSet())
        val evento = pedido.evento!!
        assertEquals(12_300L, evento.amount)
        assertEquals("acc", evento.accountId)
    }

    @Test
    fun `Este es otro pago saca un aviso desde Reconciliar`() {
        Repositories.sustitutoDePrueba = Repo()
        composeRule.setContent {
            MoviTheme { Box(Modifier.fillMaxSize()) { SMSReconcileScreen(onNavigate = {}, smsId = "n") } }
        }
        esperar { cuantos(ESTE_ES_OTRO_PAGO) == 3 }

        composeRule.onAllNodesWithText(ESTE_ES_OTRO_PAGO)[2].performSemanticsAction(SemanticsActions.OnClick)
        esperar { otrosPagos.isNotEmpty() }
        assertEquals(listOf("n"), otrosPagos)
    }

    @Test
    fun `el contador cuenta pagos, no avisos`() {
        assertEquals(2, cuantosPorRevisar(mensajes, emptyList(), emptyList()))
        assertEquals(listOf("n", "otro"), pagosPorRevisar(mensajes).map { it.id })
        // Separados, son cuatro.
        assertEquals(4, cuantosPorRevisar(conLoResueltoEnLaBandeja(mensajes, emptyMap(), ids.toSet()), emptyList(), emptyList()))
        // Con el pago cerrado, uno.
        assertEquals(1, cuantosPorRevisar(conLoResueltoEnLaBandeja(mensajes, ids.associateWith { SMS_STATE_CONFIRMED }, emptySet()), emptyList(), emptyList()))
        assertEquals("SMS · Google Wallet", canalesDelMismoPago(listOf(delSms, deWallet)))
    }
}
