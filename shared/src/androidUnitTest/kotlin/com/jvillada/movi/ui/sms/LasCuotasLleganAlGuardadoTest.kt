package com.jvillada.movi.ui.sms

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.RepositorioDePrueba
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.EventDay
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.ParsedSms
import com.jvillada.movi.shared.model.PropuestaDelAviso
import com.jvillada.movi.shared.model.PropuestasDeLaBandeja
import com.jvillada.movi.shared.model.SMS_STATE_CONFIRMED
import com.jvillada.movi.shared.model.SMS_STATE_PENDING
import com.jvillada.movi.shared.model.SmsMessage
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.UserProfile
import com.jvillada.movi.theme.MoviTheme
import com.jvillada.movi.ui.fecha.hoyEnAppZone
import com.jvillada.movi.ui.porrevisar.PorRevisarScreen
import com.jvillada.movi.ui.quickadd.tagDeLaPastillaDeCuotas
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * # Revisión de la vuelta 133 · las cuotas elegidas llegan al guardado, de punta a punta
 *
 * Las pruebas de la tarjeta sola no pasaban por el cableado de verdad. Acá se montan **Por revisar** y
 * **Revisar aviso** con un repositorio falso: se eligen las cuotas, se confirma, y se mira el movimiento
 * que recibe `postEvent`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h2400dp-xhdpi")
class LasCuotasLleganAlGuardadoTest {

    @get:Rule val composeRule = createComposeRule()

    private val master = Account("acc-3684", "Master 3684", AccountType.CREDIT_CARD, 0L, "COP")
    private val hoy = hoyEnAppZone().toString()
    private val leido = ParsedSms(1_200_000.0, "ALKOSTO", TransactionType.EXPENSE, "Hogar", cuentaSugeridaId = "acc-3684")
    private val avisos = mutableListOf(
        SmsMessage(
            id = "alk", time = "$hoy 11:02", bank = "85540", state = SMS_STATE_PENDING, det = "",
            text = "Bancolombia: Compraste \$1.200.000,00 en ALKOSTO con tu T.Cred *3684, el 04/11/2026 a las 11:02.",
        ),
    )
    private val publicados = mutableListOf<FinancialEvent>()

    private inner class Repo : RepositorioDePrueba() {
        override suspend fun getSmsMessages(): List<SmsMessage> = avisos.toList()
        override suspend fun getSms(id: String): SmsMessage = avisos.single { it.id == id }
        override suspend fun parseSms(id: String): ParsedSms = leido
        override suspend fun getSmsCoincidencias(id: String): List<FinancialEvent> = emptyList()
        override suspend fun getEventsByDay(): List<EventDay> = emptyList()
        override suspend fun getCardPaymentCandidates(): List<FinancialEvent> = emptyList()
        override suspend fun getAccounts(): List<Account> = listOf(master)
        override suspend fun getDestinos(): List<com.jvillada.movi.shared.model.DestinoConocido> = emptyList()
        override suspend fun getUserProfile(): UserProfile =
            UserProfile(id = "u1", email = "juan@movi.test", name = "Juan", avatarColor = "#4F7CFF")
        override suspend fun getPropuestasDeLaBandeja(): PropuestasDeLaBandeja = PropuestasDeLaBandeja(
            propuestas = if (avisos.first { it.id == "alk" }.state == SMS_STATE_PENDING) listOf(PropuestaDelAviso("alk", leido = leido, grupoId = avisos.first { it.id == "alk" }.grupoId)) else emptyList(),
            categoriasDelDueno = listOf("Hogar", "Comida"),
        )
        override suspend fun postEvent(event: FinancialEvent): FinancialEvent = event.also { publicados += it }
        override suspend fun confirmSmsCon(id: String, eventoId: String) {
            avisos.replaceAll { if (it.id == id) it.copy(state = SMS_STATE_CONFIRMED) else it }
        }
        override suspend fun confirmarElMismoPago(
            grupoId: String,
            pedido: com.jvillada.movi.shared.model.ConfirmarElMismoPago,
        ): com.jvillada.movi.shared.model.MismoPagoConfirmado {
            pedido.evento?.let { publicados += it }
            avisos.replaceAll { if (it.id in pedido.miembros) it.copy(state = SMS_STATE_CONFIRMED) else it }
            return com.jvillada.movi.shared.model.MismoPagoConfirmado(eventoId = pedido.evento?.id, creado = true, cerrados = pedido.miembros)
        }
        override suspend fun confirmSms(id: String) {
            avisos.replaceAll { if (it.id == id) it.copy(state = SMS_STATE_CONFIRMED) else it }
        }
    }

