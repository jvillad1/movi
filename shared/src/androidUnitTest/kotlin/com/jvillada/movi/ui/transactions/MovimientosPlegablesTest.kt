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
import com.jvillada.movi.shared.time.epochMillisToAppDate
import com.jvillada.movi.theme.MoviTheme
import kotlinx.datetime.Clock
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
        timestamp = Clock.System.now().toEpochMilliseconds(),
        reconciliationStatus = ReconciliationStatus.RECONCILED,
        countsAsCashFlow = true,
    )

    private val dia = EventDay(
        date = HOY_ISO,
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

    // El vacío de «Por confirmar» se mudó a `PorConfirmarEnMovimientosTest`, junto con el resto
    // de esa bandeja: dejó de ser un chip de esta fila (ver `CHIPS_VISIBLES`) y ya no se llega
    // tocando acá, así que la prueba tiene que entrar por donde entra el dueño.

    @Test
    fun `un dia plegado esconde sus renglones pero sigue diciendo su flujo`() {
        composeRule.onNodeWithText("Flujo del día", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("Las Doce", useUnmergedTree = true).assertIsDisplayed()

        composeRule.onNode(hasText("HOY"), useUnmergedTree = true).performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Señor Gol", useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNodeWithText("Las Doce", useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNodeWithText("Flujo del día", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("−$69.489", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("2 movimientos", substring = true, useUnmergedTree = true).assertIsDisplayed()
        assertTrue(HOY_ISO in DiasPlegadosStore.plegados(), "el pliegue tiene que quedar recordado por fecha")
    }

    @Test
    fun `volver a tocar el encabezado despliega el dia`() {
        composeRule.onNode(hasText("HOY"), useUnmergedTree = true).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Señor Gol", useUnmergedTree = true).assertDoesNotExist()

        composeRule.onNode(hasText("HOY"), useUnmergedTree = true).performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Señor Gol", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("2 movimientos", substring = true, useUnmergedTree = true).assertDoesNotExist()
        assertTrue("2024-03-15" !in DiasPlegadosStore.plegados())
    }
}

private const val AVD_MOVI_SENSOR = "w411dp-h731dp-xhdpi"

/**
 * **Hoy, en la zona de la app.** Los fixtures de esta clase tienen que caer adentro del período
 * que Movimientos muestra al abrirse (ver `diasDelPeriodo`), así que la fecha sale del reloj en vez
 * de ser una constante vieja. El encabezado del día queda en «HOY», que es igual de estable que una
 * fecha fija y además no depende del corte que tenga configurado el usuario de prueba.
 */
private val HOY_ISO: String = epochMillisToAppDate(Clock.System.now().toEpochMilliseconds()).toString()
