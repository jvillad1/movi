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
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.rightClick
import androidx.compose.ui.test.onNodeWithTag
import com.jvillada.movi.ui.components.TAG_FLECHA_DE_AGREGAR
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
 * Mantener presionado el «+» abre el menú con los seis atajos y elegir uno lo avisa; tocar el «+»
 * sigue abriendo «Agregar» sin menú. En la barra del teléfono y en los dos rails. Qué hoja abre cada
 * atajo lo fija `BuscarEnMoviTest` ([com.jvillada.movi.ui.quickadd.pantallaDelAtajo]).
 *
 * Vuelta de diseño 26: el menú tiene las cuatro acciones que salieron de la cuadrícula de Hoy
 * (Traspaso, Pago de tarjeta, Repartir, Subir comprobante), y en la web y el escritorio se abre
 * también con el clic derecho y con la flecha de «Agregar» del rail ancho.
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
    fun `mantenerlo presionado abre los seis atajos y elegir uno lo avisa`() {
        montarBarra()
        mantenerElMas()

        AtajoDeAgregar.entries.forEach { assertTrue(hay(it.rotulo), "falta «${it.rotulo}»") }
        assertNull(elegida, "mantener presionado no abre la hoja")

        composeRule.onNodeWithText(AtajoDeAgregar.TRASPASO.rotulo, useUnmergedTree = true).performClick()
        composeRule.waitForIdle()

        assertEquals(AtajoDeAgregar.TRASPASO, atajo)
        assertTrue(!hay(AtajoDeAgregar.PAGO_DE_TARJETA.rotulo), "el menú se cierra")
    }

    @Test
    fun `el menu del mas tiene las cuatro que salieron de Hoy`() {
        montarBarra()
        mantenerElMas()
        listOf("Traspaso", "Pago de tarjeta", "Repartir", "Subir comprobante").forEach {
            assertEquals(1, composeRule.onAllNodesWithText(it, useUnmergedTree = true).fetchSemanticsNodes().size, "«$it» una vez")
        }
        composeRule.onNodeWithText(AtajoDeAgregar.REPARTIR.rotulo, useUnmergedTree = true).performClick()
        composeRule.waitForIdle()
        assertEquals(AtajoDeAgregar.REPARTIR, atajo)
    }

    /** En la web y el escritorio: el clic derecho sobre el «+» abre el mismo menú, y no abre «Agregar». */
    @Test
    fun `el clic derecho sobre el mas abre los atajos`() {
        montarRail(compacto = true)
        composeRule.onNodeWithContentDescription("Agregar", useUnmergedTree = true).performMouseInput { rightClick() }
        composeRule.waitForIdle()

        AtajoDeAgregar.entries.forEach { assertTrue(hay(it.rotulo), "falta «${it.rotulo}»") }
        assertNull(elegida, "el clic derecho no abre la hoja")
    }

    /** El rail ancho lleva «Agregar ▾»: la flecha abre los atajos de un clic. */
    @Test
    fun `la flecha de Agregar en el rail ancho abre los atajos`() {
        montarRail(compacto = false)
        composeRule.onNodeWithTag(TAG_FLECHA_DE_AGREGAR, useUnmergedTree = true).performClick()
        composeRule.waitForIdle()
        assertNull(elegida, "la flecha no abre la hoja")
        composeRule.onNodeWithText(AtajoDeAgregar.TRASPASO.rotulo, useUnmergedTree = true).performClick()
        composeRule.waitForIdle()
        assertEquals(AtajoDeAgregar.TRASPASO, atajo)

        // Y tocar «Agregar» sigue abriendo la hoja.
        composeRule.onNodeWithText("Agregar", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()
        assertEquals(NavTab.ADD, elegida)
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
