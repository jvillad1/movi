package com.jvillada.movi.ui.sms

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.RepositorioDePrueba
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.CUOTA_CATEGORY
import com.jvillada.movi.shared.model.DosPatasDelAviso
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.MismoPagoConfirmado
import com.jvillada.movi.shared.model.ParsedSms
import com.jvillada.movi.shared.model.SMS_STATE_PENDING
import com.jvillada.movi.shared.model.SmsMessage
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.theme.MoviTheme
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals

/** La fila «Nota» de Revisar aviso: para qué fue el pago, después de « · » en la descripción. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h2400dp-xhdpi")
class LaNotaDelAvisoTest {

    @get:Rule val composeRule = createComposeRule()

    private val ahorros = Account("a", "Bancolombia Ahorros 8133", AccountType.SAVINGS, 5_000_000)
    private val vehiculo = Account("v", "Vehículo 4083", AccountType.LOAN, 40_000_000)

    private val aviso = SmsMessage(
        id = "sms_qr", time = "2026-11-04 09:15", bank = "85540",
        text = "Bancolombia: Pagaste \$12.000 a la llave 0099887766 desde tu cuenta *8133",
        state = SMS_STATE_PENDING, det = "",
    )
    private val comercio = "Pago QR · llave 0099887766"
    private fun leido(nota: String? = null, categoria: String = "Transporte") =
        ParsedSms(12_000.0, comercio, TransactionType.EXPENSE, categoria, nota = nota)

    private val publicados = mutableListOf<FinancialEvent>()
    private val conPatas = mutableListOf<DosPatasDelAviso>()

    @After
    fun limpiarLaCostura() {
        Repositories.sustitutoDePrueba = null
    }

    private inner class Repo(
        private val leido: ParsedSms,
        private val coincidencias: List<FinancialEvent> = emptyList(),
        private val cuentas: List<Account> = listOf(ahorros),
    ) : RepositorioDePrueba() {
        override suspend fun getAccounts(): List<Account> = cuentas
        override suspend fun getSms(id: String): SmsMessage = aviso
        override suspend fun parseSms(id: String): ParsedSms = leido
        override suspend fun getSmsCoincidencias(id: String): List<FinancialEvent> = coincidencias
        override suspend fun postEvent(event: FinancialEvent): FinancialEvent = event.also { publicados += it }
        override suspend fun confirmSms(id: String) = Unit
        override suspend fun confirmarConLasDosPatas(smsId: String, patas: DosPatasDelAviso): MismoPagoConfirmado {
            conPatas += patas
            return MismoPagoConfirmado(eventoId = patas.origenEventId, creado = true, cerrados = listOf(smsId))
        }
    }

    @Test
    fun la_nota_escrita_queda_en_la_descripcion_y_el_comercio_no_cambia() {
        montar(Repo(leido()))
        escribir("parqueadero del centro")
        tocar("Confirmar")

        assertEquals("$comercio · parqueadero del centro", publicados.single().description)
        assertEquals(comercio, publicados.single().merchant)
    }

    @Test
    fun la_nota_del_correo_de_pse_arranca_escrita_y_se_guarda_una_sola_vez() {
        montar(Repo(leido(nota = "Pago de saldo plan familiar")))
        composeRule.onNode(campo(), useUnmergedTree = true).assertTextContains("Pago de saldo plan familiar")
        tocar("Confirmar")

        assertEquals("$comercio · Pago de saldo plan familiar", publicados.single().description)
    }

    @Test
    fun la_nota_no_pasa_de_80() {
        montar(Repo(leido()))
        escribir("x".repeat(100))
        tocar("Confirmar")

        assertEquals("$comercio · ${"x".repeat(LARGO_MAXIMO_DE_LA_NOTA)}", publicados.single().description)
    }

    @Test
    fun sin_escribir_nada_todo_queda_como_antes() {
        montar(Repo(leido()))
        tocar("Confirmar")

        assertEquals(comercio, publicados.single().description)
    }

    @Test
    fun en_dos_patas_tambien() {
        val cuota = aviso.copy(id = "sms_cuota")
        Repositories.sustitutoDePrueba = Repo(
            ParsedSms(980_000.0, "Banco de Occidente", TransactionType.EXPENSE, CUOTA_CATEGORY, deudaSugeridaId = vehiculo.id),
            cuentas = listOf(ahorros, vehiculo),
        )
        montarYa(cuota.id)
        escribir("cuota de noviembre")
        tocar("Confirmar")

        assertEquals("cuota de noviembre", conPatas.single().nota)
        assertEquals(0, publicados.size)
    }

    @Test
    fun no_aparece_con_es_este() {
        val yaAnotado = FinancialEvent(
            id = "ev1", accountId = ahorros.id, type = TransactionType.EXPENSE, amount = 12_000,
            category = "Transporte", description = "Parqueadero", timestamp = 1_790_000_000_000L,
        )
        montar(Repo(leido(), coincidencias = listOf(yaAnotado)))

        composeRule.onNodeWithText("Es este").assertExists()
        composeRule.onNodeWithTag(TAG_NOTA_DEL_AVISO, useUnmergedTree = true).assertDoesNotExistCompat()
    }

    @Test
    fun no_aparece_en_repartir_en_partes() {
        montar(Repo(leido()))
        composeRule.onNodeWithTag(TAG_NOTA_DEL_AVISO, useUnmergedTree = true).assertExists()
        tocar("Esto pagó varias cosas")

        composeRule.onNodeWithTag(TAG_NOTA_DEL_AVISO, useUnmergedTree = true).assertDoesNotExistCompat()
    }

    // ── Andamio ─────────────────────────────────────────────────────────────────

    private fun campo() = hasSetTextAction() and hasTestTag(TAG_NOTA_DEL_AVISO)

    private fun escribir(texto: String) {
        composeRule.onNode(campo(), useUnmergedTree = true).performTextInput(texto)
        composeRule.waitForIdle()
    }

    private fun montar(repo: Repo) {
        Repositories.sustitutoDePrueba = repo
        montarYa(aviso.id)
    }

    private fun montarYa(smsId: String) {
        composeRule.setContent {
            MoviTheme { Box(Modifier.fillMaxSize()) { SMSReconcileScreen(onNavigate = {}, smsId = smsId) } }
        }
        composeRule.waitForIdle()
    }

    private fun tocar(texto: String) {
        composeRule.onNodeWithText(texto).performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
    }

    private fun androidx.compose.ui.test.SemanticsNodeInteraction.assertDoesNotExistCompat() = assertDoesNotExist()
}
