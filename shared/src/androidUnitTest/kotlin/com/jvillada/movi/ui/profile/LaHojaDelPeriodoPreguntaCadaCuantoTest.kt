package com.jvillada.movi.ui.profile

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyChild
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performSemanticsAction
import com.jvillada.movi.shared.model.FrecuenciaDelPeriodo
import com.jvillada.movi.shared.model.SiElCorteCaeEnFinDeSemana
import com.jvillada.movi.theme.MoviTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Vuelta 105 · Ajustes → «Inicio del período» pregunta «¿Cada cuánto te pagan?». Con «Cada quincena»
 * propone el 15 y el último día y guarda los dos; con «Cada mes» es la hoja de siempre.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h3200dp-xhdpi")
class LaHojaDelPeriodoPreguntaCadaCuantoTest {

    @get:Rule val composeRule = createComposeRule()

    private fun tocar(matcher: SemanticsMatcher) {
        composeRule.onNode(matcher, useUnmergedTree = true).performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
    }

    private fun guardar() = tocar(hasClickAction() and hasAnyChild(hasText("Guardar")))

    @Test
    fun `elegir cada quincena guarda el 15 y el ultimo dia`() {
        var mes: Int? = null
        var quincena: Triple<Int, Int, SiElCorteCaeEnFinDeSemana>? = null
        composeRule.setContent {
            MoviTheme {
                Box(Modifier.fillMaxSize()) {
                    PeriodoSheet(
                        cutoffActual = 25,
                        reglaActual = SiElCorteCaeEnFinDeSemana.DIA_HABIL_ANTERIOR,
                        onDismiss = {},
                        onSave = { d, _ -> mes = d },
                        onSaveQuincena = { a, b, r -> quincena = Triple(a, b, r) },
                    )
                }
            }
        }
        composeRule.waitForIdle()
        assertTrue(composeRule.onAllNodesWithText(CADA_CUANTO_TE_PAGAN).fetchSemanticsNodes().isNotEmpty())
        tocar(hasTestTag(TAG_FRECUENCIA_QUINCENAL))
        assertTrue(composeRule.onAllNodesWithText(PREGUNTA_DE_LA_QUINCENA).fetchSemanticsNodes().isNotEmpty())
        guardar()
        assertEquals(Triple(15, 31, SiElCorteCaeEnFinDeSemana.DIA_HABIL_ANTERIOR), quincena)
        assertNull(mes)
    }

    @Test
    fun `una quincena guardada se abre elegida, y volver al mes guarda el dia de siempre`() {
        var mes: Int? = null
        composeRule.setContent {
            MoviTheme {
                Box(Modifier.fillMaxSize()) {
                    PeriodoSheet(
                        cutoffActual = 1,
                        onDismiss = {},
                        onSave = { d, _ -> mes = d },
                        frecuenciaActual = FrecuenciaDelPeriodo.QUINCENAL,
                        segundoDiaActual = 16,
                        onSaveQuincena = { _, _, _ -> },
                    )
                }
            }
        }
        composeRule.waitForIdle()
        assertTrue(composeRule.onAllNodesWithText(PREGUNTA_DE_LA_QUINCENA).fetchSemanticsNodes().isNotEmpty())
        tocar(hasTestTag(TAG_FRECUENCIA_MENSUAL))
        guardar()
        assertEquals(1, mes)
    }

    @Test
    fun `sin quien guarde la quincena, la hoja es la de siempre`() {
        composeRule.setContent {
            MoviTheme { Box(Modifier.fillMaxSize()) { PeriodoSheet(cutoffActual = 25, onDismiss = {}, onSave = { _, _ -> }) } }
        }
        composeRule.waitForIdle()
        assertTrue(composeRule.onAllNodesWithText(CADA_CUANTO_TE_PAGAN).fetchSemanticsNodes().isEmpty())
        assertTrue(composeRule.onAllNodesWithText(PREGUNTA_DEL_INICIO_DEL_PERIODO).fetchSemanticsNodes().isNotEmpty())
    }
}
