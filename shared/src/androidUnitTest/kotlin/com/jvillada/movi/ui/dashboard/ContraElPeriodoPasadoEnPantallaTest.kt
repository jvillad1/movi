package com.jvillada.movi.ui.dashboard

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.jvillada.movi.theme.MoviTheme
import com.jvillada.movi.ui.sdui.WidgetEnQueSeVa
import com.jvillada.movi.ui.sdui.TAG_FLECHA_CONTRA_EL_ANTERIOR
import com.jvillada.movi.ui.sdui.TAG_DIFERENCIA_DEL_WIDGET
import kotlin.test.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * **El widget «En qué se va» contra el período pasado**, montado en un teléfono de 390 dp (vuelta de
 * diseño 7: antes el rótulo de la derecha se comía el título y cada fila llevaba una flecha sin
 * número). El título se ve, la comparación se dice una vez, y cada categoría dice su monto y su
 * diferencia en palabras —«$120.000 más», «nuevo»—, con la frase entera para el lector de pantalla.
 * Sin período pasado no se dice nada de la comparación.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w390dp-h844dp-xhdpi")
class ContraElPeriodoPasadoEnPantallaTest {

    @get:Rule val composeRule = createComposeRule()

    private val navegado = mutableListOf<com.jvillada.movi.ui.Screen>()

    private fun montar(data: DashboardData) {
        composeRule.setContent {
            MoviTheme {
                Box(Modifier.fillMaxSize()) {
                    WidgetEnQueSeVa(data = data, onNavigate = { navegado += it })
                }
            }
        }
    }

    private fun diferencias(): List<String> =
        composeRule.onAllNodesWithTag(TAG_DIFERENCIA_DEL_WIDGET, useUnmergedTree = true).fetchSemanticsNodes()
            .map { n -> n.config[androidx.compose.ui.semantics.SemanticsProperties.Text].joinToString("") { it.text } }

    @Test
    fun `el widget tiene titulo y cada fila dice su diferencia o nuevo`() {
        montar(
            DashboardData(
                spentByCategory = mapOf("Comida" to 300_000L, "Mercado" to 100_000L, "Ropa" to 80_000L),
                gastoDelAnterior = mapOf("Comida" to 180_000L, "Mercado" to 150_000L),
                diasComparados = 12,
            ),
        )
        composeRule.onNodeWithText("En qué se va", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText(CONTRA_EL_PERIODO_PASADO, useUnmergedTree = true).assertIsDisplayed()
        // En el orden de las categorías (de la que más pesa a la que menos), sin flechas mudas.
        assertEquals(listOf("\$120.000 más", "\$50.000 menos", "nuevo"), diferencias())
        composeRule.onAllNodesWithTag(TAG_FLECHA_CONTRA_EL_ANTERIOR, useUnmergedTree = true).assertCountEquals(0)
        // La frase entera es para el lector de pantalla.
        composeRule.onNodeWithContentDescription("\$50.000 menos que a esta altura del período pasado", useUnmergedTree = true)
            .assertIsDisplayed()
        composeRule.onNodeWithContentDescription("\$120.000 más que a esta altura del período pasado", useUnmergedTree = true)
            .assertIsDisplayed()
        // Y el monto de este período, entero, con la Cifra del sistema.
        composeRule.onNodeWithText("\$300.000", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("vs. período pasado", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun `sin periodo pasado no hay diferencias ni la linea de la comparacion`() {
        montar(DashboardData(spentByCategory = mapOf("Comida" to 300_000L)))
        assertEquals(emptyList(), diferencias())
        composeRule.onNodeWithText(CONTRA_EL_PERIODO_PASADO, useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNodeWithText("En qué se va", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("Comida", useUnmergedTree = true).assertIsDisplayed()
    }

    /** Ola 4 · Ver la plata: el widget entero abre «En qué se va». */
    @Test
    fun `el widget abre el analisis del periodo`() {
        montar(DashboardData(spentByCategory = mapOf("Comida" to 300_000L)))
        composeRule.onNodeWithTag(com.jvillada.movi.ui.sdui.TAG_TARJETA_EN_QUE_SE_VA).performClick()
        composeRule.waitForIdle()
        kotlin.test.assertEquals(com.jvillada.movi.ui.Screen.EnQueSeVa(), navegado.last())
    }
}
