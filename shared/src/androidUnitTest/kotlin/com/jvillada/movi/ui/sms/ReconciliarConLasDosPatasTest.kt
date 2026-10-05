package com.jvillada.movi.ui.sms

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.RepositorioDePrueba
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.AVANCE_DE_TARJETA_CATEGORY
import com.jvillada.movi.shared.model.CARD_PAYMENT_CATEGORY
import com.jvillada.movi.shared.model.DosPatasDelAviso
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.MismoPagoConfirmado
import com.jvillada.movi.shared.model.OperacionDelAviso
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * # Reconciliar un aviso de dos patas
 *
 * Arreglo 1 de la auditoría de la ingesta. La pantalla de verdad, con un repositorio de prueba: antes
 * de confirmar, la tarjeta de resumen dice «Sale de … · entra a …»; confirmar manda la intención
 * (`DosPatasDelAviso`) al server en vez de crear un movimiento suelto; y un avance sin la cuenta a la
 * que entró no se puede confirmar, porque anotado en la tarjeta le bajaría la deuda.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h2400dp-xhdpi")
class ReconciliarConLasDosPatasTest {

    @get:Rule val composeRule = createComposeRule()

    private val ahorros = Account("a", "Bancolombia Ahorros 8133", AccountType.SAVINGS, 50_000_000)
    private val otra = Account("o", "Nequi", AccountType.SAVINGS, 100_000)
    private val masterBlack = Account("m", "Master Black 3684", AccountType.CREDIT_CARD, -2_000_000)
    private val amex = Account("x", "AMEX 9208", AccountType.CREDIT_CARD, 0)
    private val fiducuenta = Account("f", "Fiducuenta 9586", AccountType.INVESTMENT, 20_000_000)
    private val masterBlackUsd = Account("mu", "Master Black USD", AccountType.CREDIT_CARD, -500, currency = "USD")

    private val pagoDeTarjeta = SmsMessage(
        id = "sms_pago",
        time = "2026-10-03 09:15",
        bank = "85784",
        text = "Bancolombia: Pagaste \$386.902 en la tarjeta de credito *3684 desde la cuenta *8133",
        state = SMS_STATE_PENDING,
        det = "",
    )
    private val avance = SmsMessage(
        id = "correo_avance",
        time = "2026-10-03 17:50",
        bank = "Correo · Bancolombia",
        // Sin el número de la cuenta de destino: la tiene que elegir el dueño.
        text = "Bancolombia: Hiciste un avance de \$6,200,000 en tu SUC VIRTUAL desde tu T.Credito *9208 a la cuenta *5555.",
        state = SMS_STATE_PENDING,
        det = "",
    )

    @After
    fun limpiarLaCostura() {
        Repositories.sustitutoDePrueba = null
    }

    private inner class Repo(
        private val sms: SmsMessage,
        private val leido: ParsedSms,
        private val cuentas: List<Account> = listOf(ahorros, otra, masterBlack, amex, fiducuenta),
    ) : RepositorioDePrueba() {
        val conPatas = mutableListOf<Pair<String, DosPatasDelAviso>>()
        val sueltos = mutableListOf<FinancialEvent>()
        override suspend fun getAccounts(): List<Account> = cuentas
        override suspend fun getSms(id: String): SmsMessage = sms
        override suspend fun parseSms(id: String): ParsedSms = leido
        override suspend fun getSmsCoincidencias(id: String): List<FinancialEvent> = emptyList()
        override suspend fun postEvent(event: FinancialEvent): FinancialEvent = event.also { sueltos += it }
        override suspend fun confirmSms(id: String) = Unit
        override suspend fun confirmarConLasDosPatas(smsId: String, patas: DosPatasDelAviso): MismoPagoConfirmado {
            conPatas += smsId to patas
            return MismoPagoConfirmado(eventoId = patas.origenEventId, creado = true, cerrados = listOf(smsId))
        }
    }

    @Test
    fun el_pago_de_la_tarjeta_dice_lo_que_va_a_crear_y_confirma_con_la_intencion() {
        val repo = Repo(pagoDeTarjeta, ParsedSms(386_902.0, "Pago de tarjeta", TransactionType.EXPENSE, CARD_PAYMENT_CATEGORY))
        montar(repo, pagoDeTarjeta.id)

        composeRule.onNodeWithTag(TAG_RESUMEN_DE_LAS_DOS_PATAS)
            .assertIsDisplayed()
            .assertTextEquals("Sale de Bancolombia Ahorros 8133 · entra a Master Black 3684 como pago")
        composeRule.onNodeWithTag(TAG_MONTO_EN_LA_MONEDA_DE_LA_DEUDA).assertDoesNotExist()

        tocar("Confirmar")

        assertEquals(0, repo.sueltos.size, "no se crea un gasto suelto")
        val (aviso, patas) = repo.conPatas.single()
        assertEquals(pagoDeTarjeta.id, aviso)
        assertEquals(OperacionDelAviso.PAGO_DE_TARJETA, patas.operacion)
        assertEquals(ahorros.id, patas.origenId)
        assertEquals(masterBlack.id, patas.destinoId)
        assertEquals(386_902L, patas.monto)
        assertEquals(3, setOf(patas.transferId, patas.origenEventId, patas.destinoEventId).size)
        assertNull(patas.montoEnLaMonedaDeLaDeuda, "entre pesos no hay nada que convertir")
    }

