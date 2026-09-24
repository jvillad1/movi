package com.jvillada.movi.ui.porrevisar

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasAnyChild
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.RepositorioDePrueba
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.EventDay
import com.jvillada.movi.shared.model.EventOccurrenceMark
import com.jvillada.movi.shared.model.EventSource
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.ReconciliationStatus
import com.jvillada.movi.shared.model.SMS_STATE_CONFIRMED
import com.jvillada.movi.shared.model.SMS_STATE_PENDING
import com.jvillada.movi.shared.model.SmsMessage
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.UserProfile
import com.jvillada.movi.theme.MoviTheme
import com.jvillada.movi.ui.Screen
import kotlinx.coroutines.CompletableDeferred
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * # «Por revisar», montada de verdad (ola C, tarea 5)
 *
 * La bandeja junta tres fuentes que antes vivían en dos pantallas. Lo que se fija acá:
 *
 * - con las tres fuentes con algo, las tres secciones están y cada una lleva a su acción de siempre;
 * - con ninguna, «Todo al día» — pero **solo después de que las tres lecturas contestaron**: antes
 *   hay un esqueleto, y una lectura caída dice que no se pudo leer en vez de dar el vacío por bueno;
 * - la configuración de la captura no vive acá: solo un renglón, y solo si la captura dejó de andar.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h1200dp-xhdpi")
class PorRevisarEnPantallaTest {

    @get:Rule val composeRule = createComposeRule()

    private val banco = Account("acc-banco", "Bancolombia Ahorros", AccountType.SAVINGS, 853_037L, "COP")

    private fun evento(id: String, descripcion: String, estado: ReconciliationStatus) = FinancialEvent(
        id = id,
        accountId = banco.id,
        type = TransactionType.EXPENSE,
        amount = 18_500L,
        category = "Comida",
        description = descripcion,
        timestamp = 1_757_000_000_000L,
        source = EventSource.SMS,
        reconciliationStatus = estado,
        countsAsCashFlow = true,
    )

    private val porConfirmar = evento("e-solo", "Compra Exito", ReconciliationStatus.UNCONFIRMED)
    private val aMano = evento("e-mano", "Carnes y Legumbres Santa Elena", ReconciliationStatus.RECONCILED)
    private val pagoNu = evento("e-pago", "Pago Nu", ReconciliationStatus.RECONCILED)

    private fun sms(id: String, estado: String, det: String = "Uber") = SmsMessage(
        id = id, time = "2026-09-03 07:15", bank = "Bancolombia",
        text = "Compra aprobada \$28.500 en Uber BV.", state = estado, det = det,
    )

    private val navegaciones = mutableListOf<Screen>()
    private var confirmado: String? = null

    private open inner class Repo(
        private val mensajes: List<SmsMessage> = emptyList(),
        private val eventos: List<FinancialEvent> = emptyList(),
        private val candidatos: List<FinancialEvent> = emptyList(),
        private val silenciada: Boolean = false,
    ) : RepositorioDePrueba() {
        override suspend fun getSmsMessages(): List<SmsMessage> = mensajes
        override suspend fun getEventsByDay(): List<EventDay> =
            listOf(EventDay(date = "2025-09-08", total = 0L, items = eventos))
        override suspend fun getCardPaymentCandidates(): List<FinancialEvent> = candidatos
        override suspend fun getAccounts(): List<Account> = listOf(banco)
        override suspend fun getUserProfile(): UserProfile = UserProfile(
            id = "u1", email = "juan@movi.test", name = "Juan", avatarColor = "#4F7CFF", smsAlertMuted = silenciada,
        )
        override suspend fun confirmEvent(id: String): FinancialEvent {
            confirmado = id
            return porConfirmar.copy(reconciliationStatus = ReconciliationStatus.RECONCILED)
        }
        override suspend fun getEventOccurrenceMark(id: String): EventOccurrenceMark? = null
    }

    private fun montar(repo: RepositorioDePrueba) {
        Repositories.sustitutoDePrueba = repo
        composeRule.setContent {
            MoviTheme { Box(Modifier.fillMaxSize()) { PorRevisarScreen(onNavigate = { navegaciones += it }) } }
        }
    }

    @After
    fun limpiar() {
        Repositories.sustitutoDePrueba = null
    }

