package com.jvillada.movi.ui.dashboard

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performSemanticsAction
import com.jvillada.movi.shared.model.PreferenciasDelInicio
import com.jvillada.movi.shared.model.defaultDashboardDefinition
import com.jvillada.movi.shared.model.seccionesConfigurablesDe
import com.jvillada.movi.theme.MoviTheme
import com.jvillada.movi.ui.components.ds.TAG_BOTON
import com.jvillada.movi.ui.components.ds.VarianteDeBoton
import com.jvillada.movi.ui.components.ds.VarianteDeBotonKey
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * # Vuelta 41: «Volver al orden de Movi» solo cuando hay algo que deshacer
 *
 * Con el Inicio en el orden de Movi el botón se pintaba apagado, en el marrón del texto apagado: se
 * leía como roto. Ahora no se pinta; con algo cambiado es un `Boton` `Fantasma`, en el azul del enlace.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w390dp-h2400dp")
class VolverAlOrdenDeMoviTest {

    @get:Rule val composeRule = createComposeRule()

    private val deMovi = PreferenciasDelInicio(ordenDeMovi = seccionesConfigurablesDe(defaultDashboardDefinition()))

    private fun montar(inicial: PreferenciasDelInicio): () -> PreferenciasDelInicio {
        var preferencias by mutableStateOf(inicial)
        composeRule.setContent {
            MoviTheme {
                Column { EditorDelInicio(preferencias, noSeGuardo = false, onCambiar = { preferencias = it }) }
            }
        }
        composeRule.waitForIdle()
        return { preferencias }
    }

    private fun elBoton() = composeRule.onAllNodes(hasTestTag(TAG_BOTON) and hasText(VOLVER_AL_ORDEN_DE_MOVI)).fetchSemanticsNodes()

    @Test
    fun `en el orden de Movi el boton no se pinta`() {
        montar(deMovi)
        assertTrue(elBoton().isEmpty(), "no hay nada que deshacer")
        assertTrue(
            composeRule.onAllNodes(hasText(VOLVER_AL_ORDEN_DE_MOVI), useUnmergedTree = true).fetchSemanticsNodes().isEmpty(),
            "ni apagado",
        )
    }

    @Test
    fun `con algo cambiado es un boton de texto que restaura`() {
        val actuales = montar(deMovi.copy(ocultas = listOf("CUENTAS")))
        val boton = elBoton().single()
        assertEquals(VarianteDeBoton.Fantasma, boton.config.getOrNull(VarianteDeBotonKey))
        assertTrue(boton.config.getOrNull(SemanticsProperties.Disabled) == null, "se puede tocar")

        composeRule.onNode(hasTestTag(TAG_BOTON) and hasText(VOLVER_AL_ORDEN_DE_MOVI)).performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()

        assertTrue(actuales().esLaDeMovi, "vuelve al orden de Movi")
        assertTrue(elBoton().isEmpty(), "y el botón se va")
    }
}
