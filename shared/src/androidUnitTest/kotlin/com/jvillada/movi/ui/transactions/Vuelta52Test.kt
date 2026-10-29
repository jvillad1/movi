package com.jvillada.movi.ui.transactions

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import com.jvillada.movi.data.DiasPlegadosStore
import com.jvillada.movi.data.RepositorioDePruebaDeMovimientos
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.CARD_PAYMENT_CATEGORY
import com.jvillada.movi.shared.model.CUOTA_CATEGORY
import com.jvillada.movi.shared.model.EventDay
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.ReconciliationStatus
import com.jvillada.movi.shared.model.TRANSFER_CATEGORY
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.isCashFlow
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
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * # Vuelta 52 — la marca junto a la cifra y la nota del pago de tarjeta
 *
 * 1. **El glifo junto a la cifra es «Recurrente», no «tiene par».** La revisión de la vuelta 50 lo leyó
 *    como ⇄ y pidió que saliera también en el traspaso. Pero es el ícono de repetición
 *    (`Icons.Rounded.Repeat`, `contentDescription = "Recurrente"`): sale cuando Movi reconoce que el
 *    movimiento se repite ([nombreRecurrenteDe]), y la cuota de un crédito y el pago de una tarjeta se
 *    repiten por su forma; un traspaso a mano no. Que lo lleve el traspaso sería afirmar que se repite.
 *    Lo que esta prueba fija, montado de verdad: los tres pares en un mismo día, y la marca solo en los
 *    dos que se repiten.
 * 2. **La nota del pago de tarjeta es de un renglón**, a 360 dp con la letra ×1,12, y lo que se recorta
 *    sigue completo en el `contentDescription`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h1600dp-xhdpi")
class Vuelta52Test {

    @get:Rule val composeRule = createComposeRule()

    private val banco = Account("acc-banco", "Bancolombia Ahorros", AccountType.SAVINGS, 8_000_000L, "COP")
    private val ahorro = Account("acc-cdt", "CDT", AccountType.INVESTMENT, 1_000_000L, "COP")
    private val prestamo = Account("acc-carro", "Vehículo", AccountType.LOAN, -60_000_000L, "COP")
    private val tarjeta = Account("acc-tarjeta", "Master Black", AccountType.CREDIT_CARD, -1_929_536L, "COP")

    private fun pata(
        id: String,
        cuenta: Account,
        tipo: TransactionType,
        categoria: String,
        descripcion: String,
        monto: Long,
        par: String,
    ) = FinancialEvent(
        id = id,
        accountId = cuenta.id,
        type = tipo,
        amount = monto,
        category = categoria,
        description = descripcion,
        timestamp = Clock.System.now().toEpochMilliseconds(),
        reconciliationStatus = ReconciliationStatus.RECONCILED,
        transferId = par,
        countsAsCashFlow = isCashFlow(cuenta.type, tipo, categoria),
    )

    private val items = listOf(
        pata("t-out", banco, TransactionType.EXPENSE, TRANSFER_CATEGORY, "Traspaso", 50_000L, "tr-t"),
        pata("t-in", ahorro, TransactionType.INCOME, TRANSFER_CATEGORY, "Traspaso", 50_000L, "tr-t"),
        pata("p-out", banco, TransactionType.EXPENSE, CARD_PAYMENT_CATEGORY, "Pago tarjeta", 1_929_536L, "tr-p"),
        pata("p-in", tarjeta, TransactionType.INCOME, CARD_PAYMENT_CATEGORY, "Pago tarjeta", 1_929_536L, "tr-p"),
        pata("c-out", banco, TransactionType.EXPENSE, CUOTA_CATEGORY, "Cuota de Vehículo", 980_000L, "tr-c"),
        pata("c-in", prestamo, TransactionType.INCOME, CUOTA_CATEGORY, "Abono a capital", 400_000L, "tr-c"),
    )

    @Before fun limpiarAntes() { DiasPlegadosStore.clear() }

    @After fun limpiar() {
        Repositories.sustitutoDePrueba = null
        DiasPlegadosStore.clear()
    }

    private fun montar() {
        val hoy = epochMillisToAppDate(Clock.System.now().toEpochMilliseconds()).toString()
        Repositories.sustitutoDePrueba = object : RepositorioDePruebaDeMovimientos() {
            override suspend fun getAccounts(): List<Account> = listOf(banco, ahorro, prestamo, tarjeta)
            override suspend fun getEventsByDay(): List<EventDay> =
                listOf(EventDay(date = hoy, total = 0L, items = items))
            override suspend fun getCardPaymentCandidates(): List<FinancialEvent> = emptyList()
        }
        composeRule.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(base.density, base.fontScale * 1.12f)) {
                MoviTheme { Box(Modifier.fillMaxSize()) { TransactionsScreen(onNavigate = {}) } }
            }
        }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("Cuota de Vehículo", useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.waitForIdle()
    }

    @Test
    fun `el glifo junto a la cifra es Recurrente en la cuota y el pago de tarjeta y no en el traspaso`() {
        montar()
        composeRule.onAllNodesWithText("Traspaso", useUnmergedTree = true).assertCountEquals(1)
        composeRule.onAllNodesWithText("Cuota de Vehículo", useUnmergedTree = true).assertCountEquals(1)
        composeRule.onAllNodesWithText("Pago de tarjeta", useUnmergedTree = true).assertCountEquals(1)
        // Tres pares, dos marcas: el traspaso a mano no se repite, así que no la lleva.
        composeRule.onAllNodesWithContentDescription("Recurrente", useUnmergedTree = true).assertCountEquals(2)
    }

    @Test
    fun `a 360 dp con la letra al 112 la nota del pago de tarjeta es un solo renglon y se lee entera`() {
        montar()
        val nodo = composeRule.onAllNodesWithTag(TAG_PAGO_DE_TARJETA_EN_EL_DIA, useUnmergedTree = true)
            .fetchSemanticsNodes().single()
        val resultados = mutableListOf<TextLayoutResult>()
        nodo.config[SemanticsActions.GetTextLayoutResult].action?.invoke(resultados)
        val layout = resultados.single()
        assertEquals(1, layout.lineCount, "la nota ocupa un solo renglón")
        assertTrue(!layout.didOverflowWidth, "y no se sale del ancho: o cabe la nota o cabe la corta")
        val texto = nodo.config.getOrNull(SemanticsProperties.Text)!!.joinToString("") { it.text }
        assertTrue(texto.startsWith("+ \$1.929.536 a tu tarjeta"), "dice cuánto fue a la tarjeta, vi «$texto»")
        // Con la nota recortada o no, el lector de pantalla oye la explicación completa.
        val descripcion = nodo.config.getOrNull(SemanticsProperties.ContentDescription)!!.joinToString(" ")
        assertEquals(
            "Además, \$1.929.536 fueron a pagar tarjeta — ya contado en tu gasto del mes cuando compraste, " +
                "no se duplica.",
            descripcion,
        )
    }
}
