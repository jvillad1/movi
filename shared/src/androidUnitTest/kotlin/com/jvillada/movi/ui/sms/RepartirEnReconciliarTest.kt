package com.jvillada.movi.ui.sms

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.RepositorioDePrueba
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.CREDIT_RULE_PREFIX
import com.jvillada.movi.shared.model.CUOTA_CATEGORY
import com.jvillada.movi.shared.model.ConfirmarElMismoPago
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.MismoPagoConfirmado
import com.jvillada.movi.shared.model.OperacionDelAviso
import com.jvillada.movi.shared.model.PagoParaRepartir
import com.jvillada.movi.shared.model.ParsedSms
import com.jvillada.movi.shared.model.PropuestaDeReparto
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
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * # El giro del papá, repartido en Reconciliar
 *
 * $4.280.000 a la cuenta del papá es exactamente la Libranza BBVA ($2.051.848) más la Hipoteca
 * Davibank ($2.228.152): Reconciliar lo dice arriba y «Repartir así» manda las dos cuotas, cada una
 * con sus dos patas. Sin suma exacta, «Repartir entre varios pagos…» deja elegirlos y el último toma
 * el resto.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h2400dp-xhdpi")
class RepartirEnReconciliarTest {

    @get:Rule val composeRule = createComposeRule()

    private val texto = "Bancolombia: Transferiste \$4.280.000 desde tu cuenta *8133 a la cuenta *036200170136 el 27/09/2026 a las 10:00."
    private val aviso = SmsMessage(id = "sms_papa", time = "2026-09-27 10:00", bank = "85540", text = texto, state = SMS_STATE_PENDING, det = "")
    private val ahorros = Account("acc-8133", "Bancolombia Ahorros 8133 · 4057", AccountType.SAVINGS, 20_000_000L, "COP")
    private val libranza = Account("acc_libranza_papa_bbva", "Libranza Papá (BBVA)", AccountType.LOAN, 97_718_920L, "COP")
    private val hipoteca = Account("acc_hipoteca_papa_davibank", "Hipoteca Papá (Davibank)", AccountType.LOAN, 198_223_917L, "COP")

    private fun pago(cuenta: Account, nombre: String, monto: Long) = PagoParaRepartir(
        ruleId = "$CREDIT_RULE_PREFIX${cuenta.id}", nombre = nombre, monto = monto, categoria = CUOTA_CATEGORY,
        periodo = "2026-09", vence = "2026-09-27", cuentaDeLaDeudaId = cuenta.id, asociado = true,
    )
    private val bbva = pago(libranza, "Cuota Libranza Papá (BBVA)", 2_051_848L)
    private val davibank = pago(hipoteca, "Cuota Hipoteca Papá (Davibank)", 2_228_152L)

    private var propuesta = PropuestaDeReparto(
        avisoId = "sms_papa", monto = 4_280_000L, destinoId = "dst_papa", destinoNombre = "Papá",
        pagos = listOf(davibank, bbva), exacto = listOf(davibank.ruleId, bbva.ruleId),
    )
    private val pedidos = mutableListOf<Pair<String, ConfirmarElMismoPago>>()

    private inner class Repo : RepositorioDePrueba() {
        override suspend fun getSms(id: String): SmsMessage = aviso
        override suspend fun parseSms(id: String): ParsedSms =
            ParsedSms(4_280_000.0, "Transferencia a Papá", TransactionType.EXPENSE, "Otros")
        override suspend fun getSmsCoincidencias(id: String): List<FinancialEvent> = emptyList()
        override suspend fun getAccounts(): List<Account> = listOf(ahorros, libranza, hipoteca)
        override suspend fun getEvents(accountId: String?): List<FinancialEvent> = emptyList()
        override suspend fun getRepartoDelAviso(id: String): PropuestaDeReparto = propuesta
        override suspend fun confirmarElMismoPago(grupoId: String, pedido: ConfirmarElMismoPago): MismoPagoConfirmado {
            pedidos += grupoId to pedido
            return MismoPagoConfirmado(eventoId = pedido.reparto?.first()?.patas?.origenEventId, creado = true)
        }
    }

    @After
    fun limpiar() {
        Repositories.sustitutoDePrueba = null
    }

    private fun cuantos(texto: String) =
        composeRule.onAllNodesWithText(texto, substring = true, useUnmergedTree = true).fetchSemanticsNodes().size

    private fun esperar(condicion: () -> Boolean) {
        composeRule.waitUntil(timeoutMillis = 5_000) { condicion() }
        composeRule.waitForIdle()
    }

    private fun tocar(texto: String) {
        composeRule.onNodeWithText(texto, substring = true).performScrollTo().performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
    }

    private fun montar() {
        Repositories.sustitutoDePrueba = Repo()
        composeRule.setContent {
            MoviTheme { Box(Modifier.fillMaxSize()) { SMSReconcileScreen(onNavigate = {}, smsId = "sms_papa") } }
        }
    }

    @Test
    fun `Reconciliar propone los dos pagos y Repartir asi manda las dos cuotas con sus patas`() {
        montar()
        esperar { cuantos("Son 2 pagos") == 1 }
        assertEquals(1, cuantos("Son 2 pagos: Cuota Hipoteca Papá (Davibank) \$2.228.152 + Cuota Libranza Papá (BBVA) \$2.051.848"))

        tocar(REPARTIR_ASI)
        esperar { pedidos.isNotEmpty() }

        val (grupo, pedido) = pedidos.single()
        assertEquals("sms_papa", grupo)
        assertEquals(listOf("sms_papa"), pedido.miembros)
        assertNull(pedido.evento)
        val partes = pedido.reparto!!
        assertEquals(listOf(davibank.ruleId to 2_228_152L, bbva.ruleId to 2_051_848L), partes.map { it.ruleId to it.monto })
        partes.forEach { parte ->
            val patas = parte.patas!!
            assertEquals(OperacionDelAviso.CUOTA, patas.operacion)
            assertEquals("acc-8133", patas.origenId)
            assertEquals(parte.ruleId.removePrefix(CREDIT_RULE_PREFIX), patas.destinoId)
        }
    }

    @Test
    fun `sin suma exacta, a mano, el ultimo toma el resto`() {
        propuesta = propuesta.copy(monto = 4_300_000L, exacto = emptyList())
        montar()
        esperar { cuantos(REPARTIR_A_MANO) == 1 }
        assertEquals(0, cuantos("Son 2 pagos"))

        tocar(REPARTIR_A_MANO)
        tocar("Cuota Libranza Papá (BBVA)")
        tocar("Cuota Hipoteca Papá (Davibank)")
        tocar(botonDeRepartirAMano(2))
        esperar { pedidos.isNotEmpty() }

        val partes = pedidos.single().second.reparto!!
        assertEquals(listOf(bbva.ruleId to 2_051_848L, davibank.ruleId to 4_300_000L - 2_051_848L), partes.map { it.ruleId to it.monto })
    }
}
