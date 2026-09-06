package com.jvillada.movi.ui.transactions

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.jvillada.movi.data.DiasPlegadosStore
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.RepositorioDePrueba
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.EventDay
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.ReconciliationStatus
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.theme.MoviTheme
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertTrue

/**
 * # Movimientos montada de verdad: el vacío de «Por confirmar» y los días que se pliegan
 *
 * Se monta [TransactionsScreen] completa sobre un [RepositorioDePrueba] que contesta un día con
 * dos gastos, y se afirman las dos cosas que el dueño pidió y que una función pura no puede
 * garantizar sola: **qué dice la pantalla** cuando el chip «Por confirmar» deja la lista vacía, y
 * **qué queda a la vista** cuando se pliega un día.
 *
 * La fecha del día es vieja y de otro año para que el encabezado sea siempre «15 DE MARZO DE
 * 2024» — nunca «HOY» ni «AYER», que dependen del reloj.
 *
 * `useUnmergedTree = true` en todo, como en `HojaDelMovimientoTest`: el `clickable` del
 * encabezado y el de cada renglón fusionan sus textos, y sin eso se buscaría el bloque entero.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = AVD_MOVI_SENSOR)
class MovimientosPlegablesTest {

    @get:Rule val composeRule = createComposeRule()

    private val bancolombia = Account("acc-banco", "Bancolombia", AccountType.SAVINGS, 1_000_000L, "COP")

    private fun gasto(id: String, description: String, amount: Long) = FinancialEvent(
        id = id,
        accountId = bancolombia.id,
        type = TransactionType.EXPENSE,
        amount = amount,
        category = "Comida",
        description = description,
        timestamp = 1_710_500_000_000L,
        reconciliationStatus = ReconciliationStatus.RECONCILED,
        countsAsCashFlow = true,
    )

    private val dia = EventDay(
        date = "2024-03-15",
        total = -69_489L,
        items = listOf(gasto("e1", "Señor Gol", 46_489L), gasto("e2", "Las Doce", 23_000L)),
    )

    @Before
    fun montar() {
        DiasPlegadosStore.clear()
        Repositories.sustitutoDePrueba = object : RepositorioDePrueba() {
            override suspend fun getAccounts(): List<Account> = listOf(bancolombia)
            override suspend fun getEventsByDay(): List<EventDay> = listOf(dia)
            override suspend fun getCardPaymentCandidates(): List<FinancialEvent> = emptyList()
        }
        composeRule.setContent {
            MoviTheme {
                Box(Modifier.fillMaxSize()) { TransactionsScreen(onNavigate = {}) }
            }
        }
        esperarTexto("Señor Gol")
    }

    @After
    fun limpiar() {
        Repositories.sustitutoDePrueba = null
        DiasPlegadosStore.clear()
    }

    private fun esperarTexto(texto: String) {
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText(texto, substring = true, useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun `por confirmar vacio dice que no hay nada por confirmar y no ofrece registrar`() {
        composeRule.onNodeWithText("Por confirmar", useUnmergedTree = true).performClick()
        esperarTexto("Nada por confirmar")

        composeRule.onNodeWithText("Nada por confirmar", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("lo registraste tú", substring = true, useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("+ Registrar el primero", useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNodeWithText("Sin movimientos aún", useUnmergedTree = true).assertDoesNotExist()
        // Y los renglones del día tampoco están: el chip los filtró.
        composeRule.onNodeWithText("Señor Gol", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun `un dia plegado esconde sus renglones pero sigue diciendo su flujo`() {
        composeRule.onNodeWithText("Flujo del día", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("Las Doce", useUnmergedTree = true).assertIsDisplayed()

        composeRule.onNode(hasText("15 DE MARZO DE 2024"), useUnmergedTree = true).performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Señor Gol", useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNodeWithText("Las Doce", useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNodeWithText("Flujo del día", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("−$69.489", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("2 movimientos", substring = true, useUnmergedTree = true).assertIsDisplayed()
        assertTrue("2024-03-15" in DiasPlegadosStore.plegados(), "el pliegue tiene que quedar recordado por fecha")
    }

    @Test
    fun `volver a tocar el encabezado despliega el dia`() {
        composeRule.onNode(hasText("15 DE MARZO DE 2024"), useUnmergedTree = true).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Señor Gol", useUnmergedTree = true).assertDoesNotExist()

        composeRule.onNode(hasText("15 DE MARZO DE 2024"), useUnmergedTree = true).performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Señor Gol", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("2 movimientos", substring = true, useUnmergedTree = true).assertDoesNotExist()
        assertTrue("2024-03-15" !in DiasPlegadosStore.plegados())
    }
}

private const val AVD_MOVI_SENSOR = "w411dp-h731dp-xhdpi"
