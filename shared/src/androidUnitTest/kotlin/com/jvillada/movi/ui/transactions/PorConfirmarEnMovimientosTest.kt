package com.jvillada.movi.ui.transactions

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import com.jvillada.movi.ui.LocalRefreshTick
import kotlinx.coroutines.CompletableDeferred
import com.jvillada.movi.data.DiasPlegadosStore
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.RepositorioDePrueba
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.EventDay
import com.jvillada.movi.shared.model.EventSource
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.ReconciliationStatus
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.SMS_STATE_CONFIRMED
import com.jvillada.movi.shared.model.SMS_STATE_PENDING
import com.jvillada.movi.shared.model.SmsMessage
import com.jvillada.movi.ui.Screen
import com.jvillada.movi.ui.porrevisar.TAG_RENGLON_POR_REVISAR
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import androidx.compose.ui.test.onNodeWithTag
import com.jvillada.movi.shared.time.epochMillisToAppDate
import com.jvillada.movi.theme.MoviTheme
import kotlinx.datetime.Clock
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * # Un solo renglón «N por revisar» arriba de la lista, montado de verdad
 *
 * El dueño: *«Por confirmar debería saltar en otro lugar no acá en esta misma vista»*. Primero fue
 * un aviso arriba de la lista; en la ola C (tarea 5) ese aviso, el de los pagos de tarjeta sin
 * marcar y los mensajes del banco por confirmar se juntaron en **una sola bandeja**, «Por
 * revisar», y acá queda un solo renglón con la suma que la abre.
 *
 * Se monta la pantalla con repositorios distintos —con pendientes en las tres fuentes y sin
 * ninguno— porque el punto del renglón es que en el segundo caso **no ocupe espacio**, y eso no se
 * puede afirmar con un solo montaje.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = AVD_POR_CONFIRMAR)
class PorConfirmarEnMovimientosTest {

    @get:Rule val composeRule = createComposeRule()

    private val banco = Account("acc-banco", "Bancolombia Ahorros", AccountType.SAVINGS, 853_037L, "COP")

    private fun evento(id: String, descripcion: String, estado: ReconciliationStatus, fuente: EventSource) =
        FinancialEvent(
            id = id,
            accountId = banco.id,
            type = TransactionType.EXPENSE,
            amount = 18_500L,
            category = "Comida",
            description = descripcion,
            timestamp = Clock.System.now().toEpochMilliseconds(),
            source = fuente,
            reconciliationStatus = estado,
            countsAsCashFlow = true,
        )

    private val aMano = evento("e-mano", "Carnes y Legumbres Santa Elena", ReconciliationStatus.RECONCILED, EventSource.MANUAL)
    private val porSms = evento("e-sms", "Compra Exito", ReconciliationStatus.UNCONFIRMED, EventSource.SMS)
    private val pagoDeTarjeta = evento("e-pago", "Pago Nu", ReconciliationStatus.RECONCILED, EventSource.MANUAL)

    private fun sms(id: String, estado: String) = SmsMessage(
        id = id, time = "2026-09-03 07:15", bank = "Bancolombia",
        text = "Compra aprobada \$28.500 en Uber BV.", state = estado, det = "Uber",
    )

    private val navegaciones = mutableListOf<Screen>()

