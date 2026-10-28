package com.jvillada.movi.ui.accounts

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.RepositorioDePrueba
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountGroup
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.Apartado
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.theme.MoviTheme
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertTrue

/**
 * # Vuelta 42: el ícono del tipo de la cuenta va con su rótulo
 *
 * En `full-05-cuenta` había un ícono de banco solo, sin rótulo, a la derecha del título «Bancolombia
 * Ahorros»: parecía un botón y no hacía nada. Ahora va en la tarjeta del saldo, al lado de lo que dice
 * («COP · Ahorros»), y el encabezado queda con la flecha y el título.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w390dp-h900dp-xhdpi")
class ElTipoDeLaCuentaConSuRotuloTest {

    @get:Rule val composeRule = createComposeRule()

    private val ahorros = Account("acc-b", "Bancolombia Ahorros", AccountType.SAVINGS, 13_760_800L)

    @After fun limpiar() { Repositories.sustitutoDePrueba = null }

    @Test
    fun `el tipo va con su rotulo en la tarjeta, no solo en el encabezado`() {
        Repositories.sustitutoDePrueba = object : RepositorioDePrueba() {
            override suspend fun getAccount(id: String): Account = ahorros
            override suspend fun getEvents(accountId: String?): List<FinancialEvent> = emptyList()
            override suspend fun getApartados(): List<Apartado> = emptyList()
        }
        composeRule.setContent {
            MoviTheme {
                Box(Modifier.fillMaxSize()) {
                    AccountDetailScreen(onNavigate = {}, accountId = ahorros.id, group = AccountGroup.DINERO)
                }
            }
        }
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("COP · Ahorros", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNode(hasText("COP · Ahorros") and hasAnyAncestor(hasTestTag(TAG_TIPO_DE_LA_CUENTA)), useUnmergedTree = true)
            .assertExists()
        assertTrue(
            composeRule.onAllNodesWithContentDescription("Ahorros", useUnmergedTree = true).fetchSemanticsNodes().isEmpty(),
            "volvió el ícono del tipo solo, sin rótulo, en el encabezado",
        )
    }
}