    @After
    fun limpiar() { Repositories.sustitutoDePrueba = null }

    private fun cuantos(tag: String) = composeRule.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().size

    private fun tocar(tag: String) {
        composeRule.onNodeWithTag(tag, useUnmergedTree = true).performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
    }

    private fun esperar(condicion: () -> Boolean) {
        composeRule.waitUntil(timeoutMillis = 5_000) { condicion() }
        composeRule.waitForIdle()
    }

    @Test
    fun `en Por revisar, elegir 60 cuotas y confirmar las guarda en el movimiento`() {
        Repositories.sustitutoDePrueba = Repo()
        composeRule.setContent { MoviTheme { Box(Modifier.fillMaxSize()) { PorRevisarScreen(onNavigate = {}) } } }
        esperar { cuantos(TAG_CONFIRMAR_EN_LA_FILA) == 1 && cuantos(TAG_ELEGIR_CUOTAS_EN_LA_TARJETA) == 1 }
        tocar(TAG_ELEGIR_CUOTAS_EN_LA_TARJETA)
        tocar(tagDeLaPastillaDeCuotas(60, TAG_FILA_DE_CUOTAS_EN_LA_TARJETA))
        tocar(TAG_CONFIRMAR_EN_LA_FILA)
        esperar { publicados.isNotEmpty() }
        assertEquals(60, publicados.single().cuotas)
        assertEquals("acc-3684", publicados.single().accountId)
    }

    @Test
    fun `en Por revisar, sin elegir se guarda como siempre`() {
        Repositories.sustitutoDePrueba = Repo()
        composeRule.setContent { MoviTheme { Box(Modifier.fillMaxSize()) { PorRevisarScreen(onNavigate = {}) } } }
        esperar { cuantos(TAG_CONFIRMAR_EN_LA_FILA) == 1 }
        tocar(TAG_CONFIRMAR_EN_LA_FILA)
        esperar { publicados.isNotEmpty() }
        assertNull(publicados.single().cuotas, "un «1» que nadie eligió no se manda")
    }

    @Test
    fun `en Revisar aviso, elegir 24 cuotas y confirmar las guarda en el movimiento`() {
        Repositories.sustitutoDePrueba = Repo()
        composeRule.setContent { MoviTheme { Box(Modifier.fillMaxSize()) { SMSReconcileScreen(onNavigate = {}, smsId = "alk") } } }
        esperar { cuantos(TAG_FILA_DE_CUOTAS_EN_REVISAR) == 1 }
        tocar(tagDeLaPastillaDeCuotas(24, TAG_FILA_DE_CUOTAS_EN_REVISAR))
        composeRule.onNodeWithText("Confirmar").performSemanticsAction(SemanticsActions.OnClick)
        esperar { publicados.isNotEmpty() }
        assertEquals(24, publicados.single().cuotas)
    }

    /**
     * Vuelta 134 · **la tarjeta de un mismo pago avisado varias veces** (el SMS y la notificación del banco de
     * la misma compra) tampoco tenía «¿A cuántas cuotas? · Elegir». Ahora la tiene, y lo elegido viaja en el
     * movimiento de `…/grupo/{id}/confirmar`.
     */
    @Test
    fun `en Por revisar, un pago avisado dos veces tambien pregunta las cuotas y las manda en el grupo`() {
        val sms = avisos.single().copy(grupoId = "alk", miembrosDelGrupo = listOf("alk", "alk-app"))
        avisos.clear()
        avisos += sms
        avisos += sms.copy(id = "alk-app", bank = "Notificación · Bancolombia", time = "$hoy 11:03")
        Repositories.sustitutoDePrueba = Repo()
        composeRule.setContent { MoviTheme { Box(Modifier.fillMaxSize()) { PorRevisarScreen(onNavigate = {}) } } }
        esperar { cuantos(TAG_TARJETA_DEL_MISMO_PAGO) == 1 && cuantos(TAG_ELEGIR_CUOTAS_EN_LA_TARJETA) == 1 }
        tocar(TAG_ELEGIR_CUOTAS_EN_LA_TARJETA)
        tocar(tagDeLaPastillaDeCuotas(36, TAG_FILA_DE_CUOTAS_EN_LA_TARJETA))
        tocar(TAG_CONFIRMAR_EN_LA_FILA)
        esperar { publicados.isNotEmpty() }
        assertEquals(36, publicados.single().cuotas, "las cuotas viajan en el movimiento del grupo")
    }
}