    private fun esperarTexto(texto: String) {
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText(texto, substring = true, useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.waitForIdle()
    }

    private fun hay(texto: String): Boolean =
        composeRule.onAllNodesWithText(texto, substring = true, useUnmergedTree = true)
            .fetchSemanticsNodes().isNotEmpty()

    // ── Las tres fuentes ─────────────────────────────────────────────────────────

    @Test
    fun `con las tres fuentes, las tres secciones estan`() {
        montar(
            Repo(
                mensajes = listOf(sms("s1", SMS_STATE_PENDING, det = "Uber"), sms("s2", SMS_STATE_CONFIRMED, det = "Rappi")),
                eventos = listOf(aMano, porConfirmar),
                candidatos = listOf(pagoNu),
            ),
        )
        esperarTexto("ENTRARON SOLOS")

        assertTrue(hay("MENSAJES DEL BANCO"))
        assertTrue("el pendiente está", hay("Uber"))
        assertTrue("el ya confirmado es historial, no va acá", !hay("Rappi"))
        assertTrue("lo que entró solo está", hay("Compra Exito"))
        assertTrue("lo anotado a mano no", !hay("Carnes y Legumbres"))
        assertTrue(hay("PAGOS DE TARJETA"))
        assertTrue(hay("1 pago de tarjeta sin marcar"))
        assertTrue(!hay(TODO_AL_DIA))
    }

    @Test
    fun `revisar un mensaje abre su detalle, como siempre`() {
        montar(Repo(mensajes = listOf(sms("s1", SMS_STATE_PENDING))))
        esperarTexto("Revisar")

        composeRule.onNodeWithText("Revisar", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()
        assertEquals(listOf<Screen>(Screen.SMSReconcile("s1")), navegaciones)
    }

    /** Lo que entró solo se confirma con la misma hoja de Movimientos, que es donde vive «Confirmar». */
    @Test
    fun `un movimiento que entro solo se confirma desde la bandeja`() {
        montar(Repo(eventos = listOf(porConfirmar)))
        esperarTexto("Compra Exito")

        composeRule.onNodeWithText("Compra Exito", useUnmergedTree = true).performClick()
        esperarTexto("POR CONFIRMAR")
        composeRule.onNode(hasClickAction() and hasAnyChild(hasText("Confirmar")), useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitUntil(5_000) { confirmado != null }
        assertEquals("e-solo", confirmado)
    }

    /**
     * Confirmado desde la hoja, el movimiento sale de la bandeja **aunque la relectura falle**: si
     * se quedara, el dueño lo vería todavía pendiente y creería que «Confirmar» no se guardó — lo
     * mismo que ya se hacía con los pagos de tarjeta resueltos.
     */
    @Test
    fun `confirmado, sale de la bandeja aunque la relectura falle`() {
        montar(object : Repo(eventos = listOf(porConfirmar)) {
            override suspend fun getEventsByDay(): List<EventDay> =
                if (confirmado != null) error("sin señal") else super.getEventsByDay()
        })
        esperarTexto("Compra Exito")

        composeRule.onNodeWithText("Compra Exito", useUnmergedTree = true).performClick()
        esperarTexto("POR CONFIRMAR")
        composeRule.onNode(hasClickAction() and hasAnyChild(hasText("Confirmar")), useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitUntil(5_000) { confirmado != null }
        composeRule.waitUntil(5_000) { !hay("POR CONFIRMAR") }
        composeRule.waitForIdle()

        assertTrue("ya no está pendiente", !hay("Compra Exito"))
        assertTrue(!hay("ENTRARON SOLOS"))
    }

    @Test
    fun `los pagos de tarjeta se revisan uno por uno en su hoja`() {
        montar(Repo(candidatos = listOf(pagoNu)))
        esperarTexto("1 pago de tarjeta sin marcar")

        composeRule.onNodeWithText("1 pago de tarjeta sin marcar", useUnmergedTree = true).performClick()
        esperarTexto("PAGOS DE TARJETA SIN MARCAR")
        assertTrue(hay("Pago Nu"))
    }

    // ── Ninguna ──────────────────────────────────────────────────────────────────

    @Test
    fun `sin nada en ninguna fuente, Todo al dia`() {
        montar(Repo(mensajes = listOf(sms("s1", SMS_STATE_CONFIRMED)), eventos = listOf(aMano)))
        esperarTexto(TODO_AL_DIA)

        assertTrue(!hay("MENSAJES DEL BANCO"))
        assertTrue(!hay("ENTRARON SOLOS"))
        assertTrue(!hay("PAGOS DE TARJETA"))
        composeRule.onNodeWithTag(TAG_ESQUELETO_DE_POR_REVISAR, useUnmergedTree = true).assertDoesNotExist()
    }

    // ── Nada se afirma antes de leer ─────────────────────────────────────────────

    /**
     * Con las lecturas en vuelo, el esqueleto y nada más: ni «Todo al día» ni ninguna sección. Y la
     * acción del encabezado ya está desde el primer cuadro.
     */
    @Test
    fun `mientras nada contesto, esqueleto y ningun vacio`() {
        val nunca = CompletableDeferred<Unit>()
        montar(object : Repo() {
            override suspend fun getSmsMessages(): List<SmsMessage> { nunca.await(); return emptyList() }
            override suspend fun getEventsByDay(): List<EventDay> { nunca.await(); return emptyList() }
            override suspend fun getCardPaymentCandidates(): List<FinancialEvent> { nunca.await(); return emptyList() }
        })
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(TAG_ESQUELETO_DE_POR_REVISAR, useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText(TITULO_DE_POR_REVISAR, useUnmergedTree = true).assertExists()
        composeRule.onNodeWithContentDescription("Actualizar", useUnmergedTree = true).assertExists()
        assertTrue(!hay(TODO_AL_DIA))
        assertTrue(!hay("No pudimos"))
    }

    /** Con dos fuentes vacías y una en vuelo, todavía no se sabe: sigue el esqueleto. */
    @Test
    fun `una sola fuente en vuelo alcanza para no decir Todo al dia`() {
        val nunca = CompletableDeferred<Unit>()
        montar(object : Repo() {
            override suspend fun getCardPaymentCandidates(): List<FinancialEvent> { nunca.await(); return emptyList() }
        })
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(TAG_ESQUELETO_DE_POR_REVISAR, useUnmergedTree = true).assertExists()
        assertTrue(!hay(TODO_AL_DIA))
    }

    /** Una lectura caída no es «no hay nada»: se dice en su lugar, con Reintentar, y sin Todo al día. */
    @Test
    fun `una fuente caida lo dice y no da el vacio por bueno`() {
        var hayRed = false
        montar(object : Repo() {
            override suspend fun getSmsMessages(): List<SmsMessage> =
                if (hayRed) listOf(sms("s1", SMS_STATE_PENDING, det = "Uber")) else error("sin señal")
        })
        esperarTexto("No pudimos cargar tus mensajes del banco")
        assertTrue(!hay(TODO_AL_DIA))

        hayRed = true
        composeRule.onNodeWithText("Reintentar", useUnmergedTree = true).performClick()
        esperarTexto("MENSAJES DEL BANCO")
        assertTrue(hay("Uber"))
    }

    // ── La captura ───────────────────────────────────────────────────────────────

    /** La configuración vive en Ajustes: acá no está la sección de permisos. */
    @Test
    fun `la configuracion de la captura no vive en la bandeja`() {
        montar(Repo(mensajes = listOf(sms("s1", SMS_STATE_PENDING))))
        esperarTexto("MENSAJES DEL BANCO")

        assertTrue(!hay("CAPTURA EN ESTE TELÉFONO"))
        composeRule.onNodeWithTag(TAG_AVISO_DE_CAPTURA_EN_LA_BANDEJA, useUnmergedTree = true).assertDoesNotExist()
    }

    /** Si nunca llegó nada —la misma condición que el Hoy—, un renglón lleva a «Captura del banco». */
    @Test
    fun `si la captura nunca entrego nada, un renglon lleva a Captura del banco`() {
        montar(Repo())
        esperarTexto("Movi nunca ha recibido un mensaje de tu banco")

        composeRule.onNodeWithTag(TAG_AVISO_DE_CAPTURA_EN_LA_BANDEJA, useUnmergedTree = true).performClick()
        composeRule.waitForIdle()
        assertEquals(listOf<Screen>(Screen.CapturaDelBanco), navegaciones)
    }

    /** Y si el dueño lo silenció en Captura del banco, acá tampoco aparece: la misma regla que el Hoy. */
    @Test
    fun `silenciado, el renglon de la captura no aparece`() {
        montar(Repo(silenciada = true))
        esperarTexto(TODO_AL_DIA)

        assertTrue(!hay("Movi nunca ha recibido"))
    }
}
