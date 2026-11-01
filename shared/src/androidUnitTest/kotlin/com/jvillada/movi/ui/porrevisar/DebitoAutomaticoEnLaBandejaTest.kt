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
import com.jvillada.movi.shared.model.DebitoAutomaticoPorConfirmar
import com.jvillada.movi.shared.model.EventDay
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.OrigenDelDebito
import com.jvillada.movi.shared.model.SmsMessage
import com.jvillada.movi.shared.model.TransactionType
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

    private var confirmado: ConfirmarDebitoAutomatico? = null
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
        override suspend fun confirmarDebitoAutomatico(pedido: ConfirmarDebitoAutomatico): List<FinancialEvent> {
            confirmado = pedido
            debitos = emptyList()
            return emptyList()
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

    private fun montar(esperar: String = "¿se cobró?") {
        Repositories.sustitutoDePrueba = Repo()
        composeRule.setContent {
            MoviTheme { Box(Modifier.fillMaxSize()) { PorRevisarScreen(onNavigate = {}) } }
        }
        composeRule.waitUntil(5_000) { hay(esperar) }
    }

    private fun hay(texto: String): Boolean =
        composeRule.onAllNodesWithText(texto, substring = true, ignoreCase = true, useUnmergedTree = true)
            .fetchSemanticsNodes().isNotEmpty()

    @Test
    fun `la propuesta se ve armada y no dice todo al dia`() {
        montar()
        assertTrue(hay(TITULO_DE_LOS_DEBITOS))
        // Vuelta de diseño 2: la cabeza de un aviso —lo que se cobró, el monto y de dónde sale—, sin
        // la píldora «Por confirmar» (en la bandeja todo lo está).
        assertTrue(hay("Cuota Libre inversión 9695"))
        assertTrue(hay("−\$1.204.064"))
        assertTrue(hay("Débito automático · Bancolombia Ahorros · vence el"))
        assertFalse(hay("Por confirmar"))
        assertTrue(hay(SI_SE_COBRO) && hay(CAMBIAR_MONTO) && hay(NO_SE_COBRO))
        assertFalse(hay(TODO_AL_DIA), "con un débito por confirmar la bandeja no está al día")
    }

    @Test
    fun `si se cobro manda la cuota con los ids de la propuesta`() {
        montar()
        composeRule.onNodeWithText(SI_SE_COBRO).performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitUntil(5_000) { confirmado != null }
        assertEquals(
            ConfirmarDebitoAutomatico(
                ruleId = "credit_acc_9695", periodo = "2026-10", monto = 1_204_064L,
                eventoId = "ev_deb_abc_s", transferId = "tr_deb_abc", pataDeLaDeudaId = "ev_deb_abc_e",
            ),
            confirmado,
        )
        composeRule.waitUntil(5_000) { !hay("¿se cobró?") }
    }

    @Test
    fun `no se cobro la descarta sin anotar nada`() {
        montar()
        composeRule.onNodeWithText(NO_SE_COBRO).performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitUntil(5_000) { descartado != null }
        assertEquals("credit_acc_9695" to "2026-10", descartado)
        assertEquals(null, confirmado, "decir que no, no anota nada")
        composeRule.waitUntil(5_000) { hay(TODO_AL_DIA) }
    }

    @Test
    fun `un recurrente se confirma con su gasto, sin patas de deuda`() {
        debitos = listOf(
            debito.copy(
                ruleId = "rr_seguro", origen = OrigenDelDebito.RECURRENTE, nombre = "Seguro Sura", monto = 98_500L,
                categoria = "Seguros", pataDelDineroId = "ev_deb_seg_s", deudaId = null, pataDeLaDeudaId = null, transferId = null,
            ),
        )
        montar()
        composeRule.onNodeWithText(SI_SE_COBRO).performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitUntil(5_000) { confirmado != null }
        assertEquals(ConfirmarDebitoAutomatico("rr_seguro", "2026-10", 98_500L, "ev_deb_seg_s"), confirmado)
    }

    @Test
    fun `cambiar monto abre el campo con la cuota puesta`() {
        montar()
        composeRule.onNodeWithText(CAMBIAR_MONTO).performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
        assertTrue(hay("Lo que cobró el banco"))
    }

    // ── Vuelta 90 · lo esperado ─────────────────────────────────────────────────

    private val salario = debito.copy(
        ruleId = "rr_salario", origen = OrigenDelDebito.RECURRENTE, nombre = "Salario", monto = 8_500_000L,
        categoria = "Salario", pataDelDineroId = "ev_deb_sal_s", deudaId = null, pataDeLaDeudaId = null, transferId = null,
        tipo = TransactionType.INCOME, esperado = true, ultimoMonto = 8_731_000L,
    )

    @Test
    fun `la nomina esperada pregunta si llego y cambiar monto abre con el de la ultima vez`() {
        debitos = listOf(salario)
        montar(esperar = TEXTO_DE_LO_ESPERADO_EN_LA_BANDEJA)
        assertTrue(hay(TITULO_DE_LOS_DEBITOS))
        assertTrue(hay("$ROTULO_DE_LO_ESPERADO · Bancolombia Ahorros · el"))
        assertTrue(hay(SI_ME_LLEGO) && hay(NO_ME_LLEGO))
        assertFalse(hay(SI_SE_COBRO), "no es un débito: no se pregunta si se cobró")
        composeRule.onNodeWithText(CAMBIAR_MONTO).performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
        assertTrue(hay("Lo que te llegó"))
        composeRule.onNodeWithText(SI_ME_LLEGO).performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitUntil(5_000) { confirmado != null }
        assertEquals(ConfirmarDebitoAutomatico("rr_salario", "2026-10", 8_731_000L, "ev_deb_sal_s"), confirmado)
    }

    @Test
    fun `un gasto esperado se descarta con no se pago`() {
        debitos = listOf(salario.copy(ruleId = "rr_netflix", nombre = "Netflix", monto = 49_900L, tipo = TransactionType.EXPENSE, ultimoMonto = null))
        montar(esperar = TEXTO_DE_LO_ESPERADO_EN_LA_BANDEJA)
        assertTrue(hay(SI_SE_PAGO))
        composeRule.onNodeWithText(NO_SE_PAGO).performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitUntil(5_000) { descartado != null }
        assertEquals("rr_netflix" to "2026-10", descartado)
        assertEquals(null, confirmado)
    }

    @Test
    fun `ya lo anote saca la propuesta sin anotar nada`() {
        debitos = listOf(salario)
        montar(esperar = TEXTO_DE_LO_ESPERADO_EN_LA_BANDEJA)
        composeRule.onNodeWithText(YA_LO_ANOTE).performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitUntil(5_000) { descartado != null }
        assertEquals("rr_salario" to "2026-10", descartado)
        assertEquals(null, confirmado, "«Ya lo anoté» no anota otra vez")
    }
}