    @Test
    fun pagar_en_pesos_la_tarjeta_en_dolares_pide_cuanto_bajo_la_deuda_y_lo_manda() {
        // La tarjeta en dólares la propone Movi (como el correo de PSE): el aviso no la nombra.
        val pago = pagoDeTarjeta.copy(id = "sms_pago_usd", text = "Bancolombia: Pagaste \$480.000 en la tarjeta de credito desde la cuenta *8133")
        val leido = ParsedSms(480_000.0, "Pago de tarjeta", TransactionType.EXPENSE, CARD_PAYMENT_CATEGORY, deudaSugeridaId = masterBlackUsd.id)
        val repo = Repo(pago, leido, cuentas = listOf(ahorros, masterBlackUsd))
        montar(repo, pago.id)

        composeRule.onNodeWithTag(TAG_RESUMEN_DE_LAS_DOS_PATAS)
            .assertTextEquals("Sale de Bancolombia Ahorros 8133 · entra a Master Black USD como pago")
        composeRule.onNodeWithText(rotuloDelMontoEnLaDeuda("USD")).assertIsDisplayed()
        composeRule.onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag(TAG_MONTO_EN_LA_MONEDA_DE_LA_DEUDA)), useUnmergedTree = true)
            .performTextInput("120")
        composeRule.waitForIdle()
        tocar("Confirmar")

        val (_, patas) = repo.conPatas.single()
        assertEquals(OperacionDelAviso.PAGO_DE_TARJETA, patas.operacion)
        assertEquals(masterBlackUsd.id, patas.destinoId)
        assertEquals(480_000L, patas.monto, "lo que salió de la cuenta, en pesos")
        assertEquals(120L, patas.montoEnLaMonedaDeLaDeuda, "lo que bajó la deuda, en dólares")
    }

    @Test
    fun el_avance_sin_la_cuenta_de_destino_no_se_confirma_y_al_elegirla_manda_el_avance() {
        val repo = Repo(avance, ParsedSms(6_200_000.0, "Avance de la tarjeta *9208", TransactionType.INCOME, AVANCE_DE_TARJETA_CATEGORY))
        montar(repo, avance.id)

        composeRule.onNodeWithTag(TAG_RESUMEN_DE_LAS_DOS_PATAS).assertTextEquals(faltaLaCuentaDeDestino(esAvance = true))
        composeRule.onNodeWithText("Confirmar").assertIsNotEnabled()
        tocar("Confirmar")
        assertTrue(repo.conPatas.isEmpty() && repo.sueltos.isEmpty(), "un avance en la tarjeta le bajaría la deuda")

        composeRule.onNodeWithTag(TAG_CAMBIAR_EL_DESTINO).performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(tagDelDestinoElegible(ahorros.id)).performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(TAG_RESUMEN_DE_LAS_DOS_PATAS)
            .assertTextEquals("Sale de AMEX 9208 como avance · entra a Bancolombia Ahorros 8133")
        composeRule.onNodeWithText("Confirmar").assertIsEnabled()
        tocar("Confirmar")

        val (_, patas) = repo.conPatas.single()
        assertEquals(OperacionDelAviso.AVANCE, patas.operacion)
        assertEquals(amex.id, patas.origenId)
        assertEquals(ahorros.id, patas.destinoId)
        assertEquals(6_200_000L, patas.monto)
        assertEquals(0, repo.sueltos.size)
    }

    @Test
    fun un_aviso_sin_destino_propio_sigue_siendo_un_movimiento_suelto() {
        val compra = pagoDeTarjeta.copy(id = "sms_compra", text = "Bancolombia: Compraste \$15.100 en TOSTAO con tu T.Deb *8133")
        val repo = Repo(compra, ParsedSms(15_100.0, "TOSTAO", TransactionType.EXPENSE, "Comida"))
        montar(repo, compra.id)

        composeRule.onNodeWithTag(TAG_RESUMEN_DE_LAS_DOS_PATAS).assertDoesNotExist()
        tocar("Confirmar")
        assertEquals(1, repo.sueltos.size)
        assertTrue(repo.conPatas.isEmpty())
    }

    @Test
    fun el_aviso_que_entra_desde_una_cuenta_propia_se_confirma_como_traspaso() {
        // Sintético, con la forma del brief: el aviso del lado que recibe nombra de dónde vino.
        val recibido = pagoDeTarjeta.copy(
            id = "sms_recibido",
            text = "Bancolombia: Recibiste \$500.000 de tu cuenta *9586 en tu cuenta *8133 el 04/10/2026 a las 10:15.",
        )
        val leido = ParsedSms(500_000.0, "Desde Fiducuenta 9586", TransactionType.INCOME, "Transferencia", traspasoDesdeId = fiducuenta.id)
        val repo = Repo(recibido, leido)
        montar(repo, recibido.id)

        composeRule.onNodeWithTag(TAG_RESUMEN_DE_LAS_DOS_PATAS)
            .assertTextEquals("Sale de Fiducuenta 9586 · entra a Bancolombia Ahorros 8133 como traspaso")
        tocar("Confirmar")

        assertEquals(0, repo.sueltos.size, "no se anota un ingreso suelto")
        val (_, patas) = repo.conPatas.single()
        assertEquals(OperacionDelAviso.TRASPASO, patas.operacion)
        assertEquals(fiducuenta.id, patas.origenId)
        assertEquals(ahorros.id, patas.destinoId)
        assertTrue(patas.avisoDelLadoQueEntra)
    }

    private fun montar(repo: Repo, smsId: String) {
        Repositories.sustitutoDePrueba = repo
        composeRule.setContent {
            MoviTheme {
                Box(Modifier.fillMaxSize()) { SMSReconcileScreen(onNavigate = {}, smsId = smsId) }
            }
        }
        composeRule.waitForIdle()
    }

    /** `performSemanticsAction`: bajo Robolectric `performClick` no llega al composable. */
    private fun tocar(texto: String) {
        composeRule.onNodeWithText(texto).performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
    }
}
