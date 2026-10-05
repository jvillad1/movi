package com.jvillada.movi.ui.destinos

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasAnyChild
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import com.jvillada.movi.data.RecurringOfferGate
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.RepositorioDePrueba
import com.jvillada.movi.data.RepositorioDePruebaDeMovimientos
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.DestinoConocido
import com.jvillada.movi.shared.model.DestinosDescartados
import com.jvillada.movi.shared.model.EventDay
import com.jvillada.movi.shared.model.EventOccurrenceMark
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.IdentificadorDelDestino
import com.jvillada.movi.shared.model.MovimientosDelDestino
import com.jvillada.movi.shared.model.MovimientosParaRenombrar
import com.jvillada.movi.shared.model.ReconciliationStatus
import com.jvillada.movi.shared.model.TipoDeIdentificador
import com.jvillada.movi.shared.model.TipoDeTercero
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.UserProfile
import com.jvillada.movi.shared.model.conIdentificadores
import com.jvillada.movi.shared.repository.ApiException
import com.jvillada.movi.theme.MoviTheme
import com.jvillada.movi.ui.Screen
import com.jvillada.movi.ui.components.formatMoney
import com.jvillada.movi.ui.transactions.TransactionsScreen
import kotlinx.datetime.Clock
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
 * # «Personas y comercios», perfecto, en pantalla (4-oct-2026)
 *
 * Lo que pidió el dueño, montado de verdad: **el acceso principal en Movimientos** (un toque a la
 * lista), **la ficha con lo enviado y lo recibido por período** y cada movimiento que se abre,
 * **editar** con todo lo que lo reconoce, **unir** dos fichas y **eliminar** sin tocar movimientos.
 * Números y nombres sintéticos.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h2400dp-xhdpi")
class PersonasYComerciosEnPantallaTest {

    @get:Rule val composeRule = createComposeRule()

    private val ahorros = Account("acc-banco", "Ahorros 9999", AccountType.SAVINGS, 2_000_000L)
    private val ahora = Clock.System.now().toEpochMilliseconds()
    private val dia = 86_400_000L

    private val ana = DestinoConocido(
        id = "dst_ana", nombre = "Ana", numero = "", deQuien = "esposa",
        tipo = TipoDeTercero.PERSONA, tipoInferido = true,
        totalesDelPeriodo = mapOf("COP" to 1_500_000L), recibidosDelPeriodo = mapOf("COP" to 400_000L),
    ).conIdentificadores(listOf(IdentificadorDelDestino(TipoDeIdentificador.NUMERO, "55500000756")))
    private val repetida = DestinoConocido(id = "dst_rep", nombre = "Ana Prueba Salazar", numero = "", llave = "ana prueba salazar")
    private val hernan = DestinoConocido(
        id = "dst_hernan", nombre = "Hernán", numero = "00800000404",
        totalesDelPeriodo = mapOf("COP" to 6_170_560L),
    )

    private fun ev(id: String, tipo: TransactionType, monto: Long, nombre: String, cuando: Long) = FinancialEvent(
        id = id, accountId = ahorros.id, type = tipo, amount = monto, category = "Otros", description = nombre,
        timestamp = cuando, reconciliationStatus = ReconciliationStatus.RECONCILED,
    )

    private val enviadoHoy = ev("ev-hoy", TransactionType.EXPENSE, 1_500_000L, "Transferencia a Ana", ahora - 60_000L)
    private val enviadoAntes = ev("ev-antes", TransactionType.EXPENSE, 2_000_000L, "Mercado de la casa", ahora - 45 * dia)
    private val recibidoHoy = ev("ev-recibido", TransactionType.INCOME, 400_000L, "Transferencia de Ana", ahora - 120_000L)

    private val actualizados = mutableListOf<DestinoConocido>()
    private val unidos = mutableListOf<Pair<String, String>>()
    private val borrados = mutableListOf<String>()

