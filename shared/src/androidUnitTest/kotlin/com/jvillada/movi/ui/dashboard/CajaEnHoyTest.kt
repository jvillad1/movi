package com.jvillada.movi.ui.dashboard

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.RepositorioDePrueba
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.GastoDelDiaADia
import com.jvillada.movi.shared.model.PeriodSettings
import com.jvillada.movi.shared.model.SECCIONES_OPCIONALES
import com.jvillada.movi.shared.model.defaultDashboardDefinition
import com.jvillada.movi.shared.model.periodoActual
import kotlinx.datetime.Clock
import com.jvillada.movi.theme.MoviTheme
import com.jvillada.movi.ui.Screen
import com.jvillada.movi.ui.plan.SEGMENTO_PAGOS
import com.jvillada.movi.ui.sdui.SduiRenderer
import com.jvillada.movi.ui.sdui.TAG_HEROE_EN_TARJETA
import kotlinx.coroutines.CompletableDeferred
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * # Vuelta 75 · «¿Me alcanza?» en el héroe de Inicio
 *
 * El Inicio por defecto —con «Disponible» oculto, que es una sección opcional— montado con el renderer
 * SDUI de verdad: el renglón está igual, dentro del héroe, y tocarlo abre Pagos en la caja. Sin la lista
 * del período, o mientras el supuesto del gasto del día a día no llega, no se pinta.
 *
 * El período del dueño (corte 25) que contiene hoy —el héroe usa el reloj de la app—, sin salario: la caja
 * llega al arranque del siguiente (vuelta 75), con $20.000 de gasto diario.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w390dp-h844dp-xhdpi")
class CajaEnHoyTest {

    @get:Rule val composeRule = createComposeRule()

    private val puerta = CompletableDeferred<Unit>()
    private val navegado = mutableListOf<Screen>()

    private val ajustes = PeriodSettings(cutoffDay = 25)

    /** El período de hoy (el héroe usa el reloj de la app): la caja mira desde hoy hasta el siguiente. */
    private fun datos(saldo: Long) = DashboardData(
        accounts = listOf(Account("acc-banco", "Bancolombia", AccountType.SAVINGS, saldo, "COP")),
        upcoming = emptyList(),
        ocurrencias = emptyList(),
        ajustesDePeriodo = ajustes,
        periodoActual = periodoActual(Clock.System.now().toEpochMilliseconds(), ajustes),
    )

    private fun montar(data: DashboardData) {
        Repositories.sustitutoDePrueba = object : RepositorioDePrueba() {
            override suspend fun getGastoDelDiaADia(): GastoDelDiaADia {
                puerta.await()
                return GastoDelDiaADia(porDia = 20_000L)
            }
        }
        composeRule.setContent {
            MoviTheme {
                Box(Modifier.fillMaxSize()) {
                    SduiRenderer(
                        definition = defaultDashboardDefinition(),
                        data = data,
                        modifier = Modifier.fillMaxSize(),
                        onNavigate = { navegado += it },
                    )
                }
            }
        }
        composeRule.waitForIdle()
    }

    @After
    fun limpiar() {
        Repositories.sustitutoDePrueba = null
    }

    private fun contarTag(tag: String): Int =
        composeRule.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().size

    @Test
    fun `con Disponible oculto el renglon esta en el heroe, y tocarlo abre Pagos en la caja`() {
        assertTrue("DISPONIBLE_DEL_PERIODO" in SECCIONES_OPCIONALES)
        assertTrue(
            defaultDashboardDefinition().sections.none { it.type == "DISPONIBLE_DEL_PERIODO" },
            "el Inicio por defecto no pinta Disponible",
        )
        montar(datos(saldo = 2_000_000L))
        assertEquals(1, contarTag(TAG_HEROE_EN_TARJETA))
        assertEquals(0, contarTag(TAG_ME_ALCANZA), "mientras viaja el supuesto, el renglón no afirma nada")

        puerta.complete(Unit)
        composeRule.waitUntil(5_000) { contarTag(TAG_ME_ALCANZA) == 1 }
        val textos = composeRule.onAllNodesWithText("Te alcanza hasta el", substring = true, useUnmergedTree = true)
            .fetchSemanticsNodes()
        assertEquals(1, textos.size, "el renglón dice hasta cuándo alcanza")
        assertTrue(
            composeRule.onAllNodesWithText("Tu día más justo, ", substring = true, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty(),
        )

        composeRule.onNodeWithTag(TAG_ME_ALCANZA, useUnmergedTree = true).performScrollTo().performClick()
        assertEquals(listOf<Screen>(Screen.Plan(SEGMENTO_PAGOS, enLaCaja = true)), navegado)
    }

    @Test
    fun `si no alcanza lo dice con la cifra del peor dia`() {
        // Una cuenta en rojo: lo libre ya es negativo hoy.
        montar(datos(saldo = -500_000L))
        puerta.complete(Unit)
        composeRule.waitUntil(5_000) { contarTag(TAG_ME_ALCANZA) == 1 }
        assertTrue(
            composeRule.onAllNodesWithText("No te alcanza", substring = true, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty(),
        )
    }

    @Test
    fun `sin la lista del periodo no hay renglon`() {
        montar(datos(saldo = 2_000_000L).copy(upcoming = null))
        puerta.complete(Unit)
        composeRule.waitForIdle()
        assertEquals(1, contarTag(TAG_HEROE_EN_TARJETA))
        assertEquals(0, contarTag(TAG_ME_ALCANZA))
    }
}
