package com.jvillada.movi.ui.dashboard

import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import com.jvillada.movi.ConClaseDeAncho
import com.jvillada.movi.EsqueletoDeLaCascara
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.CardSummary
import com.jvillada.movi.shared.model.CreditSummary
import com.jvillada.movi.shared.model.FinanceSummary
import com.jvillada.movi.shared.model.PeriodSettings
import com.jvillada.movi.shared.model.PeriodoFinanciero
import com.jvillada.movi.shared.model.Scope
import com.jvillada.movi.shared.model.defaultDashboardDefinition
import com.jvillada.movi.ui.Screen
import com.jvillada.movi.ui.components.NavTab
import com.jvillada.movi.ui.components.RelevoDeScroll
import com.jvillada.movi.ui.components.ds.TAG_TARJETA_DE_CUENTA
import com.jvillada.movi.ui.sdui.SduiRenderer
import com.jvillada.movi.ui.sdui.TAG_CARRUSEL_DE_CUENTAS
import com.jvillada.movi.ui.sdui.TAG_GRILLA_DE_CUENTAS
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * # «Tus cuentas» en grilla en pantalla ancha (vuelta de diseño 9), montada y medida
 *
 * A 1280 px la sección seguía siendo un carrusel con la segunda tarjeta cortada a la derecha. Fuera
 * del teléfono va en grilla ([tarjetasPorRenglon]): 2 por renglón en una columna del Inicio en dos, 3
 * en la columna de lectura. En el teléfono, el carrusel de siempre.
 *
 * Dentro de la cáscara de verdad (el rail y el tope del tablero cuentan) y con la letra ×1,12 de la
 * app ([ConClaseDeAncho]). Se mide con `boundsInRoot` en `mdpi`; `sdk = [34]` y `NATIVE` para que el
 * texto se mida con el motor real.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class CuentasDelInicioEnGrillaTest {

    @get:Rule val composeRule = createComposeRule()

    private val cuentas = listOf(
        Account("a1", "Bancolombia Ahorros", AccountType.SAVINGS, 13_845_800),
        Account("a2", "Nu", AccountType.SAVINGS, 2_890_000),
        Account("a3", "Efectivo", AccountType.CASH, 120_000),
        // Un nombre que va en dos renglones: su tarjeta crece, y su vecina con ella.
        Account("a4", "Fiducuenta Bancolombia Inversión Virtual", AccountType.INVESTMENT, 113_264),
    )

    private val datos = DashboardData(
        summary = FinanceSummary(scope = Scope.SELF, balance = 0, ingresos = 10_000_000, egresos = 4_000_000),
        accounts = cuentas,
        credits = emptyList<CreditSummary>(),
        cards = emptyList<CardSummary>(),
        upcoming = emptyList(),
        ocurrencias = emptyList(),
        periodoActual = PeriodoFinanciero(2026, 9),
        ajustesDePeriodo = PeriodSettings(),
        spentByCategory = mapOf("Mercado" to 392_100L),
    )

    private fun montar() {
        composeRule.setContent {
            ConClaseDeAncho {
                EsqueletoDeLaCascara(
                    pantalla = Screen.Dashboard,
                    activeTab = NavTab.HOY,
                    conNavegacion = true,
                    onTabSelected = {},
                    relevoDeScroll = remember { RelevoDeScroll() },
                ) {
                    SduiRenderer(
                        definition = defaultDashboardDefinition(),
                        data = datos,
                        modifier = Modifier.fillMaxSize(),
                        onNavigate = {},
                    )
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun hayTag(tag: String): Boolean =
        composeRule.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun limites(tag: String): Rect =
        composeRule.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot

    private fun tarjetas(): List<Rect> =
        composeRule.onAllNodesWithTag(TAG_TARJETA_DE_CUENTA, useUnmergedTree = true).fetchSemanticsNodes().map { it.boundsInRoot }

    /** Todas las tarjetas, enteras dentro de la grilla, de a [porRenglon] por renglón y parejas. */
    private fun comprobarGrilla(porRenglon: Int) {
        assertTrue(hayTag(TAG_GRILLA_DE_CUENTAS), "fuera del teléfono, una grilla")
        assertTrue(!hayTag(TAG_CARRUSEL_DE_CUENTAS), "y no el carrusel")
        val grilla = limites(TAG_GRILLA_DE_CUENTAS)
        val todas = tarjetas()
        assertEquals(cuentas.size, todas.size, "se ven todas las cuentas, no solo la primera y la que asoma")
        todas.forEach {
            assertTrue(it.left >= grilla.left - 0.5f && it.right <= grilla.right + 0.5f, "ninguna tarjeta cortada: $it en $grilla")
        }
        todas.chunked(porRenglon).forEach { renglon ->
            renglon.forEach { t ->
                assertEquals(renglon.first().top, t.top, 0.5f, "un renglón arranca a la misma altura")
                assertEquals(renglon.first().height, t.height, 0.5f, "y sus tarjetas miden lo mismo de alto")
                // El ancho se reparte en píxeles enteros: una puede llevarse uno de más.
                assertEquals(todas.first().width, t.width, 1.5f, "todas miden lo mismo de ancho")
            }
        }
        val renglones = todas.chunked(porRenglon)
        if (renglones.size > 1) {
            assertTrue(renglones[1].first().top > renglones[0].first().bottom, "el renglón siguiente va debajo")
        }
    }

    /** 1280 − 216 = 1064: el Inicio en dos columnas de 532; las cuentas, a la derecha, de a dos. */
    @Test
    @Config(qualifiers = "w1280dp-h2400dp-mdpi")
    fun `a 1280 las cuentas van en grilla de dos sin ninguna cortada`() {
        montar()
        comprobarGrilla(porRenglon = 2)
    }

    /** 1024 − 216 = 808: el Inicio en una columna de lectura (720), y caben tres por renglón. */
    @Test
    @Config(qualifiers = "w1024dp-h2400dp-mdpi")
    fun `a 1024 en la columna de lectura van de a tres`() {
        montar()
        comprobarGrilla(porRenglon = 3)
    }

    @Test
    @Config(qualifiers = "w390dp-h2400dp-mdpi")
    fun `a 390 sigue el carrusel con la siguiente asomando`() {
        montar()
        assertTrue(hayTag(TAG_CARRUSEL_DE_CUENTAS))
        assertTrue(!hayTag(TAG_GRILLA_DE_CUENTAS))
        val carrusel = composeRule.onNodeWithTag(TAG_CARRUSEL_DE_CUENTAS, useUnmergedTree = true).getUnclippedBoundsInRoot()
        val segunda = composeRule.onAllNodesWithTag(TAG_TARJETA_DE_CUENTA, useUnmergedTree = true)[1].getUnclippedBoundsInRoot()
        assertTrue(segunda.left < carrusel.right, "la segunda empieza adentro de la pantalla")
        assertTrue(segunda.right > carrusel.right, "y asoma cortada, a propósito: hay más deslizando")
    }
}