    private open inner class Repo(
        private val guardados: List<DestinoConocido> = listOf(ana, hernan, repetida),
    ) : RepositorioDePruebaDeMovimientos() {
        override suspend fun getAccounts(): List<Account> = listOf(ahorros)
        override suspend fun getUserProfile(): UserProfile =
            UserProfile(id = "u1", email = "juan@movi.test", name = "Juan", avatarColor = "#4F7CFF", periodCutoffDay = 25)
        override suspend fun getDestinos(): List<DestinoConocido> = guardados
        override suspend fun getDestinosSugeridos() = emptyList<com.jvillada.movi.shared.model.DestinoSugerido>()
        override suspend fun getDestinosDescartados(): DestinosDescartados = DestinosDescartados()
        override suspend fun getMovimientosDelDestino(id: String): MovimientosDelDestino {
            val d = guardados.first { it.id == id }
            return if (id == ana.id) {
                MovimientosDelDestino(destino = d, movimientos = listOf(enviadoHoy, enviadoAntes), recibidos = listOf(recibidoHoy))
            } else {
                MovimientosDelDestino(destino = d, movimientos = emptyList())
            }
        }
        override suspend fun getRenombrablesDelDestino(id: String) = MovimientosParaRenombrar("", "", emptyList())
        override suspend fun updateDestino(id: String, destino: DestinoConocido): DestinoConocido = destino.also { actualizados += it }
        override suspend fun unirDestinos(id: String, con: String): DestinoConocido {
            unidos += id to con
            return guardados.first { it.id == con }
        }
        override suspend fun deleteDestino(id: String) { borrados += id }

        // Movimientos y la hoja de un movimiento.
        override suspend fun getEventsByDay(): List<EventDay> = emptyList()
        override suspend fun getCardPaymentCandidates(): List<FinancialEvent> = emptyList()
        override suspend fun getSmsMessages() = emptyList<com.jvillada.movi.shared.model.SmsMessage>()
        override suspend fun getParecidos(id: String): List<FinancialEvent> = emptyList()
        override suspend fun getEventOccurrenceMark(id: String): EventOccurrenceMark? = null
    }

    @After
    fun limpiar() {
        Repositories.sustitutoDePrueba = null
        RecurringOfferGate.clear()
    }

    // ── El acceso principal ─────────────────────────────────────────────────────

