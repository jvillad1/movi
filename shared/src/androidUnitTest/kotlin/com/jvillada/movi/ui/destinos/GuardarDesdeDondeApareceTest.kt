package com.jvillada.movi.ui.destinos

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasAnyChild
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.RepositorioDePrueba
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.DestinoConocido
import com.jvillada.movi.shared.model.EventDay
import com.jvillada.movi.shared.model.EventOccurrenceMark
import com.jvillada.movi.shared.model.EventSource
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.ParsedSms
import com.jvillada.movi.shared.model.SMS_STATE_PENDING
import com.jvillada.movi.shared.model.SmsMessage
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.UserProfile
import com.jvillada.movi.theme.MoviTheme
import com.jvillada.movi.ui.porrevisar.PorRevisarScreen
import com.jvillada.movi.ui.sms.SMSReconcileScreen
import com.jvillada.movi.ui.transactions.HojaDelMovimiento
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * # Una cuenta de otro se guarda desde donde aparece
 *
 * El pedido del dueño (29-sep): *«gestionar cuentas conocidas no propias es un feature que necesito
 * y debe ser de fácil acceso»*. Hasta acá, cuando el banco avisaba «Transferiste a la cuenta
 * *31973270756», la única forma de ponerle nombre era salir a Ajustes y copiar el número a mano.
 *
 * Lo que se fija, montando las pantallas de verdad:
 *
 * - en **Reconciliar movimiento**, con un `*NNNN` que nadie conoce aparece la fila; guardar crea el
 *   destino con el número del mensaje y la propuesta pasa a «Transferencia a <nombre>»;
 * - con un destino que ya conoce ese número, **la fila no aparece** (ni se inventa una segunda);
 * - el nombre de quien mandó plata llega prellenado en Título Caso, no en mayúsculas, y se guarda
 *   como llave;
 * - la tarjeta de **Por revisar** y el **detalle de un movimiento** ofrecen la misma fila.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
// `sdk = [34]` y alta: mismo motivo que `ReconciliarSinDuplicarTest` (el texto con `lineHeight` mide
// cero en el SDK por defecto, y la columna perezosa tiene que componer todo).
@Config(sdk = [34], qualifiers = "w411dp-h2400dp-xhdpi")
class GuardarDesdeDondeApareceTest {

    @get:Rule val composeRule = createComposeRule()

    private val ahorros = Account("acc-banco", "Bancolombia Ahorros 8133", AccountType.SAVINGS, 2_000_000L)

    private val transferencia = SmsMessage(
        id = "sms-1",
        time = "2026-09-29 10:02",
        bank = "Bancolombia",
        text = "Bancolombia: Transferiste \$350.000 desde tu cuenta *8133 a la cuenta *31973270756 el 29/09/2026 a las 10:02.",
        state = SMS_STATE_PENDING,
        det = "Transferencia · \$350.000",
    )
    private val leida = ParsedSms(
        amount = 350_000.0,
        merchant = "Transferencia",
        type = TransactionType.EXPENSE,
        category = "Otros",
        identificadorDelDestino = "31973270756",
    )

    private val llego = SmsMessage(
        id = "sms-2",
        time = "2026-09-29 11:30",
        bank = "Notificación · Nu",
        text = "Te llegó dinero de CAROLINA RESTREPO SALAZAR con tu llave. Recibiste \$120.000.",
        state = SMS_STATE_PENDING,
        det = "CAROLINA RESTREPO SALAZAR · \$120.000",
    )
    private val llegoLeida = ParsedSms(
        amount = 120_000.0,
        merchant = "CAROLINA RESTREPO SALAZAR",
        type = TransactionType.INCOME,
        category = "Otros ingresos",
        identificadorDelDestino = "carolina restrepo salazar",
        identificadorEsLlave = true,
    )

    private val caro = DestinoConocido(id = "dst_caro", nombre = "Caro", numero = "31973270756", deQuien = "esposa")

