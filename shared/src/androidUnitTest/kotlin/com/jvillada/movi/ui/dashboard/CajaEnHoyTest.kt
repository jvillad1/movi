package com.jvillada.movi.ui.dashboard

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.height
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.RepositorioDePrueba
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.GastoDelDiaADia
import com.jvillada.movi.shared.model.PaymentStatus
import com.jvillada.movi.shared.model.PeriodSettings
import com.jvillada.movi.shared.model.PeriodoFinanciero
import com.jvillada.movi.shared.model.RecurringRule
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.UpcomingPayment
import com.jvillada.movi.theme.MoviTheme
import com.jvillada.movi.ui.Screen
import com.jvillada.movi.ui.plan.SEGMENTO_PAGOS
import kotlinx.coroutines.CompletableDeferred
import kotlinx.datetime.LocalDate
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * **«Tu día más justo» en Hoy**, montado en un teléfono de 390 dp a la escala de letra de la app:
 * mientras viaja el supuesto del gasto del día a día la fila se pinta con su forma y sin cifras, y al
 * llegar no cambia de alto (máximo 8 dp, la regla de `PlanScreenTest`). Tocarla lleva a Plan.
 *
 * Octubre del dueño (corte 25: 25-sep → 24-oct), hoy 13-oct, $2M en la cuenta, el arriendo de $800.000
 * el 20 y $20.000 de gasto diario: el día más justo es el 24, con $980.000.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w390dp-h844dp-xhdpi")
class CajaEnHoyTest {

    @get:Rule val composeRule = createComposeRule()

    private val hoy = LocalDate(2026, 10, 13)
    private val puerta = CompletableDeferred<Unit>()
    private val navegado = mutableListOf<Screen>()

    private fun datos(saldo: Long) = DashboardData(
        accounts = listOf(Account("acc-banco", "Bancolombia", AccountType.SAVINGS, saldo, "COP")),
        upcoming = listOf(
            UpcomingPayment(
                rule = RecurringRule(
                    id = "rr_arriendo", name = "Arriendo", category = "Vivienda", amount = 800_000L,
                    dayOfMonth = 20, type = TransactionType.EXPENSE,
                ),
                dueDate = "2026-10-20",
                daysUntil = 7,
                status = PaymentStatus.UPCOMING,
            ),
        ),
        ocurrencias = emptyList(),
        ajustesDePeriodo = PeriodSettings(cutoffDay = 25),
        periodoActual = PeriodoFinanciero(2026, 10),
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
                val base = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(base.density, base.fontScale * 1.12f)) {
                    Box(Modifier.fillMaxSize()) {
                        CajaEnHoy(data = data, hoy = hoy, onNavigate = { navegado += it })
                    }
                }
            }
        }
        composeRule.waitForIdle()
    }

    @After
    fun limpiar() {
        Repositories.sustitutoDePrueba = null
    }

    private fun hay(texto: String): Boolean =
        composeRule.onAllNodesWithText(texto, substring = true, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun contarTag(tag: String): Int =
        composeRule.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().size

    @Test
    fun `cargando no inventa cifras y al llegar el dato la fila no salta`() {
        montar(datos(saldo = 2_000_000L))

        assertEquals(1, contarTag(TAG_ESQUELETO_CAJA_EN_HOY))
        assertTrue(!hay("\$"), "ninguna cifra antes de leer el supuesto")
        assertTrue(hay(ROTULO_CAJA_SIN_DATO))
        val altoCargando = composeRule.onNodeWithTag(TAG_CAJA_EN_HOY).getUnclippedBoundsInRoot().height

        puerta.complete(Unit)
        composeRule.waitForIdle()

        assertEquals(0, contarTag(TAG_ESQUELETO_CAJA_EN_HOY))
        assertTrue(hay("Tu día más justo del período"))
        assertTrue(hay("El 24 de octubre quedas en"))
        assertTrue(hay("\$980.000"))
        val altoCargado = composeRule.onNodeWithTag(TAG_CAJA_EN_HOY).getUnclippedBoundsInRoot().height
        val diferencia = abs(altoCargado.value - altoCargando.value)
        assertTrue(
            diferencia <= 8f,
            "La fila mide ${altoCargando.value} dp cargando y ${altoCargado.value} dp cargada: $diferencia dp, el máximo son 8",
        )
    }

    @Test
    fun `si algun dia queda en rojo lo dice, y tocarla abre Plan`() {
        montar(datos(saldo = 1_000_000L))
        puerta.complete(Unit)
        composeRule.waitForIdle()

        assertTrue(hay("Quedarías en rojo"))
        assertTrue(hay("El 24 de octubre quedarías en"))
        composeRule.onNodeWithTag(TAG_CAJA_EN_HOY).performClick()
        assertEquals(listOf<Screen>(Screen.Plan(SEGMENTO_PAGOS)), navegado)
    }

    @Test
    fun `sin la lista del periodo no hay fila`() {
        montar(datos(saldo = 2_000_000L).copy(upcoming = null))
        assertEquals(0, contarTag(TAG_CAJA_EN_HOY))
    }
}
