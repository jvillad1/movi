package com.jvillada.movi.ui.transactions

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import com.jvillada.movi.data.DiasPlegadosStore
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.RepositorioDePruebaDeMovimientos
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.CARD_PAYMENT_CATEGORY
import com.jvillada.movi.shared.model.EventDay
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.ReconciliationStatus
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.time.epochMillisToAppDate
import com.jvillada.movi.theme.MoviTheme
import kotlinx.datetime.Clock
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertTrue

/**
 * # Ola U — «Flujo del día $0» no puede quedar sin explicación cuando salió plata de verdad
 *
 * El caso del dueño, montado de verdad: pagó dos tarjetas desde Bancolombia Ahorros el mismo día
 * ($386.902 + $1.542.634 = $1.929.536) y Movimientos decía «Flujo del día $0» — correcto para el
 * mes (la compra ya se contó como gasto), pero leído sin explicación se ve como una mentira. Ver
 * `PagoDeTarjetaEnElDiaTest` para la función pura detrás de esta línea.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = AVD_MOVI_SENSOR_PAGO_DE_TARJETA)
class PagoDeTarjetaEnMovimientosTest {

    @get:Rule val composeRule = createComposeRule()

    private val banco = Account("acc-banco", "Bancolombia Ahorros", AccountType.SAVINGS, 853_037L, "COP")
    private val tarjeta = Account("acc-tarjeta", "Master Black", AccountType.CREDIT_CARD, -1_929_536L, "COP")

    private fun pagoDeTarjeta(
        id: String,
        transferId: String,
        cuenta: Account,
        tipo: TransactionType,
        monto: Long,
    ) = FinancialEvent(
        id = id,
        accountId = cuenta.id,
        type = tipo,
        amount = monto,
        category = CARD_PAYMENT_CATEGORY,
        description = "Pago tarjeta ${cuenta.name}",
        timestamp = Clock.System.now().toEpochMilliseconds(),
        transferId = transferId,
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
        timestamp = Clock.System.now().toEpochMilliseconds(),
        reconciliationStatus = ReconciliationStatus.RECONCILED,
        countsAsCashFlow = true,
    )

    @Before
    fun limpiarAntes() {
        DiasPlegadosStore.clear()
    }

    @After
    fun limpiar() {
        Repositories.sustitutoDePrueba = null
        DiasPlegadosStore.clear()
    }

    private fun montarCon(items: List<FinancialEvent>) {
        Repositories.sustitutoDePrueba = object : RepositorioDePruebaDeMovimientos() {
            override suspend fun getAccounts(): List<Account> = listOf(banco, tarjeta)
            override suspend fun getEventsByDay(): List<EventDay> =
                listOf(EventDay(date = HOY_ISO_PAGO_DE_TARJETA, total = 0L, items = items))
            override suspend fun getCardPaymentCandidates(): List<FinancialEvent> = emptyList()
        }
        composeRule.setContent {
            MoviTheme {
                Box(Modifier.fillMaxSize()) { TransactionsScreen(onNavigate = {}) }
            }
        }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag(TAG_ENCABEZADO_DE_DIA, useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.waitForIdle()
    }

    /**
     * **Dos pagos de tarjeta y ningún otro gasto**: el caso exacto del dueño. «Flujo del día» sigue
     * en $0 (correcto: la compra ya se contó al comprarla) pero ya no se queda sin decir nada — la
     * línea nueva dice cuánto salió de verdad de su cuenta.
     */
    @Test
    fun `un dia de puros pagos de tarjeta dice Flujo del dia 0 y la linea nueva con el monto real`() {
        montarCon(
            listOf(
                pagoDeTarjeta("p1-out", "tr1", banco, TransactionType.EXPENSE, 386_902L),
                pagoDeTarjeta("p1-in", "tr1", tarjeta, TransactionType.INCOME, 386_902L),
                pagoDeTarjeta("p2-out", "tr2", banco, TransactionType.EXPENSE, 1_542_634L),
                pagoDeTarjeta("p2-in", "tr2", tarjeta, TransactionType.INCOME, 1_542_634L),
            ),
        )

        composeRule.onNodeWithText("$0", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText(
            "Además, \$1.929.536 salieron a pagar tarjeta (ya contado al comprar)",
            useUnmergedTree = true,
        ).assertIsDisplayed()
    }

    /** Un día sin ningún pago de tarjeta no gana la línea de más: «Flujo del día» sigue solo. */
    @Test
    fun `un dia normal sin pagos de tarjeta no cambia`() {
        montarCon(listOf(gasto))

        composeRule.onNodeWithText("Carnes y Legumbres Santa Elena", useUnmergedTree = true).assertIsDisplayed()
        // El monto aparece dos veces (la fila y «Flujo del día», que en este día son iguales): un
        // conteo, no `assertIsDisplayed`, que exige un único nodo.
        assertTrue(
            composeRule.onAllNodesWithText("−$18.500", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty(),
        )
        val lineas = composeRule.onAllNodesWithTag(TAG_PAGO_DE_TARJETA_EN_EL_DIA, useUnmergedTree = true)
            .fetchSemanticsNodes()
        assertTrue(lineas.isEmpty(), "sin pagos de tarjeta no hay línea de más, había ${lineas.size}")
        val texto = composeRule.onAllNodesWithText("salieron a pagar tarjeta", substring = true, useUnmergedTree = true)
            .fetchSemanticsNodes()
        assertTrue(texto.isEmpty())
    }

    /**
     * Un día con un gasto normal Y un pago de tarjeta: «Flujo del día» ya no es $0 (el gasto normal
     * sí cuenta) y la línea nueva igual aparece, sumando solo el pago de tarjeta.
     */
    @Test
    fun `un dia con gasto normal y pago de tarjeta suma solo el pago de tarjeta en la linea nueva`() {
        montarCon(
            listOf(
                gasto,
                pagoDeTarjeta("p1-out", "tr1", banco, TransactionType.EXPENSE, 386_902L),
                pagoDeTarjeta("p1-in", "tr1", tarjeta, TransactionType.INCOME, 386_902L),
            ),
        )

        assertTrue(
            composeRule.onAllNodesWithText("−$18.500", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty(),
        )
        composeRule.onNodeWithText(
            "Además, \$386.902 salieron a pagar tarjeta (ya contado al comprar)",
            useUnmergedTree = true,
        ).assertIsDisplayed()
    }
}

/** El mismo tamaño de pantalla que las otras pruebas montadas de esta carpeta. */
private const val AVD_MOVI_SENSOR_PAGO_DE_TARJETA = "w411dp-h731dp-xhdpi"

/** Hoy, en la zona de la app — para caer adentro del período que Movimientos muestra al abrirse. */
private val HOY_ISO_PAGO_DE_TARJETA: String =
    epochMillisToAppDate(Clock.System.now().toEpochMilliseconds()).toString()
