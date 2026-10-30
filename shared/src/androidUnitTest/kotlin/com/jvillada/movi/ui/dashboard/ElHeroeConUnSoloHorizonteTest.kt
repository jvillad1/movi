package com.jvillada.movi.ui.dashboard

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.RepositorioDePrueba
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.FinanceSummary
import com.jvillada.movi.shared.model.GastoDelDiaADia
import com.jvillada.movi.shared.model.PeriodSettings
import com.jvillada.movi.shared.model.PeriodoFinanciero
import com.jvillada.movi.shared.model.Scope
import com.jvillada.movi.shared.model.ScreenSection
import com.jvillada.movi.theme.MoviTheme
import com.jvillada.movi.ui.sdui.HeroDeUnVistazo
import kotlinx.datetime.LocalDate
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * # Vuelta 78 · el héroe habla de un solo horizonte
 *
 * «Te quedan $1.450.950 · Para 2 días · $725.475 por día» iba encima de «No te alcanza: el 29 nov
 * quedarías en −$1,1M» (`r6-carga-inicio-8000.png`). Ahora la línea de los días se calla a tres días del
 * cierre o si «¿Me alcanza?» dice que no.
 *
 * Período del dueño con corte 25 (25-ago al 24-sep de 2026), $10M de ingresos y sin salario anotado: la
 * caja mira hasta el 25-sep.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w390dp-h2400dp-xhdpi")
class ElHeroeConUnSoloHorizonteTest {

    @get:Rule val composeRule = createComposeRule()

    private fun datos() = DashboardData(
        summary = FinanceSummary(scope = Scope.SELF, balance = 0, ingresos = 10_000_000, egresos = 4_000_000),
        accounts = listOf(Account(id = "a1", name = "Ahorros", type = AccountType.SAVINGS, balance = 2_345_678)),
        upcoming = emptyList(),
        ocurrencias = emptyList(),
        gastoVariablePorDia = mapOf("2026-08-26" to 100_000L),
        ajustesDePeriodo = PeriodSettings(cutoffDay = 25),
        periodoActual = PeriodoFinanciero(2026, 9),
    )

    private fun montar(hoy: LocalDate, gastoPorDia: Long) {
        Repositories.sustitutoDePrueba = object : RepositorioDePrueba() {
            override suspend fun getGastoDelDiaADia(): GastoDelDiaADia = GastoDelDiaADia(porDia = gastoPorDia)
        }
        composeRule.setContent {
            MoviTheme {
                Column(Modifier.fillMaxSize()) {
                    HeroDeUnVistazo(section = ScreenSection(type = "HERO_BALANCE"), data = datos(), onNavigate = {}, hoy = hoy)
                }
            }
        }
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag(TAG_ME_ALCANZA, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @After
    fun limpiar() {
        Repositories.sustitutoDePrueba = null
    }

    private fun hay(texto: String) =
        composeRule.onAllNodesWithText(texto, substring = true, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun lineasDeDetalle() =
        composeRule.onAllNodesWithTag(TAG_DETALLE_DEL_HEROE, useUnmergedTree = true).fetchSemanticsNodes().size

    @Test
    fun `lejos del cierre y con plata para el periodo, dice para cuantos dias`() {
        montar(LocalDate(2026, 9, 21), gastoPorDia = 20_000)
        assertTrue(hay("Te alcanza"), "el renglón dice que alcanza")
        assertTrue(hay("Para 4 días"), "a cuatro días del cierre, la línea de los días va")
        assertEquals(1, lineasDeDetalle())
    }

    @Test
    fun `a tres dias del cierre la linea de los dias se calla`() {
        montar(LocalDate(2026, 9, 22), gastoPorDia = 20_000)
        assertTrue(hay("Te quedan"))
        assertTrue(!hay("Para 3 días"))
        assertTrue(!hay("por día"))
        assertEquals(0, lineasDeDetalle())
    }

    @Test
    fun `si no alcanza, el heroe no dice cuanto por dia`() {
        montar(LocalDate(2026, 9, 21), gastoPorDia = 2_000_000)
        assertTrue(hay("No te alcanza"), "el renglón dice que no alcanza")
        assertTrue(hay("Te quedan"))
        assertTrue(!hay("por día"), "y la línea de los días se calla")
        assertEquals(0, lineasDeDetalle())
    }

    /** Revisión de la 78: la tarjeta «Cuánto puedes gastar» de Pagos calla la misma línea, con la misma regla. */
    @Test
    fun `Pagos calla la linea de los dias con la misma regla`() {
        val hoy = LocalDate(2026, 9, 21)
        val disponible = disponibleDelInicio(datos(), hoy)!!
        val noAlcanza = RenglonMeAlcanza("No te alcanza", "el 24 sep quedarías en −$1M", enRojo = true)
        var renglon by androidx.compose.runtime.mutableStateOf<RenglonMeAlcanza?>(null)
        composeRule.setContent {
            MoviTheme {
                com.jvillada.movi.ui.sdui.TarjetaDelDisponible(titulo = "Cuánto puedes gastar", disponible = disponible, renglon = renglon)
            }
        }
        composeRule.waitForIdle()
        fun detalles() = composeRule.onAllNodesWithTag(com.jvillada.movi.ui.sdui.TAG_DETALLE_TE_QUEDAN_EN_PAGOS, useUnmergedTree = true).fetchSemanticsNodes().size
        assertEquals(1, detalles())
        assertTrue(hay("Para 4 días"))
        renglon = noAlcanza
        composeRule.waitForIdle()
        assertEquals(0, detalles(), "si no alcanza, se calla")
        assertTrue(!hay("por día"))
    }
}
