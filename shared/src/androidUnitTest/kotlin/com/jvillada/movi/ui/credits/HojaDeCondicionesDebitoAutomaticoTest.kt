package com.jvillada.movi.ui.credits

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.RepositorioDePrueba
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.CreditSummary
import com.jvillada.movi.shared.model.CreditTerms
import com.jvillada.movi.shared.model.DEBITO_SIN_CUENTA
import com.jvillada.movi.theme.MoviTheme
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * # «El banco la cobra solo» en la hoja de condiciones
 *
 * El viaje redondo de [HojaDeCondicionesSinInteresesTest]: se marca la casilla, se elige la cuenta,
 * se guarda y se mira qué `CreditTerms` llega al repositorio. Además: sin cuenta elegida el botón
 * dice qué falta, la tarjeta de crédito no se ofrece como cuenta del débito, y un crédito que ya lo
 * tenía se puede desmarcar (viaja `null`).
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h1600dp-xhdpi")
class HojaDeCondicionesDebitoAutomaticoTest {

    @get:Rule val composeRule = createComposeRule()

    private val credito = Account("acc_9695", "Libre inversión 9695", AccountType.LOAN, 30_000_000L)
    private val ahorros = Account("acc_ahorros", "Bancolombia Ahorros", AccountType.SAVINGS, 2_000_000L)
    private val tarjeta = Account("acc_master", "Master Black", AccountType.CREDIT_CARD, 1_000_000L)

    private val condiciones = CreditTerms(
        accountId = credito.id,
        bank = "Bancolombia",
        principal = 40_000_000L,
        rateEa = 24.5,
        termMonths = 60,
        installment = 1_204_064L,
        dayOfMonth = 15,
        startDate = "2025-03-15",
    )

    private var guardado: CreditTerms? = null

    @After
    fun limpiarLaCostura() {
        Repositories.sustitutoDePrueba = null
    }

    private fun montarLaHoja(terms: CreditTerms) {
        guardado = null
        Repositories.sustitutoDePrueba = object : RepositorioDePrueba() {
            override suspend fun getAccounts(): List<Account> = listOf(credito, ahorros, tarjeta)
            override suspend fun putCreditTerms(terms: CreditTerms): CreditSummary {
                guardado = terms
                return CreditSummary(account = credito, terms = terms, paidPct = null)
            }
        }
        composeRule.setContent {
            MoviTheme {
                CreditTermsSheet(
                    editing = CreditSummary(account = credito, terms = terms, paidPct = null),
                    candidates = emptyList(),
                    onDismiss = {},
                    onSaved = {},
                )
            }
        }
        composeRule.waitForIdle()
    }

    private fun hay(texto: String) =
        composeRule.onAllNodesWithText(texto, substring = true).fetchSemanticsNodes().isNotEmpty()

    private fun tocar(texto: String) {
        composeRule.onNodeWithText(texto).performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
    }

    @Test
    fun `marcar la casilla y elegir la cuenta guarda el debito`() {
        montarLaHoja(condiciones)
        assertTrue(hay(TITULO_DEL_DEBITO_AUTOMATICO))
        tocar(TITULO_DEL_DEBITO_AUTOMATICO)

        composeRule.waitUntil(5_000) { hay("Bancolombia Ahorros") }
        // Sin cuenta elegida no se puede guardar, y se dice por qué.
        assertTrue(hay(DEBITO_SIN_CUENTA))
        // Una tarjeta no es una cuenta de la que el banco debite una cuota.
        assertTrue(!hay("Master Black"), "la tarjeta no se ofrece como cuenta del débito")

        tocar("Bancolombia Ahorros")
        tocar("Guardar crédito")
        composeRule.waitUntil(5_000) { guardado != null }
        assertEquals(ahorros.id, assertNotNull(guardado).debitoAutomaticoDesde)
    }

    @Test
    fun `un credito que ya se debita solo se puede desmarcar`() {
        montarLaHoja(condiciones.copy(debitoAutomaticoDesde = ahorros.id))
        composeRule.waitUntil(5_000) { hay("Bancolombia Ahorros") }
        tocar(TITULO_DEL_DEBITO_AUTOMATICO)
        tocar("Guardar crédito")
        composeRule.waitUntil(5_000) { guardado != null }
        assertNull(assertNotNull(guardado).debitoAutomaticoDesde, "desmarcar tiene que mandar null")
    }

    @Test
    fun `con la libranza marcada no se pregunta`() {
        montarLaHoja(condiciones.copy(payrollDeduction = true))
        assertTrue(!hay(TITULO_DEL_DEBITO_AUTOMATICO), "una libranza no se debita de una cuenta")
    }
}
