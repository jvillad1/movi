package com.jvillada.movi.ui.transactions

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import com.jvillada.movi.data.DiasPlegadosStore
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.RepositorioDePruebaDeMovimientos
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.EventDay
import com.jvillada.movi.shared.model.EventOccurrenceMark
import com.jvillada.movi.shared.model.EventSource
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.RecurringRule
import com.jvillada.movi.shared.model.ReconciliationStatus
import com.jvillada.movi.shared.model.SmsMessage
import com.jvillada.movi.shared.model.SubscriptionsResult
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.UserProfile
import com.jvillada.movi.theme.MoviTheme
import com.jvillada.movi.ui.components.TAG_BUSCAR_CATEGORIA
import com.jvillada.movi.ui.components.TAG_PANEL_DE_HOJA
import kotlinx.datetime.Clock
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * # Ola 2b · deslizar una fila de Movimientos, montada de verdad
 *
 * A la izquierda abre el selector de categoría de ese movimiento; a la derecha confirma un aviso
 * pendiente (el mismo `confirmEvent` del botón «Confirmar») y en uno que no está pendiente no hace
 * nada. Y tocar la fila sigue abriendo el detalle, ahora con «En este comercio» cuando aplica.
 *
 * Teléfono de 411×731: el gesto es del teléfono (en pantalla ancha no se activa).
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h731dp-xhdpi")
class DeslizarLaFilaTest {

    @get:Rule val composeRule = createComposeRule()

    private val nu = Account("acc-nu", "Nu", AccountType.SAVINGS, 500_000L, "COP")
    private val ahora = Clock.System.now().toEpochMilliseconds()

    private fun gasto(
        id: String,
        descripcion: String,
        monto: Long,
        merchant: String? = null,
        estado: ReconciliationStatus = ReconciliationStatus.RECONCILED,
    ) = FinancialEvent(
        id = id, accountId = nu.id, type = TransactionType.EXPENSE, amount = monto, category = "Comida",
        description = descripcion, merchant = merchant, timestamp = ahora, reconciliationStatus = estado,
        source = if (estado == ReconciliationStatus.UNCONFIRMED) EventSource.SMS else EventSource.MANUAL,
        countsAsCashFlow = true,
    )

    private val rappi1 = gasto("e1", "Rappi", 27_500L, merchant = "RAPPI*LAS DOCE")
    private val rappi2 = gasto("e2", "Rappi Turbo", 41_300L, merchant = "RAPPI*TURBO")
    private val carnes = gasto("e3", "Carnes y Legumbres Santa Elena", 80_000L)
    private val pendiente = gasto("e4", "Compra Exito", 18_500L, estado = ReconciliationStatus.UNCONFIRMED)

    private inner class Repo(private val items: List<FinancialEvent>) : RepositorioDePruebaDeMovimientos() {
        val confirmados = mutableListOf<String>()
        override suspend fun getUserProfile(): UserProfile =
            UserProfile(id = "u1", email = "juan@movi.test", name = "Juan", avatarColor = "morado", periodCutoffDay = 1)
        override suspend fun getAccounts(): List<Account> = listOf(nu)
        override suspend fun getEventsByDay(): List<EventDay> = listOf(EventDay(HOY, -items.sumOf { it.amount }, items))
        override suspend fun getCardPaymentCandidates(): List<FinancialEvent> = emptyList()
        override suspend fun getSmsMessages(): List<SmsMessage> = emptyList()
        override suspend fun getEventOccurrenceMark(id: String): EventOccurrenceMark? = null
        override suspend fun getRecurringRules(): List<RecurringRule> = emptyList()
        override suspend fun getSubscriptions(): SubscriptionsResult = SubscriptionsResult(emptyList(), 0)
        override suspend fun getParecidos(id: String): List<FinancialEvent> = emptyList()
        override suspend fun confirmEvent(id: String): FinancialEvent {
            confirmados += id
            return items.first { it.id == id }.copy(reconciliationStatus = ReconciliationStatus.RECONCILED)
        }
    }

