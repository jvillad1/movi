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
import com.jvillada.movi.theme.MoviTheme
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * # La casilla «No cobra intereses» en la hoja de condiciones
 *
 * Mismo viaje redondo que [HojaDeCondicionesOtrosCargosTest]: se abre la hoja, se toca la casilla,
 * se guarda y se mira qué `CreditTerms` llega al repositorio. Con la casilla marcada la tasa no se
 * pide, se guarda en 0 aunque el campo tuviera algo, y la casilla viaja en `true`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h731dp-xhdpi")
class HojaDeCondicionesSinInteresesTest {

    @get:Rule val composeRule = createComposeRule()

    private val prestamo = Account("acc_papa", "Préstamo papá", AccountType.LOAN, 10_000_000L)

    private val condiciones = CreditTerms(
        accountId = prestamo.id,
        bank = "Papá",
        principal = 10_000_000L,
        rateEa = 5.0,
        termMonths = 7,
        installment = 1_500_000L,
        dayOfMonth = 5,
        startDate = "2026-01-05",
    )

    private var guardado: CreditTerms? = null

    @After
    fun limpiarLaCostura() {
        Repositories.sustitutoDePrueba = null
    }

    private fun montarLaHoja(terms: CreditTerms) {
        guardado = null
        Repositories.sustitutoDePrueba = object : RepositorioDePrueba() {
            override suspend fun getAccounts(): List<Account> = listOf(prestamo)
            override suspend fun putCreditTerms(terms: CreditTerms): CreditSummary {
                guardado = terms
                return CreditSummary(account = prestamo, terms = terms, paidPct = null)
            }
        }
        composeRule.setContent {
            MoviTheme {
                CreditTermsSheet(
                    editing = CreditSummary(account = prestamo, terms = terms, paidPct = null),
                    candidates = emptyList(),
                    onDismiss = {},
                    onSaved = {},
                )
            }
        }
        composeRule.waitForIdle()
    }

    private fun cuantosNodosDicen(texto: String) =
        composeRule.onAllNodesWithText(texto, substring = true).fetchSemanticsNodes().size

    @Test
    fun `marcar la casilla esconde la tasa y guarda cero con la casilla puesta`() {
        montarLaHoja(condiciones)
        assertTrue(cuantosNodosDicen("5.0") > 0, "sin la casilla la tasa se pide")

        composeRule.onNodeWithText("No cobra intereses").performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
        assertEquals(0, cuantosNodosDicen("5.0"), "con la casilla la tasa no se pide")

        composeRule.onNodeWithText("Guardar crédito").performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitUntil(5_000) { guardado != null }

        val terms = assertNotNull(guardado)
        assertTrue(terms.sinIntereses, "la hoja se olvidó de mandar la casilla")
        assertEquals(0.0, terms.rateEa, "con la casilla la tasa se guarda en 0, aunque el campo dijera 5")
    }

    @Test
    fun `un credito ya marcado abre con la casilla y se puede desmarcar`() {
        montarLaHoja(condiciones.copy(rateEa = 12.0, sinIntereses = true))
        assertEquals(0, cuantosNodosDicen("12.0"))

        composeRule.onNodeWithText("No cobra intereses").performSemanticsAction(SemanticsActions.OnClick)
        composeRule.onNodeWithText("Guardar crédito").performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitUntil(5_000) { guardado != null }

        val terms = assertNotNull(guardado)
        assertFalse(terms.sinIntereses)
        assertEquals(12.0, terms.rateEa, "desmarcarla devuelve la tasa que había en el campo")
    }
}
