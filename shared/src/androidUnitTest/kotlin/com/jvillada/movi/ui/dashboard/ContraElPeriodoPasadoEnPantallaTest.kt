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
import com.jvillada.movi.shared.model.ScreenSection
import com.jvillada.movi.theme.MoviTheme
import com.jvillada.movi.ui.sdui.GastoPorCategoriaSection
import com.jvillada.movi.ui.sdui.TAG_FLECHA_CONTRA_EL_ANTERIOR
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * **«¿En qué se va?» con flechas**, montado en un teléfono de 390 dp: cada categoría dice si va por
 * encima o por debajo del período pasado a la misma altura, el pie dice contra cuántos días, y sin
 * comparación no se pinta ninguna flecha.
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
                    GastoPorCategoriaSection(
                        section = ScreenSection(type = "GASTO_POR_CATEGORIA", title = "En qué se va"),
                        data = data,
                        onNavigate = { navegado += it },
                    )
                }
            }
        }
    }

    @Test
    fun `cada categoria lleva su flecha y el pie dice contra que se compara`() {
        montar(
            DashboardData(
                spentByCategory = mapOf("Comida" to 300_000L, "Mercado" to 100_000L),
                gastoDelAnterior = mapOf("Comida" to 180_000L, "Mercado" to 150_000L),
                diasComparados = 12,
            ),
        )
        composeRule.onAllNodesWithTag(TAG_FLECHA_CONTRA_EL_ANTERIOR, useUnmergedTree = true).assertCountEquals(2)
        composeRule.onNodeWithText("↑ \$120.000", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("↓ \$50.000", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithContentDescription("\$120.000 más que a esta altura del período pasado", useUnmergedTree = true)
            .assertIsDisplayed()
        composeRule.onNodeWithText("Las flechas comparan con los primeros 12 días del período pasado.", useUnmergedTree = true)
            .assertIsDisplayed()
    }

    @Test
    fun `sin periodo pasado no hay flechas ni pie`() {
        montar(DashboardData(spentByCategory = mapOf("Comida" to 300_000L)))
        composeRule.onAllNodesWithTag(TAG_FLECHA_CONTRA_EL_ANTERIOR, useUnmergedTree = true).assertCountEquals(0)
        composeRule.onNodeWithText("Comida", useUnmergedTree = true).assertIsDisplayed()
    }

    /** Ola 4 · Ver la plata: «Ver todas» y la tarjeta abren «En qué se va». */
    @Test
    fun `ver todas y la tarjeta abren el analisis del periodo`() {
        montar(DashboardData(spentByCategory = mapOf("Comida" to 300_000L)))
        composeRule.onNodeWithText("Ver todas", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()
        kotlin.test.assertEquals(com.jvillada.movi.ui.Screen.EnQueSeVa(), navegado.last())
        navegado.clear()
        composeRule.onNodeWithTag(com.jvillada.movi.ui.sdui.TAG_TARJETA_EN_QUE_SE_VA).performClick()
        composeRule.waitForIdle()
        kotlin.test.assertEquals(com.jvillada.movi.ui.Screen.EnQueSeVa(), navegado.last())
    }
}
