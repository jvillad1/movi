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
 * # Ola W — «Flujo del día» ya incluye el pago de tarjeta como la salida real que es
 *
 * El caso del dueño, montado de verdad: pagó dos tarjetas desde Bancolombia Ahorros el mismo día
 * ($386.902 + $1.542.634 = $1.929.536). La Ola U había dejado «Flujo del día $0» con una línea
 * aparte explicando la exclusión; el dueño pidió lo contrario: esa cuenta SÍ perdió esa plata ese
 * día, así que «Flujo del día» debe decir −$1.929.536, y la línea de abajo debe aclarar que eso no
 * se duplica en el gasto del período/Disponible (que sigue excluyéndola, sin cambios). Ver
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
     * **Dos pagos de tarjeta y ningún otro gasto**: el caso exacto del dueño. «Flujo del día» ahora
     * dice −$1.929.536 (la plata que de verdad salió de su cuenta ese día) y la línea de abajo
     * aclara que eso no se duplica en el gasto del mes.
     */
    @Test
    fun `un dia de puros pagos de tarjeta dice Flujo del dia con el monto real y la linea de contexto`() {
        montarCon(
            listOf(
                pagoDeTarjeta("p1-out", "tr1", banco, TransactionType.EXPENSE, 386_902L),
                pagoDeTarjeta("p1-in", "tr1", tarjeta, TransactionType.INCOME, 386_902L),
                pagoDeTarjeta("p2-out", "tr2", banco, TransactionType.EXPENSE, 1_542_634L),
                pagoDeTarjeta("p2-in", "tr2", tarjeta, TransactionType.INCOME, 1_542_634L),
            ),
        )

        composeRule.onNodeWithText("−$1.929.536", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText(
            "De eso, \$1.929.536 fueron a pagar tarjeta — ya contado en tu gasto del mes cuando " +
                "compraste, no se duplica.",
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
        val texto = composeRule.onAllNodesWithText("fueron a pagar tarjeta", substring = true, useUnmergedTree = true)
            .fetchSemanticsNodes()
        assertTrue(texto.isEmpty())
    }

    /**
     * Un día con un gasto normal Y un pago de tarjeta: «Flujo del día» suma los dos (−$18.500 del
     * gasto y −$386.902 del pago = −$405.402) y la línea de abajo aclara que solo el pago de
     * tarjeta no se duplica en el gasto del mes.
     */
    @Test
    fun `un dia con gasto normal y pago de tarjeta suma los dos en Flujo del dia`() {
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
        composeRule.onNodeWithText("−$405.402", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText(
            "De eso, \$386.902 fueron a pagar tarjeta — ya contado en tu gasto del mes cuando " +
                "compraste, no se duplica.",
            useUnmergedTree = true,
        ).assertIsDisplayed()
    }
}

/** El mismo tamaño de pantalla que las otras pruebas montadas de esta carpeta. */
private const val AVD_MOVI_SENSOR_PAGO_DE_TARJETA = "w411dp-h731dp-xhdpi"

/** Hoy, en la zona de la app — para caer adentro del período que Movimientos muestra al abrirse. */
private val HOY_ISO_PAGO_DE_TARJETA: String =
    epochMillisToAppDate(Clock.System.now().toEpochMilliseconds()).toString()
