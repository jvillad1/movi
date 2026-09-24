package com.jvillada.movi.ui.mas

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.FinanceSummary
import com.jvillada.movi.shared.model.Scope
import com.jvillada.movi.theme.MoviTheme
import com.jvillada.movi.ui.Screen
import com.jvillada.movi.ui.components.LocalWindowWidthClass
import com.jvillada.movi.ui.components.WindowWidthClass
import com.jvillada.movi.ui.dashboard.DashboardData
import com.jvillada.movi.ui.dashboard.DashboardDataCache
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

/**
 * **Ajustes** (la pantalla que se llamaba «Más»). Se monta la pantalla completa (y no solo la lista
 * privada `items`, que no se puede leer desde afuera) para probar lo que de verdad importa: qué
 * ficha ve el dueño.
 *
 * PR 2 del rediseño de Recurrentes (2026-09): «Recurrentes» dejó de tener entrada acá. Ola C: salió
 * también todo lo que ahora es una pestaña (Cuentas, el cuadre, Créditos y «Cuentas de otros» son
 * Patrimonio; Presupuestos es Plan), y la pantalla pasó a abrirse desde el avatar.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h731dp-xhdpi")
class MasScreenTest {

    @get:Rule val composeRule = createComposeRule()

    private val lasDeAjustes = listOf("Perfil", "Categorías", "Documentos", "Compartir", "Movi AI", "Captura del banco")
    private val lasQueSonPestana = listOf(
        "Cuentas", "Cuadre de saldos", "Presupuestos", "Créditos", "Cuentas de otros", "Recurrentes",
    )

    @Test
    fun `Ajustes ofrece sus fichas y ninguna que ya sea una pestana`() {
        composeRule.setContent {
            MoviTheme { MasScreen(onNavigate = {}) }
        }

        composeRule.onNodeWithText(TITULO_DE_AJUSTES, useUnmergedTree = true).assertIsDisplayed()
        lasDeAjustes.forEach { composeRule.onNodeWithText(it, useUnmergedTree = true).assertIsDisplayed() }
        lasQueSonPestana.forEach { composeRule.onNodeWithText(it, useUnmergedTree = true).assertDoesNotExist() }
    }

    /**
     * Hasta la ola C, en pantalla ancha Más escondía lo que el rail ya mostraba. Ahora nada de Ajustes
     * es una pestaña, así que la web ve las mismas fichas que el teléfono.
     */
    @Test
    fun `en pantalla ancha Ajustes muestra las mismas fichas`() {
        composeRule.setContent {
            CompositionLocalProvider(LocalWindowWidthClass provides WindowWidthClass.Expanded) {
                MoviTheme { MasScreen(onNavigate = {}) }
            }
        }

        lasDeAjustes.forEach { composeRule.onNodeWithText(it, useUnmergedTree = true).assertIsDisplayed() }
    }

    /** Ajustes no es pestaña: lleva flecha (vuelve a donde se estaba), no el avatar que la abrió. */
    @Test
    fun `Ajustes lleva flecha de volver`() {
        composeRule.setContent {
            MoviTheme { MasScreen(onNavigate = {}) }
        }

        composeRule.onNodeWithContentDescription("Volver", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun `tocar Perfil abre Perfil`() {
        var navegoA: Screen? = null
        composeRule.setContent {
            MoviTheme { MasScreen(onNavigate = { navegoA = it }) }
        }

        composeRule.onNodeWithText("Perfil", useUnmergedTree = true).performClick()

        assertEquals(Screen.Profile, navegoA)
    }

    /**
     * Ola C, tarea 5: la configuración de la captura (permisos, notificaciones, hibernación) y el
     * historial de mensajes salieron de la bandeja y viven en Ajustes. En Android —donde corren
     * estas pruebas— la ficha se llama «Captura del banco».
     */
    @Test
    fun `la captura del banco vive en Ajustes`() {
        var navegoA: Screen? = null
        composeRule.setContent {
            MoviTheme { MasScreen(onNavigate = { navegoA = it }) }
        }

        composeRule.onNodeWithText("Captura del banco", useUnmergedTree = true).performClick()

        assertEquals(Screen.CapturaDelBanco, navegoA)
        composeRule.onNodeWithText("Mensajes del banco", useUnmergedTree = true).assertDoesNotExist()
    }

    // ── Ola B, tarea 7: Metas y Extractos salieron; Primeros pasos es condicional ──────

    @Test
    fun `Ajustes no ofrece Metas ni Extractos, y Documentos sigue ahi`() {
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
