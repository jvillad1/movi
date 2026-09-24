package com.jvillada.movi.ui.mas

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.FinanceSummary
import com.jvillada.movi.shared.model.Scope
import com.jvillada.movi.theme.MoviTheme
import com.jvillada.movi.ui.dashboard.DashboardData
import com.jvillada.movi.ui.dashboard.DashboardDataCache
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * PR 2 del rediseño de Recurrentes (2026-09): «Recurrentes» dejó de tener entrada en Más — la
 * misma razón que le sacó el destino propio al rail, ver `MinNavRailTest`. Se monta la pantalla
 * completa (y no solo la lista privada `items`, que no se puede leer desde afuera) para probar
 * lo que de verdad importa: qué ficha ve el dueño.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h731dp-xhdpi")
class MasScreenTest {

    @get:Rule val composeRule = createComposeRule()

    @Test
    fun `Mas ya no ofrece Recurrentes, y el resto de las fichas sigue ahi`() {
        composeRule.setContent {
            MoviTheme { MasScreen(onNavigate = {}) }
        }

        composeRule.onNodeWithText("Recurrentes", useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNodeWithText("Cuentas", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("Presupuestos", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("Créditos", useUnmergedTree = true).assertIsDisplayed()
    }

    // ── Ola B, tarea 7: Metas y Extractos salieron; Primeros pasos es condicional ──────

    @Test
    fun `Mas ya no ofrece Metas ni Extractos, y Documentos sigue ahi`() {
        composeRule.setContent {
            MoviTheme { MasScreen(onNavigate = {}) }
        }

        composeRule.onNodeWithText("Metas", useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNodeWithText("Extractos", useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNodeWithText("Documentos", useUnmergedTree = true).assertIsDisplayed()
    }

    private fun cuentaConMovimiento(eventCount: Int) = DashboardData(
        accounts = if (eventCount > 0) listOf(Account(id = "a1", name = "Nu", type = AccountType.SAVINGS, balance = 1)) else emptyList(),
        summary = FinanceSummary(scope = Scope.SELF, balance = 0L, ingresos = 0L, egresos = 0L, eventCount = eventCount),
        upcoming = emptyList(),
        credits = emptyList(),
        cards = emptyList(),
    )

    @Test
    fun `Primeros pasos aparece mientras la guia del Inicio este incompleta`() {
        // Contestaron las cinco lecturas y no hay ni cuenta ni movimiento: la MISMA condición
        // que prende la guía en el Inicio (`DashboardData.guiaIncompleta`).
        DashboardDataCache.data = cuentaConMovimiento(eventCount = 0)

        composeRule.setContent {
            MoviTheme { MasScreen(onNavigate = {}) }
        }

        composeRule.onNodeWithText("Primeros pasos", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun `Primeros pasos no aparece si ya hay cuenta y movimiento`() {
        DashboardDataCache.data = cuentaConMovimiento(eventCount = 3)

        composeRule.setContent {
            MoviTheme { MasScreen(onNavigate = {}) }
        }

        composeRule.onNodeWithText("Primeros pasos", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun `sin datos del Inicio todavia, Primeros pasos tampoco aparece`() {
        // `DashboardDataCache.data` empieza en null en cada prueba (ver AppDePrueba): sin
        // respuesta, `guiaIncompleta` es `false` — no se afirma que falte algo que Más nunca
        // preguntó, mismo criterio que `puedeAfirmarVacio`.
        composeRule.setContent {
            MoviTheme { MasScreen(onNavigate = {}) }
        }

        composeRule.onNodeWithText("Primeros pasos", useUnmergedTree = true).assertDoesNotExist()
    }
}
