package com.jvillada.movi.ui.accounts

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasAnyChild
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performSemanticsAction
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.RepositorioDePrueba
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountGroup
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.theme.MoviTheme
import com.jvillada.movi.ui.Screen
import com.jvillada.movi.ui.cuadre.CUADRAR_EFECTIVO
import com.jvillada.movi.ui.cuadre.cuentasConLaPrimera
import com.jvillada.movi.ui.cuadre.efectivoImposible
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * # Un efectivo en negativo se explica y se cuadra (vuelta de diseño 23)
 *
 * «Efectivo −$26.500» se pintaba en rojo sin decir nada. No se puede tener menos de cero billetes:
 * el detalle lo dice y ofrece «Cuadrar efectivo», que abre el cuadre de saldos de siempre con esa
 * cuenta arriba.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h731dp-xhdpi")
class EfectivoEnNegativoTest {

    @get:Rule val composeRule = createComposeRule()

    private val efectivo = Account("acc-efectivo", "Efectivo", AccountType.CASH, -26_500L)
    private val nu = Account("acc-nu", "Nu", AccountType.SAVINGS, -5_000L)

    @After
    fun limpiar() {
        Repositories.sustitutoDePrueba = null
    }

    @Test
    fun `solo el efectivo en negativo es imposible`() {
        assertTrue(efectivoImposible(efectivo))
        assertFalse(efectivoImposible(efectivo.copy(balance = 0)), "en cero se puede")
        assertFalse(efectivoImposible(efectivo.copy(balance = 10_000)))
        assertFalse(efectivoImposible(nu), "una cuenta de banco en descubierto sí existe")
    }

    @Test
    fun `el cuadre pone arriba la cuenta preseleccionada`() {
        val cuentas = listOf(nu, efectivo)
        assertEquals(listOf(efectivo, nu), cuentasConLaPrimera(cuentas, efectivo.id))
        assertEquals(cuentas, cuentasConLaPrimera(cuentas, null))
        assertEquals(cuentas, cuentasConLaPrimera(cuentas, "otra"), "si no está, el orden de siempre")
    }

    @Test
    fun `el detalle del efectivo en negativo ofrece cuadrarlo con esa cuenta`() {
        val navegado = mutableListOf<Screen>()
        Repositories.sustitutoDePrueba = object : RepositorioDePrueba() {
            override suspend fun getAccount(id: String): Account = efectivo
            override suspend fun getEvents(accountId: String?): List<FinancialEvent> = emptyList()
        }
        composeRule.setContent {
            MoviTheme {
                Box(Modifier.fillMaxSize()) {
                    AccountDetailScreen(onNavigate = { navegado += it }, accountId = efectivo.id, group = AccountGroup.DINERO)
                }
            }
        }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText(CUADRAR_EFECTIVO, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        assertEquals(1, composeRule.onAllNodesWithTag(TAG_EFECTIVO_EN_NEGATIVO, useUnmergedTree = true).fetchSemanticsNodes().size)
        assertTrue(
            composeRule.onAllNodesWithText("Cuadrar con el banco", useUnmergedTree = true).fetchSemanticsNodes().isEmpty(),
            "una sola acción de cuadre: la que lo explica",
        )

        composeRule.onNode(hasClickAction() and hasAnyChild(hasText(CUADRAR_EFECTIVO)), useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
        assertEquals(Screen.CuadrarLaCuenta(efectivo.id), navegado.last())
    }
}
