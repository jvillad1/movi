package com.jvillada.movi.ui.transactions

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.EventSource
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.ReconciliationStatus
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.theme.MoviTheme
import com.jvillada.movi.ui.sms.REPARTIR_EN_PARTES
import com.jvillada.movi.ui.sms.ROTULO_QUE_FUE
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * # Vuelta 71 · «Dividir en categorías» en una compra con tarjeta
 *
 * `sePuedeRepartirEnPartes` excluía toda cuenta de deuda, así que una compra con tarjeta que fue
 * mercado y farmacia no se podía dividir. Ahora se ofrece en un gasto suelto de una tarjeta de crédito,
 * y el editor solo deja partes de gasto con categoría: no pregunta «Qué fue» (cuota, traspaso y pago de
 * tarjeta no tienen sentido desde una deuda). Un crédito, la pata de un avance o un ajuste siguen afuera.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h914dp-xhdpi")
class DividirUnaCompraConTarjetaTest {

    @get:Rule val composeRule = createComposeRule()

    private val nu = Account("nu", "Tarjeta Nu", AccountType.CREDIT_CARD, 0)
    private val ahorros = Account("a1", "Bancolombia Ahorros", AccountType.SAVINGS, 1_000_000)
    private val libranza = Account("l1", "Libranza", AccountType.LOAN, 0)
    private val cuentas = listOf(nu, ahorros, libranza)

    private fun gasto(cuenta: String, categoria: String = "Mercado", transferId: String? = null) = FinancialEvent(
        id = "ev-$cuenta", accountId = cuenta, type = TransactionType.EXPENSE, amount = 300_000L,
        category = categoria, description = "Éxito", timestamp = 0L, source = EventSource.MANUAL,
        reconciliationStatus = ReconciliationStatus.RECONCILED, transferId = transferId,
    )

    @Test
    fun se_ofrece_en_un_gasto_de_tarjeta_y_no_en_un_credito_un_avance_ni_un_ajuste() {
        assertTrue(sePuedeRepartirEnPartes(gasto(nu.id), cuentas), "una compra con tarjeta se divide")
        assertTrue(sePuedeRepartirEnPartes(gasto(ahorros.id), cuentas), "lo de siempre sigue igual")
        assertFalse(sePuedeRepartirEnPartes(gasto(libranza.id), cuentas), "un crédito no")
        assertFalse(sePuedeRepartirEnPartes(gasto(nu.id, transferId = "tr-avance"), cuentas), "la pata de un avance no")
        assertFalse(sePuedeRepartirEnPartes(gasto(nu.id, categoria = "Ajuste de saldo"), cuentas), "un ajuste no")
    }

    @Test
    fun en_una_tarjeta_el_editor_solo_tiene_gastos_y_no_pregunta_que_fue() {
        assertEquals(0, cuantosQueFue(gasto(nu.id)), "desde la tarjeta, solo gastos: no se pregunta «Qué fue»")
    }

    @Test
    fun en_una_cuenta_de_dinero_sigue_preguntando_que_fue() {
        assertEquals(1, cuantosQueFue(gasto(ahorros.id)))
    }

    private fun cuantosQueFue(evento: FinancialEvent): Int {
        composeRule.setContent {
            MoviTheme {
                Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    SeccionDeRepartirEnPartes(evento = evento, cuentas = cuentas, onRepartido = {})
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText(REPARTIR_EN_PARTES).performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
        return composeRule.onAllNodes(hasText(ROTULO_QUE_FUE), useUnmergedTree = true).fetchSemanticsNodes().size
    }
}
