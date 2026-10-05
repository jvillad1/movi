package com.jvillada.movi.ui.porrevisar

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.RepositorioDePrueba
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.ConfirmarDebitoAutomatico
import com.jvillada.movi.shared.model.CreatePagoDeCuotaRequest
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.DebitoAutomaticoPorConfirmar
import com.jvillada.movi.shared.model.EventDay
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.NOTA_DEL_DEBITO_AUTOMATICO
import com.jvillada.movi.shared.model.OrigenDelDebito
import com.jvillada.movi.shared.model.PagoDeCuotaResult
import com.jvillada.movi.shared.model.SmsMessage
import com.jvillada.movi.shared.model.UserProfile
import com.jvillada.movi.theme.MoviTheme
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * # La tarjeta del débito automático en «Por revisar»
 *
 * La cuota que el banco cobró solo aparece en su propio bloque —no entre los mensajes del banco—,
 * con el texto armado y tres salidas. «Sí, se cobró» manda la cuota por el camino de siempre
 * (`payInstallment`) con los ids de la propuesta y la fecha del vencimiento; «No se cobró» la
 * descarta. Mientras haya una, la bandeja no dice «Todo al día».
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h1200dp-xhdpi")
class DebitoAutomaticoEnLaBandejaTest {

    @get:Rule val composeRule = createComposeRule()

    private val ahorros = Account("acc_ahorros", "Bancolombia Ahorros", AccountType.SAVINGS, 2_000_000L)

    private val debito = DebitoAutomaticoPorConfirmar(
        ruleId = "credit_acc_9695",
        periodo = "2026-10",
        origen = OrigenDelDebito.CUOTA_DE_CREDITO,
        nombre = "Cuota Libre inversión 9695",
        monto = 1_204_064L,
        vence = "2026-10-15",
        cuentaId = ahorros.id,
        cuentaNombre = ahorros.name,
        categoria = "Cuota de crédito",
        pataDelDineroId = "ev_deb_abc_s",
        deudaId = "acc_9695",
        pataDeLaDeudaId = "ev_deb_abc_e",
        transferId = "tr_deb_abc",
    )

    private var pagado: CreatePagoDeCuotaRequest? = null
    private var recurrenteConfirmado: ConfirmarDebitoAutomatico? = null
    private var descartado: Pair<String, String>? = null
    private var debitos = listOf(debito)

    private inner class Repo : RepositorioDePrueba() {
        override suspend fun getSmsMessages(): List<SmsMessage> = emptyList()
        override suspend fun getEventsByDay(): List<EventDay> = emptyList()
        override suspend fun getCardPaymentCandidates(): List<FinancialEvent> = emptyList()
        override suspend fun getAccounts(): List<Account> = listOf(ahorros)
        override suspend fun getUserProfile(): UserProfile = UserProfile(
            id = "u1", email = "juan@movi.test", name = "Juan", avatarColor = "#4F7CFF", smsAlertMuted = false,
        )
        override suspend fun getDebitosAutomaticos(): List<DebitoAutomaticoPorConfirmar> = debitos
        override suspend fun payInstallment(request: CreatePagoDeCuotaRequest): PagoDeCuotaResult {
            pagado = request
            debitos = emptyList()
            return PagoDeCuotaResult(deudaRestante = 0L, patas = emptyList())
        }
        override suspend fun confirmarDebitoRecurrente(pedido: ConfirmarDebitoAutomatico): FinancialEvent {
            recurrenteConfirmado = pedido
            debitos = emptyList()
            return FinancialEvent(
                id = pedido.eventoId, accountId = ahorros.id, type = TransactionType.EXPENSE, amount = pedido.monto,
                category = "Seguros", description = "Seguro Sura", timestamp = 0L,
            )
        }
        override suspend fun descartarDebitoAutomatico(ruleId: String, periodo: String) {
            descartado = ruleId to periodo
            debitos = emptyList()
        }
    }

    @After
    fun limpiar() {
        Repositories.sustitutoDePrueba = null
    }

    private fun montar() {
        Repositories.sustitutoDePrueba = Repo()
        composeRule.setContent {
            MoviTheme { Box(Modifier.fillMaxSize()) { PorRevisarScreen(onNavigate = {}) } }
        }
        composeRule.waitUntil(5_000) { hay("¿se cobró?") }
    }

    private fun hay(texto: String): Boolean =
        composeRule.onAllNodesWithText(texto, substring = true, ignoreCase = true, useUnmergedTree = true)
            .fetchSemanticsNodes().isNotEmpty()

    @Test
    fun `la propuesta se ve armada y no dice todo al dia`() {
        montar()
        assertTrue(hay(TITULO_DE_LOS_DEBITOS))
        assertTrue(hay("Débito automático: Cuota Libre inversión 9695 · \$1.204.064 desde Bancolombia Ahorros — ¿se cobró?"))
        assertTrue(hay(SI_SE_COBRO) && hay(CAMBIAR_MONTO) && hay(NO_SE_COBRO))
        assertFalse(hay(TODO_AL_DIA), "con un débito por confirmar la bandeja no está al día")
    }

    @Test
    fun `si se cobro manda la cuota con los ids y la fecha de la propuesta`() {
        montar()
        composeRule.onNodeWithText(SI_SE_COBRO).performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitUntil(5_000) { pagado != null }
        val pedido = assertNotNull(pagado)
        assertEquals(ahorros.id, pedido.fromAccountId)
        assertEquals("acc_9695", pedido.debtAccountId)
        assertEquals(1_204_064L, pedido.amount)
        assertEquals("tr_deb_abc", pedido.transferId)
        assertEquals("ev_deb_abc_s", pedido.fromEventId)
        assertEquals("ev_deb_abc_e", pedido.toEventId)
        assertEquals(NOTA_DEL_DEBITO_AUTOMATICO, pedido.note)
        composeRule.waitUntil(5_000) { !hay("¿se cobró?") }
    }

    @Test
    fun `no se cobro la descarta sin anotar nada`() {
        montar()
        composeRule.onNodeWithText(NO_SE_COBRO).performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitUntil(5_000) { descartado != null }
        assertEquals("credit_acc_9695" to "2026-10", descartado)
        assertEquals(null, pagado)
        composeRule.waitUntil(5_000) { hay(TODO_AL_DIA) }
    }

    @Test
    fun `un recurrente se confirma por su propio camino, no como cuota`() {
        debitos = listOf(
            debito.copy(
                ruleId = "rr_seguro", origen = OrigenDelDebito.RECURRENTE, nombre = "Seguro Sura", monto = 98_500L,
                categoria = "Seguros", pataDelDineroId = "ev_deb_seg_s", deudaId = null, pataDeLaDeudaId = null, transferId = null,
            ),
        )
        montar()
        composeRule.onNodeWithText(SI_SE_COBRO).performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitUntil(5_000) { recurrenteConfirmado != null }
        assertEquals(ConfirmarDebitoAutomatico("rr_seguro", "2026-10", 98_500L, "ev_deb_seg_s"), recurrenteConfirmado)
        assertEquals(null, pagado, "un seguro no es la cuota de un crédito")
    }

    @Test
    fun `cambiar monto abre el campo con la cuota puesta`() {
        montar()
        composeRule.onNodeWithText(CAMBIAR_MONTO).performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
        assertTrue(hay("Lo que cobró el banco"))
    }
}
