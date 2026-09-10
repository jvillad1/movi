package com.jvillada.movi.ui.transactions

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.jvillada.movi.data.DiasPlegadosStore
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.RepositorioDePrueba
import com.jvillada.movi.shared.model.ADJUSTMENT_CATEGORY
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
 * # Los ajustes de saldo dejan de tapar el día, montado de verdad
 *
 * El día que lo pidió es el del dueño conciliando contra el portal de Bancolombia: **un gasto real
 * y tres ajustes de saldo**, y los ajustes ganándole la pantalla al gasto. Palabra por palabra:
 * *«Tantos movimientos de ajuste de saldo se ven horribles»*.
 *
 * Lo que una función pura no puede afirmar y esto sí: que el renglón agrupado **se ve**, que los
 * ajustes **no** se ven hasta tocarlo, que al tocarlo aparecen, y que el gasto real nunca se fue
 * de la lista — que es el punto entero del cambio.
 *
 * Fecha vieja y de otro año para que el encabezado no dependa del reloj, y
 * `useUnmergedTree = true` en todo, igual que en [MovimientosPlegablesTest].
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = AVD_MOVI_SENSOR)
class AjustesEnMovimientosTest {

    @get:Rule val composeRule = createComposeRule()

    private val banco = Account("acc-banco", "Bancolombia Ahorros", AccountType.SAVINGS, 853_037L, "COP")
    private val libranza = Account("acc-libranza", "Libranza 4608", AccountType.LOAN, 0L, "COP")

    private fun ajuste(id: String, cuenta: String, quedoEn: String, monto: Long) = FinancialEvent(
        id = id,
        accountId = cuenta,
        type = TransactionType.INCOME,
        amount = monto,
        category = ADJUSTMENT_CATEGORY,
        description = "Ajuste al saldo del banco — quedó en $quedoEn",
        timestamp = 1_710_500_000_000L,
        reconciliationStatus = ReconciliationStatus.RECONCILED,
        countsAsCashFlow = false,
    )

    private val gasto = FinancialEvent(
        id = "e-comida",
        accountId = banco.id,
        type = TransactionType.EXPENSE,
        amount = 18_500L,
        category = "Comida",
        description = "Carnes y Legumbres Santa Elena",
        timestamp = 1_710_500_000_000L,
        reconciliationStatus = ReconciliationStatus.RECONCILED,
        countsAsCashFlow = true,
    )

    /** Un segundo gasto real, para que el total del día no coincida con el monto de ninguna fila. */
    private val gastoQR = gasto.copy(id = "e-qr", amount = 10_000L, description = "Pago QR Mora Soccer")

    private val dia = EventDay(
        date = "2024-03-15",
        total = -28_500L,
        items = listOf(
            gasto,
            gastoQR,
            ajuste("a1", banco.id, "$498.547", 9_006L),
            ajuste("a2", libranza.id, "$224.731.301", 2_568_699L),
            ajuste("a3", banco.id, "$255.677.421", 6_708_741L),
        ),
    )

    @Before
    fun montar() {
        DiasPlegadosStore.clear()
        Repositories.sustitutoDePrueba = object : RepositorioDePrueba() {
            override suspend fun getAccounts(): List<Account> = listOf(banco, libranza)
            override suspend fun getEventsByDay(): List<EventDay> = listOf(dia)
            override suspend fun getCardPaymentCandidates(): List<FinancialEvent> = emptyList()
        }
        composeRule.setContent {
            MoviTheme {
                Box(Modifier.fillMaxSize()) { TransactionsScreen(onNavigate = {}) }
            }
        }
        esperarTexto("Carnes y Legumbres Santa Elena")
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

    /**
     * **Dos cuentas y no tres correcciones**: dos de los ajustes son de la misma cuenta, y lo que
     * el dueño hizo fue repasar dos cuentas contra el banco.
     */
    @Test
    fun `los ajustes llegan plegados en un solo renglon y el gasto real sigue a la vista`() {
        composeRule.onNodeWithText("Ajustaste el saldo de 2 cuentas", useUnmergedTree = true)
            .assertIsDisplayed()
        composeRule.onNodeWithText("3 correcciones", substring = true, useUnmergedTree = true)
            .assertIsDisplayed()
        // Lo que el dueño vino a ver, que antes quedaba sepultado.
        composeRule.onNodeWithText("Carnes y Legumbres Santa Elena", useUnmergedTree = true)
            .assertIsDisplayed()
        // Y ninguno de los ajustes ocupa un renglón propio todavía.
        val sueltos = composeRule.onAllNodesWithText("quedó en", substring = true, useUnmergedTree = true)
            .fetchSemanticsNodes()
        assertTrue(sueltos.isEmpty(), "los ajustes tienen que llegar plegados, había ${sueltos.size}")
    }

    @Test
    fun `al tocar el renglon aparecen los ajustes, y al tocarlo otra vez se van`() {
        composeRule.onNodeWithText("Ajustaste el saldo de 2 cuentas", useUnmergedTree = true).performClick()
        esperarTexto("quedó en $498.547")

        composeRule.onNodeWithText("Ajuste al saldo del banco — quedó en $498.547", useUnmergedTree = true)
            .assertIsDisplayed()
        composeRule.onNodeWithText("Ajuste al saldo del banco — quedó en $224.731.301", useUnmergedTree = true)
            .assertIsDisplayed()

        composeRule.onNodeWithText("Ajustaste el saldo de 2 cuentas", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()
        val cerrados = composeRule.onAllNodesWithText("quedó en", substring = true, useUnmergedTree = true)
            .fetchSemanticsNodes()
        assertTrue(cerrados.isEmpty(), "volver a tocar tiene que cerrarlo, quedaban ${cerrados.size}")
    }

    /**
     * **El «Flujo del día» no cambia**, y no podía cambiar: ningún ajuste cuenta como plata que
     * entró o salió (`isCashFlow` los excluye), así que agruparlos no toca ninguna cifra. Esta es
     * la afirmación que hace del cambio algo cosmético y no una corrección silenciosa de números.
     */
    @Test
    fun `agrupar no mueve el flujo del dia`() {
        composeRule.onNodeWithText("−$28.500", useUnmergedTree = true).assertIsDisplayed()
    }
}

/** El mismo tamaño de pantalla que usan las otras pruebas de esta carpeta (privado por archivo). */
private const val AVD_MOVI_SENSOR = "w411dp-h731dp-xhdpi"