    private val creados = mutableListOf<DestinoConocido>()
    private val actualizados = mutableListOf<DestinoConocido>()
    private val publicados = mutableListOf<FinancialEvent>()

    private open inner class Repo(
        private val sms: SmsMessage = transferencia,
        private val parseo: ParsedSms = leida,
        private val guardados: List<DestinoConocido> = emptyList(),
    ) : RepositorioDePrueba() {
        override suspend fun getAccounts(): List<Account> = listOf(ahorros)
        override suspend fun getSms(id: String): SmsMessage = sms
        override suspend fun parseSms(id: String): ParsedSms = parseo
        override suspend fun getSmsCoincidencias(id: String): List<FinancialEvent> = emptyList()
        override suspend fun getEvents(accountId: String?): List<FinancialEvent> = emptyList()
        override suspend fun postEvent(event: FinancialEvent): FinancialEvent = event.also { publicados += it }
        override suspend fun confirmSms(id: String) {}
        override suspend fun getDestinos(): List<DestinoConocido> = guardados
        override suspend fun createDestino(destino: DestinoConocido): DestinoConocido =
            destino.copy(id = "dst_nuevo").also { creados += it }
        override suspend fun updateDestino(id: String, destino: DestinoConocido): DestinoConocido =
            destino.also { actualizados += it }

        // Por revisar y la hoja del movimiento.
        override suspend fun getSmsMessages(): List<SmsMessage> = listOf(sms)
        override suspend fun getEventsByDay(): List<EventDay> = emptyList()
        override suspend fun getCardPaymentCandidates(): List<FinancialEvent> = emptyList()
        override suspend fun getUserProfile(): UserProfile =
            UserProfile(id = "u1", email = "juan@movi.test", name = "Juan", avatarColor = "#4F7CFF")
        override suspend fun getParecidos(id: String): List<FinancialEvent> = emptyList()
        override suspend fun getEventOccurrenceMark(id: String): EventOccurrenceMark? = null
    }

    @After
    fun limpiar() {
        Repositories.sustitutoDePrueba = null
    }

    // ── Reconciliar movimiento ──────────────────────────────────────────────────

    @Test
    fun `con un numero desconocido aparece la fila, guardar crea el destino y la propuesta cambia`() {
        reconciliar(Repo())

        esperar("¿De quién es la cuenta ·0756?")
        tocar(GUARDAR_COMO)
        // Primero los dos campos de la fila (nombre, de quién); el de «Comercio» va después.
        val campos = composeRule.onAllNodes(hasSetTextAction(), useUnmergedTree = true)
        campos[0].performTextReplacement("Caro")
        campos[1].performTextReplacement("esposa")
        composeRule.waitForIdle()
        tocar("Guardar cuenta")
        composeRule.waitUntil(timeoutMillis = 5_000) { creados.isNotEmpty() }
        composeRule.waitForIdle()

        val creado = creados.single()
        assertEquals("Caro", creado.nombre)
        assertEquals("31973270756", creado.numero, "el número es el del mensaje, sin que él lo escriba")
        assertEquals("esposa", creado.deQuien)
        assertNull(creado.llave)

        // La propuesta cambió, y la fila se fue: ya es una cuenta conocida.
        assertTrue(hay("Transferencia a Caro"), "la propuesta no tomó el nombre del destino")
        assertFalse(hayLaFila(), "la fila sigue ofreciendo guardar una cuenta que ya se guardó")

        tocar("Confirmar")
        assertEquals("Transferencia a Caro", publicados.single().description)
        // El texto del banco viaja con el movimiento: es lo que lo engancha a «Cuentas de otros».
        assertEquals(transferencia.text, publicados.single().rawPayload)
    }

    @Test
    fun `con un destino que ya conoce ese numero la fila no aparece`() {
        reconciliar(Repo(guardados = listOf(caro)))

        esperar("Confirma o ajusta".uppercase(), "Confirma o ajusta")
        assertFalse(hayLaFila())
        assertFalse(hay("¿De quién es"))
    }

