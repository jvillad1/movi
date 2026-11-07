package com.jvillada.movi.ui.credits

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.RepositorioDePrueba
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.CUENTA_DE_LA_CUOTA_FUERA_DE_TU_PLATA
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * # Vuelta 141 · «¿De qué cuenta sale la cuota?» en la hoja de condiciones (issue #701)
 *
 * El caso del dueño: la Hipoteca 1254 decía «la paga Skandia» para no bajar «Te quedan». Elegir la AFC
 * (condicionada) borra quién la paga, la hoja avisa que esa cuenta no es Tu plata, y se guarda `sePagaDesde`
 * sin `paidBy`. Con una cuenta de Tu plata no avisa nada, y quitarla manda `null`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h1600dp-xhdpi")
class HojaDeCondicionesCuentaDeLaCuotaTest {

    @get:Rule val composeRule = createComposeRule()

    private val hipoteca = Account("acc_1254", "Hipoteca 1254", AccountType.LOAN, 300_000_000L)
    private val afc = Account("acc_afc", "AFC Davibank 9497", AccountType.SAVINGS, 30_000_000L, condicionadaA = "vivienda")
    private val ahorros = Account("acc_8133", "Bancolombia 8133", AccountType.SAVINGS, 5_000_000L)
    private val tarjeta = Account("acc_master", "Master Black", AccountType.CREDIT_CARD, 1_000_000L)

    private val condiciones = CreditTerms(
        accountId = hipoteca.id, bank = "Davibank", principal = 400_000_000L, rateEa = 12.0, termMonths = 240,
        installment = 9_145_256L, dayOfMonth = 5, startDate = "2020-01-05",
    )

    private var guardado: CreditTerms? = null

    @After
    fun limpiarLaCostura() {
        Repositories.sustitutoDePrueba = null
    }

    private fun montarLaHoja(terms: CreditTerms) {
        guardado = null
        Repositories.sustitutoDePrueba = object : RepositorioDePrueba() {
            override suspend fun getAccounts(): List<Account> = listOf(hipoteca, afc, ahorros, tarjeta)
            override suspend fun putCreditTerms(terms: CreditTerms): CreditSummary {
                guardado = terms
                return CreditSummary(account = hipoteca, terms = terms, paidPct = null)
            }
        }
        composeRule.setContent {
            MoviTheme {
                CreditTermsSheet(
                    editing = CreditSummary(account = hipoteca, terms = terms, paidPct = null),
                    candidates = emptyList(),
                    onDismiss = {},
                    onSaved = {},
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag(TAG_CAMPO_DE_LA_CUENTA_DE_LA_CUOTA).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun hay(texto: String) =
        composeRule.onAllNodesWithText(texto, substring = true).fetchSemanticsNodes().isNotEmpty()

    private fun tocar(texto: String) {
        composeRule.onNodeWithText(texto).performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
    }

    private fun abrirElCampo() {
        composeRule.onNodeWithTag(TAG_CAMPO_DE_LA_CUENTA_DE_LA_CUOTA).performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
    }

    @Test
    fun `elegir la AFC borra quien la paga, avisa que no es Tu plata y la guarda`() {
        montarLaHoja(condiciones.copy(paidBy = "Skandia"))
        assertTrue(hay(PREGUNTA_DE_LA_CUENTA_DE_LA_CUOTA))
        assertTrue(hay("Skandia"))

        abrirElCampo()
        assertFalse(hay("Master Black"), "una tarjeta no paga una cuota")
        tocar(afc.name)
        assertTrue(hay(CUENTA_DE_LA_CUOTA_FUERA_DE_TU_PLATA), "la hoja dice que la AFC no es Tu plata")
        // El campo vacío vuelve a su guía («Skandia, Caro, mi papá…»): lo escrito, exacto, ya no está.
        assertTrue(
            composeRule.onAllNodesWithText("Skandia").fetchSemanticsNodes().isEmpty(),
            "elegir la cuenta borró «La paga otra persona»",
        )

        tocar("Guardar crédito")
        composeRule.waitUntil(5_000) { guardado != null }
        val t = assertNotNull(guardado)
        assertEquals(afc.id, t.sePagaDesde)
        assertNull(t.paidBy, "no viajan las dos")
    }

    @Test
    fun `con una cuenta de Tu plata no avisa, y quitarla manda null`() {
        montarLaHoja(condiciones.copy(sePagaDesde = afc.id))
        assertTrue(hay(afc.name))
        abrirElCampo()
        tocar(ahorros.name)
        assertFalse(hay(CUENTA_DE_LA_CUOTA_FUERA_DE_TU_PLATA))

        abrirElCampo()
        tocar(SIN_CUENTA_DE_LA_CUOTA)
        tocar("Guardar crédito")
        composeRule.waitUntil(5_000) { guardado != null }
        assertNull(assertNotNull(guardado).sePagaDesde, "quitarla tiene que mandar null")
    }
}