    private fun montar(items: List<FinancialEvent> = listOf(rappi1, rappi2, carnes, pendiente)): Repo {
        DiasPlegadosStore.clear()
        val repo = Repo(items)
        Repositories.sustitutoDePrueba = repo
        composeRule.setContent {
            MoviTheme { Box(Modifier.fillMaxSize()) { TransactionsScreen(onNavigate = {}) } }
        }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText(items.first().description, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.waitForIdle()
        return repo
    }

    @After
    fun limpiar() {
        Repositories.sustitutoDePrueba = null
        DiasPlegadosStore.clear()
    }

    private fun fila(descripcion: String) = composeRule.onAllNodes(
        hasTestTag(TAG_FILA_DE_MOVIMIENTO_SUELTO) and hasAnyDescendant(hasText(descripcion)),
        useUnmergedTree = true,
    ).onFirst()

    private fun hayTag(tag: String) =
        composeRule.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun deslizar_a_la_izquierda_abre_el_selector_de_categoria() {
        montar()

        fila("Carnes y Legumbres Santa Elena").performTouchInput { swipeLeft() }
        composeRule.waitUntil(timeoutMillis = 5_000) { hayTag(TAG_BUSCAR_CATEGORIA) }

        // Del movimiento deslizado, no de otro: su detalle está detrás del selector.
        assertTrue(hayTag(TAG_PANEL_DE_HOJA), "se abrió la hoja")
        composeRule.onNodeWithTag(TAG_MONTO_DEL_MOVIMIENTO, useUnmergedTree = true).assertTextEquals("−$80.000")
    }

    @Test
    fun deslizar_a_la_derecha_confirma_un_pendiente() {
        val repo = montar()

        fila("Compra Exito").performTouchInput { swipeRight() }
        composeRule.waitUntil(timeoutMillis = 5_000) { repo.confirmados.isNotEmpty() }

        assertEquals(listOf("e4"), repo.confirmados)
        assertTrue(!hayTag(TAG_PANEL_DE_HOJA), "confirmar deslizando no abre la hoja")
    }

    @Test
    fun deslizar_a_la_derecha_uno_que_no_esta_pendiente_no_hace_nada() {
        val repo = montar()

        fila("Carnes y Legumbres Santa Elena").performTouchInput { swipeRight() }
        composeRule.waitForIdle()

        assertTrue(repo.confirmados.isEmpty(), "confirmó un movimiento que no estaba pendiente")
        assertTrue(!hayTag(TAG_PANEL_DE_HOJA), "no se abrió nada")
    }

    @Test
    fun tocar_la_fila_sigue_abriendo_el_detalle_con_en_este_comercio() {
        montar()

        fila("Rappi").performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitUntil(timeoutMillis = 5_000) { hayTag(TAG_EN_ESTE_COMERCIO) }

        // Dos de Rappi (27.500 + 41.300) y uno de otro comercio que no cuenta.
        composeRule.onNode(hasTestTag(TAG_EN_ESTE_COMERCIO) and hasAnyDescendant(hasText("2 veces este período · $68.800")), useUnmergedTree = true)
            .assertExists()
        // Y no se abrió el selector: el detalle se lee primero.
        assertTrue(!hayTag(TAG_BUSCAR_CATEGORIA))
    }

    @Test
    fun tocar_en_este_comercio_cierra_la_hoja_y_busca_ese_comercio() {
        montar()

        fila("Rappi").performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitUntil(timeoutMillis = 5_000) { hayTag(TAG_EN_ESTE_COMERCIO) }
        composeRule.onNodeWithTag(TAG_EN_ESTE_COMERCIO, useUnmergedTree = true).performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()

        assertTrue(!hayTag(TAG_PANEL_DE_HOJA), "la hoja se cerró")
        // Ola R4: la búsqueda que pide «En este comercio» se ve como un chip con su × (la lupa abre Buscar).
        composeRule.onNode(hasTestTag(TAG_CHIP_DE_LA_BUSQUEDA) and hasText("Rappi", substring = true)).assertExists()
        assertTrue(composeRule.onAllNodesWithText("Carnes y Legumbres Santa Elena", useUnmergedTree = true).fetchSemanticsNodes().isEmpty())
    }
}

private val HOY: String = com.jvillada.movi.ui.quickadd.todayIsoInAppZone()