    private fun montar(
        items: List<FinancialEvent>,
        esperar: String,
        mensajes: List<SmsMessage> = emptyList(),
        candidatos: List<FinancialEvent> = emptyList(),
    ) {
        DiasPlegadosStore.clear()
        Repositories.sustitutoDePrueba = object : RepositorioDePrueba() {
            override suspend fun getAccounts(): List<Account> = listOf(banco)
            override suspend fun getEventsByDay(): List<EventDay> =
                listOf(EventDay(date = HOY_ISO, total = -18_500L, items = items))
            override suspend fun getCardPaymentCandidates(): List<FinancialEvent> = candidatos
            override suspend fun getSmsMessages(): List<SmsMessage> = mensajes
        }
        composeRule.setContent {
            MoviTheme {
                Box(Modifier.fillMaxSize()) { TransactionsScreen(onNavigate = { navegaciones += it }) }
            }
        }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText(esperar, substring = true, useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.waitForIdle()
    }

    @After
    fun limpiar() {
        Repositories.sustitutoDePrueba = null
        DiasPlegadosStore.clear()
    }

    @Test
    fun `el chip ya no esta en la fila de filtros`() {
        montar(listOf(aMano), esperar = "Carnes y Legumbres")

        // Los que sí son formas de mirar los movimientos siguen ahí.
        composeRule.onNodeWithText("Todo", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("Gastos", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("Ingresos", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("Por confirmar", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun `sin nada pendiente en ninguna fuente el renglon no ocupa espacio`() {
        // El caso normal de quien anota todo a mano, y el punto entero del renglón.
        //
        // Se espera a que las dos lecturas propias del renglón contesten: afirmar «no está» con
        // ellas todavía en vuelo pasaría igual aunque el renglón apareciera un cuadro después.
        var contestaronMensajes = false
        var contestaronCandidatos = false
        DiasPlegadosStore.clear()
        Repositories.sustitutoDePrueba = object : RepositorioDePrueba() {
            override suspend fun getAccounts(): List<Account> = listOf(banco)
            override suspend fun getEventsByDay(): List<EventDay> =
                listOf(EventDay(date = HOY_ISO, total = -18_500L, items = listOf(aMano)))
            override suspend fun getCardPaymentCandidates(): List<FinancialEvent> =
                emptyList<FinancialEvent>().also { contestaronCandidatos = true }
            override suspend fun getSmsMessages(): List<SmsMessage> =
                listOf(sms("s1", SMS_STATE_CONFIRMED)).also { contestaronMensajes = true }
        }
        composeRule.setContent {
            MoviTheme { Box(Modifier.fillMaxSize()) { TransactionsScreen(onNavigate = {}) } }
        }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            contestaronMensajes && contestaronCandidatos &&
                composeRule.onAllNodesWithText("Carnes y Legumbres", substring = true, useUnmergedTree = true)
                    .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.waitForIdle()
        assertTrue(contestaronMensajes && contestaronCandidatos)

        composeRule.onNodeWithTag(TAG_RENGLON_POR_REVISAR, useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNodeWithText("por revisar", substring = true, useUnmergedTree = true).assertDoesNotExist()
    }

    /**
     * Un movimiento que entró solo, dos mensajes del banco por confirmar y un pago de tarjeta sin
     * marcar son **un** renglón con la suma, no tres avisos. Y los avisos viejos ya no están.
     */
    @Test
    fun `el renglon suma las tres fuentes y abre la bandeja`() {
        montar(
            listOf(aMano, porSms),
            esperar = "por revisar",
            mensajes = listOf(sms("s1", SMS_STATE_PENDING), sms("s2", SMS_STATE_PENDING), sms("s3", SMS_STATE_CONFIRMED)),
            candidatos = listOf(pagoDeTarjeta),
        )

        composeRule.onNodeWithText("4 por revisar", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("entró solo", substring = true, useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNodeWithText("sin marcar", substring = true, useUnmergedTree = true).assertDoesNotExist()

        composeRule.onNodeWithText("4 por revisar", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()
        assertEquals(listOf<Screen>(Screen.PorRevisar), navegaciones)
    }

    /**
     * **Una recarga no lo saca de la pantalla.** Cada guardado desde la hoja de Agregar y cada
     * «Reintentar» vuelven a leer todo; si el renglón se desmontara mientras tanto, la lista
     * saltaría ~56 dp hacia arriba y volvería a bajar. Con las lecturas de la recarga detenidas a
     * propósito, el renglón sigue ahí con el último número que se leyó.
     */
    @Test
    fun `una recarga no saca el renglon mientras lee`() {
        val tick = mutableStateOf(0)
        var compuerta: CompletableDeferred<Unit>? = null
        DiasPlegadosStore.clear()
        Repositories.sustitutoDePrueba = object : RepositorioDePrueba() {
            override suspend fun getAccounts(): List<Account> = listOf(banco)
            override suspend fun getEventsByDay(): List<EventDay> {
                compuerta?.await()
                return listOf(EventDay(date = HOY_ISO, total = -18_500L, items = listOf(aMano, porSms)))
            }
            override suspend fun getCardPaymentCandidates(): List<FinancialEvent> {
                compuerta?.await()
                return emptyList()
            }
            override suspend fun getSmsMessages(): List<SmsMessage> {
                compuerta?.await()
                return listOf(sms("s1", SMS_STATE_PENDING))
            }
        }
        composeRule.setContent {
            MoviTheme {
                CompositionLocalProvider(LocalRefreshTick provides tick.value) {
                    Box(Modifier.fillMaxSize()) { TransactionsScreen(onNavigate = {}) }
                }
            }
        }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("2 por revisar", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }

        // La recarga: todas las lecturas quedan en vuelo hasta abrir la compuerta.
        val cerrada = CompletableDeferred<Unit>()
        compuerta = cerrada
        tick.value++
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(TAG_RENGLON_POR_REVISAR, useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText("2 por revisar", useUnmergedTree = true).assertExists()

        cerrada.complete(Unit)
        composeRule.waitForIdle()
        composeRule.onNodeWithText("2 por revisar", useUnmergedTree = true).assertExists()
    }

    /** Si solo hay mensajes del banco, el renglón igual está: la bandeja es una sola. */
    @Test
    fun `con solo mensajes del banco el renglon tambien aparece`() {
        montar(listOf(aMano), esperar = "por revisar", mensajes = listOf(sms("s1", SMS_STATE_PENDING)))

        composeRule.onNodeWithText("1 por revisar", useUnmergedTree = true).assertIsDisplayed()
    }

    /**
     * Una fuente que no contestó no cuenta, y no se inventa: con los mensajes del banco caídos, el
     * renglón dice lo que sí se leyó.
     */
    @Test
    fun `una fuente caida no suma ni tapa a las otras`() {
        DiasPlegadosStore.clear()
        Repositories.sustitutoDePrueba = object : RepositorioDePrueba() {
            override suspend fun getAccounts(): List<Account> = listOf(banco)
            override suspend fun getEventsByDay(): List<EventDay> =
                listOf(EventDay(date = HOY_ISO, total = -18_500L, items = listOf(aMano, porSms)))
            override suspend fun getCardPaymentCandidates(): List<FinancialEvent> = emptyList()
            override suspend fun getSmsMessages(): List<SmsMessage> = error("sin señal")
        }
        composeRule.setContent {
            MoviTheme { Box(Modifier.fillMaxSize()) { TransactionsScreen(onNavigate = {}) } }
        }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("por revisar", substring = true, useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("1 por revisar", useUnmergedTree = true).assertIsDisplayed()
    }
}

/** El mismo tamaño de pantalla que usan las otras pruebas de esta carpeta (privado por archivo). */
private const val AVD_POR_CONFIRMAR = "w411dp-h731dp-xhdpi"

/**
 * **Hoy, en la zona de la app.** Los fixtures de esta clase tienen que caer adentro del período
 * que Movimientos muestra al abrirse (ver `diasDelPeriodo`), así que la fecha sale del reloj en vez
 * de ser una constante vieja. El encabezado del día queda en «HOY», que es igual de estable que una
 * fecha fija y además no depende del corte que tenga configurado el usuario de prueba.
 */
private val HOY_ISO: String = epochMillisToAppDate(Clock.System.now().toEpochMilliseconds()).toString()
