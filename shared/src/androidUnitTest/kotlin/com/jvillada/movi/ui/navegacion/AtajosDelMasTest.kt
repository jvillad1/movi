package com.jvillada.movi.ui.navegacion

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onAllNodesWithContentDescription
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
import com.jvillada.movi.ui.components.ATAJOS_FUERA_DEL_INICIO
import com.jvillada.movi.ui.components.LocalAtajosDeAgregar
import com.jvillada.movi.ui.components.MAS_FORMAS_DE_ANOTAR
import com.jvillada.movi.ui.components.MasFormasDeAnotar
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
 * sigue abriendo «Agregar» sin menú. En los dos rails. Qué hoja abre cada atajo lo fija
 * `BuscarEnMoviTest` ([com.jvillada.movi.ui.quickadd.pantallaDelAtajo]).
 *
 * Vuelta 38: la barra del teléfono ya no tiene «+». Ahí, Traspaso, Pago de tarjeta, Repartir y Subir
 * comprobante se abren desde «Más formas de anotar» de Inicio ([MasFormasDeAnotar]), con el mismo menú.
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

    /** La barra del teléfono y, encima, «Más formas de anotar» como lo pone Inicio. */
    private fun montarElTelefono() {
        composeRule.setContent {
            CompositionLocalProvider(
                LocalWindowWidthClass provides WindowWidthClass.Compact,
                LocalAtajosDeAgregar provides { atajo = it },
            ) {
                MoviTheme {
                    Box(Modifier.fillMaxSize()) {
                        MasFormasDeAnotar(modifier = Modifier.align(Alignment.Center))
                        Box(Modifier.align(Alignment.BottomCenter)) {
                            MinBottomNav(active = NavTab.HOY, onTabSelected = { elegida = it })
                        }
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
    fun `la barra del telefono no tiene mas`() {
        montarElTelefono()
        assertTrue(!hay("Agregar"))
        assertEquals(0, composeRule.onAllNodesWithContentDescription("Agregar", useUnmergedTree = true).fetchSemanticsNodes().size)
    }

    /**
     * Sin «+», las cuatro formas de anotar que no son acciones de Inicio siguen a un toque en el
     * teléfono: «Más formas de anotar» abre el menú con Traspaso, Pago de tarjeta, Repartir y Subir
     * comprobante, y elegir una la avisa a la cáscara (que la hace con `hacerElAtajo`).
     */
    @Test
    fun `en el telefono Mas formas de anotar abre las cuatro que no estan en Inicio`() {
        montarElTelefono()
        composeRule.onNodeWithText(MAS_FORMAS_DE_ANOTAR, useUnmergedTree = true).performClick()
        composeRule.waitForIdle()

        ATAJOS_FUERA_DEL_INICIO.forEach { assertTrue(hay(it.rotulo), "falta «${it.rotulo}»") }
        assertEquals(
            listOf(AtajoDeAgregar.TRASPASO, AtajoDeAgregar.PAGO_DE_TARJETA, AtajoDeAgregar.REPARTIR, AtajoDeAgregar.SUBIR_COMPROBANTE),
            ATAJOS_FUERA_DEL_INICIO,
        )
        ATAJOS_FUERA_DEL_INICIO.forEach { elegido ->
            if (!hay(elegido.rotulo)) {
                composeRule.onNodeWithText(MAS_FORMAS_DE_ANOTAR, useUnmergedTree = true).performClick()
                composeRule.waitForIdle()
            }
            composeRule.onNodeWithText(elegido.rotulo, useUnmergedTree = true).performClick()
            composeRule.waitForIdle()
            assertEquals(elegido, atajo)
            assertTrue(!hay(elegido.rotulo), "el menú se cierra")
        }
        assertNull(elegida, "ninguna pestaña se tocó")
    }

    @Test
    fun `sin la cascara Mas formas de anotar no se pinta`() {
        composeRule.setContent { MoviTheme { MasFormasDeAnotar() } }
        assertTrue(!hay(MAS_FORMAS_DE_ANOTAR))
    }

    @Test
    fun `tocar el mas abre Agregar como siempre, sin menu`() {
        montarRail(compacto = true)
        composeRule.onNodeWithContentDescription("Agregar", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()

        assertEquals(NavTab.ADD, elegida)
        assertNull(atajo)
        assertTrue(!hay(AtajoDeAgregar.TRASPASO.rotulo))
    }

    @Test
    fun `mantenerlo presionado abre los seis atajos y elegir uno lo avisa`() {
        montarRail(compacto = true)
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
        montarRail(compacto = true)
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
