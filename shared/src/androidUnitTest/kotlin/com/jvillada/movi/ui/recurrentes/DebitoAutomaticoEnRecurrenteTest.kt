package com.jvillada.movi.ui.recurrentes

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.RepositorioDePrueba
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.DEBITO_DE_REGLA_SIN_CUENTA
import com.jvillada.movi.shared.model.DestinoConocido
import com.jvillada.movi.shared.model.RecurringRule
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.theme.MoviTheme
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * # «El banco lo cobra solo» en la hoja de un recurrente
 *
 * Marcar la casilla en un gasto con cuenta manda `seDebitaSolo = true`; sin cuenta, el botón dice
 * qué falta en vez de guardar una marca que no puede proponer nada.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h1600dp-xhdpi")
class DebitoAutomaticoEnRecurrenteTest {

    @get:Rule val composeRule = createComposeRule()

    private val ahorros = Account("acc_ahorros", "Bancolombia Ahorros", AccountType.SAVINGS, 2_000_000L)
    private var guardada: RecurringRule? = null

    private inner class Repo : RepositorioDePrueba() {
        override suspend fun getAccounts(): List<Account> = listOf(ahorros)
        override suspend fun getDestinos(): List<DestinoConocido> = emptyList()
        override suspend fun updateRecurringRule(id: String, rule: RecurringRule): RecurringRule {
            guardada = rule
            return rule.copy(id = id)
        }
    }

    @After
    fun limpiar() {
        Repositories.sustitutoDePrueba = null
    }

    private fun montar(regla: RecurringRule) {
        guardada = null
        Repositories.sustitutoDePrueba = Repo()
        composeRule.setContent {
            MoviTheme { CreateRecurringRuleSheet(onDismiss = {}, onSaved = {}, existing = regla) }
        }
        composeRule.waitForIdle()
    }

    private fun hay(texto: String) =
        composeRule.onAllNodesWithText(texto, substring = true).fetchSemanticsNodes().isNotEmpty()

    private val seguro = RecurringRule(
        id = "rr_seguro", name = "Seguro Sura", category = "Seguros", amount = 98_500L,
        dayOfMonth = 10, type = TransactionType.EXPENSE, accountId = ahorros.id,
    )

    @Test
    fun `marcarlo en un gasto con cuenta lo guarda`() {
        montar(seguro)
        composeRule.onNodeWithText(TITULO_DEL_DEBITO_DE_LA_REGLA).performSemanticsAction(SemanticsActions.OnClick)
        composeRule.onNodeWithText("Guardar cambios").performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitUntil(5_000) { guardada != null }
        assertEquals(true, assertNotNull(guardada).seDebitaSolo)
    }

    @Test
    fun `sin cuenta dice que falta en vez de guardar`() {
        montar(seguro.copy(accountId = null))
        composeRule.onNodeWithText(TITULO_DEL_DEBITO_DE_LA_REGLA).performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
        assertTrue(hay(DEBITO_DE_REGLA_SIN_CUENTA))
    }

    @Test
    fun `sin tocarla viaja en false, no en null`() {
        montar(seguro)
        composeRule.onNodeWithText("Guardar cambios").performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitUntil(5_000) { guardada != null }
        assertEquals(false, assertNotNull(guardada).seDebitaSolo)
    }
}