    @Test
    fun `el numero de una cuenta suya no se ofrece como cuenta de otro`() {
        reconciliar(Repo(parseo = leida.copy(identificadorDelDestino = "8133")))

        esperar("Confirma o ajusta".uppercase(), "Confirma o ajusta")
        assertFalse(hayLaFila(), "ofreció guardar como ajena la cuenta ·8133, que es suya")
    }

    @Test
    fun `quien mando plata llega prellenado en Titulo Caso y se guarda como llave`() {
        reconciliar(Repo(sms = llego, parseo = llegoLeida))

        esperar("¿Guardar a Carolina Restrepo Salazar en tus cuentas de otros?")
        tocar(GUARDAR_COMO)
        // Prellenado, y no en mayúsculas: se guarda sin escribir nada.
        assertTrue(hayCampoCon("Carolina Restrepo Salazar"), "el nombre no llegó prellenado en Título Caso")
        tocar("Guardar cuenta")
        composeRule.waitUntil(timeoutMillis = 5_000) { creados.isNotEmpty() }
        composeRule.waitForIdle()

        val creado = creados.single()
        assertEquals("Carolina Restrepo Salazar", creado.nombre)
        assertEquals("", creado.numero, "un destino conocido solo por su llave va sin número")
        assertEquals("carolina restrepo salazar", creado.llave)
        assertTrue(hay("Transferencia de Carolina Restrepo Salazar"))
    }

    @Test
    fun `la llave de alguien que ya esta guardada con su numero se le suma, no crea otra`() {
        reconciliar(
            Repo(
                parseo = leida.copy(identificadorDelDestino = "0092184713", identificadorEsLlave = true),
                guardados = listOf(caro),
            ),
        )

        esperar("¿De quién es la llave 0092184713?")
        tocar(GUARDAR_COMO)
        composeRule.onAllNodes(hasSetTextAction(), useUnmergedTree = true)[0].performTextReplacement("caro")
        composeRule.waitForIdle()
        assertTrue(hay("Se agrega a «Caro», que ya tienes guardada."))
        tocar("Guardar cuenta")
        composeRule.waitUntil(timeoutMillis = 5_000) { actualizados.isNotEmpty() }

        assertTrue(creados.isEmpty(), "creó una segunda «Caro» en vez de sumarle la llave")
        val actualizado = actualizados.single()
        assertEquals("dst_caro", actualizado.id)
        assertEquals("31973270756", actualizado.numero)
        assertEquals("0092184713", actualizado.llave)
    }

    // ── Por revisar ─────────────────────────────────────────────────────────────

    @Test
    fun `la tarjeta de Por revisar ofrece guardar la cuenta, y con una conocida no`() {
        Repositories.sustitutoDePrueba = Repo()
        composeRule.setContent {
            MoviTheme { Box(Modifier.fillMaxSize()) { PorRevisarScreen(onNavigate = {}) } }
        }
        esperar("¿De quién es la cuenta ·0756?")
        tocar(GUARDAR_COMO)
        composeRule.onAllNodes(hasSetTextAction(), useUnmergedTree = true)[0].performTextReplacement("Caro")
        composeRule.waitForIdle()
        tocar("Guardar cuenta")
        composeRule.waitUntil(timeoutMillis = 5_000) { creados.isNotEmpty() }
        composeRule.waitForIdle()

        assertEquals("31973270756", creados.single().numero)
        assertFalse(hayLaFila())
    }

    @Test
    fun `en Por revisar una cuenta conocida no ofrece nada`() {
        Repositories.sustitutoDePrueba = Repo(guardados = listOf(caro))
        composeRule.setContent {
            MoviTheme { Box(Modifier.fillMaxSize()) { PorRevisarScreen(onNavigate = {}) } }
        }
        esperar("Revisar")
        composeRule.waitForIdle()
        assertFalse(hayLaFila())
    }

    // ── El detalle de un movimiento ─────────────────────────────────────────────

