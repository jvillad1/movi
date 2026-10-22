package com.jvillada.movi.ui.navegacion

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import com.jvillada.movi.theme.MoviTheme
import com.jvillada.movi.ui.components.LocalWindowWidthClass
import com.jvillada.movi.ui.components.MinBottomNav
import com.jvillada.movi.ui.components.MinNavRail
import com.jvillada.movi.ui.components.NavTab
import com.jvillada.movi.ui.components.WindowWidthClass
import com.jvillada.movi.ui.quickadd.AtajoDeAgregar
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * # Ola 5 · los atajos del «+»
 *
 * Mantener presionado el «+» abre el menú con los cinco atajos y elegir uno lo avisa; tocar el «+»
 * sigue abriendo «Agregar» sin menú. En la barra del teléfono y en los dos rails. Qué hoja abre cada
 * atajo lo fija `BuscarEnMoviTest` ([com.jvillada.movi.ui.quickadd.pantallaDelAtajo]).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w390dp-h800dp-xhdpi")
class AtajosDelMasTest {

    @get:Rule val composeRule = createComposeRule()

    private var elegida: NavTab? = null
    private var atajo: AtajoDeAgregar? = null

    private fun montarBarra() {
        composeRule.setContent {
            CompositionLocalProvider(LocalWindowWidthClass provides WindowWidthClass.Compact) {
                MoviTheme {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                        MinBottomNav(active = NavTab.HOY, onTabSelected = { elegida = it }, onAtajo = { atajo = it })
                    }
                }
            }
        }
    }

    private fun montarRail(compacto: Boolean) {
        composeRule.setContent {
            val clase = if (compacto) WindowWidthClass.Medium else WindowWidthClass.Expanded
            CompositionLocalProvider(LocalWindowWidthClass provides clase) {
                MoviTheme { MinNavRail(active = NavTab.HOY, onTabSelected = { elegida = it }, compacto = compacto, onAtajo = { atajo = it }) }
            }
        }
    }

    private fun hay(texto: String) = composeRule.onAllNodesWithText(texto, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun mantenerElMas() {
        composeRule.onNodeWithContentDescription("Agregar", useUnmergedTree = true).performTouchInput { longClick() }
        composeRule.waitForIdle()
    }

    @Test
    fun `tocar el mas abre Agregar como siempre, sin menu`() {
        montarBarra()
        composeRule.onNodeWithContentDescription("Agregar", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()

        assertEquals(NavTab.ADD, elegida)
        assertNull(atajo)
        assertTrue(!hay(AtajoDeAgregar.TRASPASO.rotulo))
    }

    @Test
    fun `mantenerlo presionado abre los cinco atajos y elegir uno lo avisa`() {
        montarBarra()
        mantenerElMas()

        AtajoDeAgregar.entries.forEach { assertTrue(hay(it.rotulo), "falta «${it.rotulo}»") }
        assertNull(elegida, "mantener presionado no abre la hoja")

        composeRule.onNodeWithText(AtajoDeAgregar.TRASPASO.rotulo, useUnmergedTree = true).performClick()
        composeRule.waitForIdle()

        assertEquals(AtajoDeAgregar.TRASPASO, atajo)
        assertTrue(!hay(AtajoDeAgregar.PAGO_DE_TARJETA.rotulo), "el menú se cierra")
    }

    private fun elRailTieneLosAtajos(compacto: Boolean) {
        montarRail(compacto)
        mantenerElMas()
        composeRule.onNodeWithText(AtajoDeAgregar.PAGO_DE_TARJETA.rotulo, useUnmergedTree = true).performClick()
        composeRule.waitForIdle()
        assertEquals(AtajoDeAgregar.PAGO_DE_TARJETA, atajo)
    }

    @Test
    fun `el rail ancho tambien tiene los atajos`() = elRailTieneLosAtajos(compacto = false)

    @Test
    fun `el rail compacto tambien tiene los atajos`() = elRailTieneLosAtajos(compacto = true)
}