    @Test
    fun `Movimientos tiene el acceso a Personas y comercios con los nombres, y un toque abre la lista`() {
        var navegoA: Screen? = null
        Repositories.sustitutoDePrueba = Repo()
        composeRule.setContent {
            MoviTheme { Box(Modifier.fillMaxSize()) { TransactionsScreen(onNavigate = { navegoA = it }) } }
        }
        // Primero a quien más se le mandó este período.
        esperar("Hernán, Ana y Ana Prueba Salazar")
        composeRule.onNodeWithTag(TAG_ACCESO_A_PERSONAS_Y_COMERCIOS, useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
        assertEquals(Screen.Destinos(), navegoA)
    }

    @Test
    fun `el acceso esta aunque la lectura falle, e invita a guardar`() {
        Repositories.sustitutoDePrueba = object : Repo() {
            override suspend fun getDestinos(): List<DestinoConocido> = throw ApiException(503, "sin señal")
        }
        composeRule.setContent {
            MoviTheme { Box(Modifier.fillMaxSize()) { TransactionsScreen(onNavigate = {}) } }
        }
        esperar("Guarda a quién le envías plata")
        assertEquals(1, composeRule.onAllNodesWithTag(TAG_ACCESO_A_PERSONAS_Y_COMERCIOS, useUnmergedTree = true).fetchSemanticsNodes().size)
    }

    // ── La ficha ────────────────────────────────────────────────────────────────

    @Test
    fun `llegar con una ficha pedida la abre sola`() {
        montar(abrir = ana.id)
        esperar("ESTE PERÍODO")
        assertTrue(hay("Persona · ·0756 · esposa"))
    }

    @Test
    fun `la ficha dice lo enviado y lo recibido este periodo y periodo por periodo`() {
        montar(abrir = ana.id)
        esperar("POR PERÍODO")
        composeRule.onNodeWithTag(TAG_ESTE_PERIODO_DEL_TERCERO, useUnmergedTree = true).assertExists()
        assertTrue(hay("Le enviaste"))
        assertTrue(hay("Te envió"))
        // 1.500.000 este período (en «Este período» y en su fila de la tabla), 2.000.000 el anterior.
        assertTrue(composeRule.onAllNodesWithText(formatMoney(1_500_000L, "COP"), useUnmergedTree = true).fetchSemanticsNodes().size >= 2)
        assertTrue(hay(formatMoney(2_000_000L, "COP")))
        assertTrue(composeRule.onAllNodesWithText(formatMoney(400_000L, "COP"), substring = true, useUnmergedTree = true).fetchSemanticsNodes().size >= 2)
    }

    @Test
    fun `un movimiento de la ficha se abre con un toque`() {
        montar(abrir = ana.id)
        esperar("Mercado de la casa")
        composeRule.onNodeWithTag(TAG_MOVIMIENTO_DEL_TERCERO + ":" + enviadoAntes.id, useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
        esperar("MONTO, CUENTA Y CONCEPTO")
    }

    // ── Editar, unir, eliminar ──────────────────────────────────────────────────

    @Test
    fun `editar muestra como lo reconoce Movi y no guarda como elegido el tipo deducido`() {
        montar(abrir = ana.id)
        esperar("ESTE PERÍODO")
        composeRule.onNodeWithTag(TAG_EDITAR_EL_TERCERO, useUnmergedTree = true).performSemanticsAction(SemanticsActions.OnClick)
        esperar("CÓMO LO RECONOCE MOVI")
        assertTrue(hay(AGREGAR_UN_IDENTIFICADOR))
        assertTrue(hay("Número de cuenta"))

        composeRule.onAllNodes(hasSetTextAction(), useUnmergedTree = true)[0].performTextReplacement("Ana María")
        composeRule.waitForIdle()
        tocar("Guardar cambios")
        composeRule.waitUntil(timeoutMillis = 5_000) { actualizados.isNotEmpty() }
        assertEquals("Ana María", actualizados.single().nombre)
        assertNull(actualizados.single().tipo, "lo dedujo Movi y nadie lo tocó: no se guarda como elegido")
        assertEquals("esposa", actualizados.single().deQuien)
    }

    @Test
    fun `unir pide con cual y confirma antes, y no crea nada`() {
        montar(abrir = repetida.id)
        esperar("ESTE PERÍODO")
        composeRule.onNodeWithTag(TAG_EDITAR_EL_TERCERO, useUnmergedTree = true).performSemanticsAction(SemanticsActions.OnClick)
        esperar(ES_LA_MISMA_QUE_OTRA)
        tocar(ES_LA_MISMA_QUE_OTRA)
        esperar("¿Con cuál la unes?")
        composeRule.onAllNodes(
            hasClickAction() and hasAnyChild(hasText("Ana")) and hasAnyAncestor(hasTestTag(TAG_UNIR_CON)),
            useUnmergedTree = true,
        ).onFirst().performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
        esperar("¿Unir «Ana Prueba Salazar» con «Ana»?")
        assertTrue(hay("Tus movimientos no se tocan"))
        assertTrue(unidos.isEmpty(), "nada se une sin confirmar")
        tocar("Unir")
        composeRule.waitUntil(timeoutMillis = 5_000) { unidos.isNotEmpty() }
        assertEquals(repetida.id to ana.id, unidos.single())
    }

    @Test
    fun `eliminar pregunta antes y dice que los movimientos no se tocan`() {
        montar(abrir = hernan.id)
        esperar("ESTE PERÍODO")
        composeRule.onNodeWithTag(TAG_EDITAR_EL_TERCERO, useUnmergedTree = true).performSemanticsAction(SemanticsActions.OnClick)
        esperar("Eliminar")
        tocar("Eliminar")
        esperar("¿Eliminar «Hernán»?")
        assertTrue(hay("Tus movimientos no se tocan"))
        assertTrue(borrados.isEmpty())
        composeRule.onAllNodes(hasClickAction() and (hasText("Eliminar") or hasAnyChild(hasText("Eliminar"))), useUnmergedTree = true)
            .fetchSemanticsNodes().let { assertTrue(it.size >= 2, "falta el botón de confirmar") }
        composeRule.onAllNodes(hasClickAction() and (hasText("Eliminar") or hasAnyChild(hasText("Eliminar"))), useUnmergedTree = true)[1]
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitUntil(timeoutMillis = 5_000) { borrados.isNotEmpty() }
        assertEquals(listOf(hernan.id), borrados)
    }

    @Test
    fun `los comercios van aparte de las personas`() {
        Repositories.sustitutoDePrueba = Repo(listOf(ana, hernan.copy(tipo = TipoDeTercero.COMERCIO, nombre = "Parqueadero")))
        composeRule.setContent { MoviTheme { Box(Modifier.fillMaxSize()) { DestinosScreen(onNavigate = {}) } } }
        esperar("COMERCIOS")
        assertTrue(hay("PERSONAS"))
    }

    // ── Andamio ─────────────────────────────────────────────────────────────────

    private fun montar(abrir: String? = null, repo: RepositorioDePrueba = Repo()) {
        Repositories.sustitutoDePrueba = repo
        composeRule.setContent { MoviTheme { Box(Modifier.fillMaxSize()) { DestinosScreen(onNavigate = {}, abrir = abrir) } } }
        composeRule.waitForIdle()
    }

    private fun hay(texto: String): Boolean =
        composeRule.onAllNodesWithText(texto, substring = true, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun esperar(texto: String) {
        composeRule.waitUntil(timeoutMillis = 5_000) { hay(texto) }
        composeRule.waitForIdle()
    }

    private fun tocar(texto: String) {
        composeRule.onAllNodes(hasClickAction() and (hasText(texto) or hasAnyChild(hasText(texto))), useUnmergedTree = true)[0]
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
    }
}