    private val yaAnotado = FinancialEvent(
        id = "ev-1",
        accountId = ahorros.id,
        type = TransactionType.EXPENSE,
        amount = 350_000L,
        category = "Otros",
        description = "Mercado",
        merchant = "Transferencia",
        source = EventSource.SMS,
        rawPayload = transferencia.text,
        timestamp = 1_790_000_000_000L,
    )

    @Test
    fun `el detalle de un movimiento con un numero desconocido ofrece la misma fila`() {
        hoja(Repo(), yaAnotado)

        esperar("¿De quién es la cuenta ·0756?")
        tocar(GUARDAR_COMO)
        composeRule.onAllNodes(hasSetTextAction(), useUnmergedTree = true)[0].performTextReplacement("Caro")
        composeRule.waitForIdle()
        tocar("Guardar cuenta")
        composeRule.waitUntil(timeoutMillis = 5_000) { creados.isNotEmpty() }
        composeRule.waitForIdle()

        assertEquals("31973270756", creados.single().numero)
        assertTrue(hay("«Caro» quedó en tus cuentas de otros"))
        assertFalse(hayLaFila())
    }

    @Test
    fun `el detalle de un movimiento a una cuenta conocida no ofrece nada`() {
        hoja(Repo(guardados = listOf(caro)), yaAnotado)

        esperar("Mercado")
        composeRule.waitForIdle()
        assertFalse(hayLaFila())
    }

    @Test
    fun `un movimiento que no nombra ninguna cuenta no lee los destinos`() {
        var lecturas = 0
        hoja(
            object : Repo() {
                override suspend fun getDestinos(): List<DestinoConocido> = emptyList<DestinoConocido>().also { lecturas++ }
            },
            yaAnotado.copy(rawPayload = null, merchant = "TOSTAO CAFE Y PAN"),
        )

        esperar("Mercado")
        composeRule.waitForIdle()
        assertEquals(0, lecturas)
        assertFalse(hayLaFila())
    }

    // ── Andamio ─────────────────────────────────────────────────────────────────

    private fun reconciliar(repo: RepositorioDePrueba) {
        Repositories.sustitutoDePrueba = repo
        composeRule.setContent {
            MoviTheme { Box(Modifier.fillMaxSize()) { SMSReconcileScreen(onNavigate = {}, smsId = "sms") } }
        }
        composeRule.waitForIdle()
    }

    private fun hoja(repo: RepositorioDePrueba, evento: FinancialEvent) {
        Repositories.sustitutoDePrueba = repo
        composeRule.setContent {
            MoviTheme {
                Box(Modifier.fillMaxSize()) {
                    HojaDelMovimiento(event = evento, cuentas = listOf(ahorros), onDismiss = {}, onCambiado = {})
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun hay(texto: String): Boolean =
        composeRule.onAllNodesWithText(texto, substring = true, useUnmergedTree = true)
            .fetchSemanticsNodes().isNotEmpty()

    private fun hayCampoCon(texto: String): Boolean =
        composeRule.onAllNodes(hasSetTextAction() and hasText(texto), useUnmergedTree = true)
            .fetchSemanticsNodes().isNotEmpty()

    private fun hayLaFila(): Boolean =
        composeRule.onAllNodesWithTag(TAG_GUARDAR_EL_DESTINO, useUnmergedTree = true)
            .fetchSemanticsNodes().isNotEmpty()

    /** Espera a que aparezca cualquiera de [textos] (un encabezado puede ir en versales). */
    private fun esperar(vararg textos: String) {
        composeRule.waitUntil(timeoutMillis = 5_000) { textos.any { hay(it) } }
        composeRule.waitForIdle()
    }

    /** Por la acción semántica: bajo Robolectric un toque por coordenadas no llega a una hoja alta. */
    private fun tocar(texto: String) {
        val nodos = composeRule.onAllNodes(
            hasClickAction() and (hasText(texto) or hasAnyChild(hasText(texto))),
            useUnmergedTree = true,
        )
        nodos[0].performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
    }
}
