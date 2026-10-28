package com.jvillada.movi.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * **Vuelta 45 · «En qué se va» abre sin elegir.** Abría con «25 oct · $0 · período pasado $0»: el clic que
 * la abrió dejó el puntero donde quedó la gráfica, y el movimiento sintético de un puntero quieto la
 * elegía. Ahora el mouse elige solo cuando de verdad se mueve; el dedo, como siempre.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w390dp-h800dp-xhdpi")
class LaGraficaAbreSinElegirTest {

    @get:Rule val composeRule = createComposeRule()

    private var elegido: Int? = null

    private fun montar() {
        elegido = null
        composeRule.setContent {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .testTag("grafica")
                    .graficaQueSeToca(cuantos = 31, datos = Unit, alElegir = { elegido = it }),
            )
        }
    }

    @Test
    fun `un puntero que llega quieto no elige nada`() {
        montar()
        composeRule.onNodeWithTag("grafica").performMouseInput {
            enter(Offset(width * 0.8f, height / 2f))
            moveTo(Offset(width * 0.8f, height / 2f))
        }
        composeRule.waitForIdle()
        assertNull(elegido, "la gráfica abre sin elegir: la leyenda dice el resumen")
    }

    @Test
    fun `el mouse que se mueve si elige, y al salir se suelta`() {
        montar()
        composeRule.onNodeWithTag("grafica").performMouseInput {
            enter(Offset(width * 0.8f, height / 2f))
            moveTo(Offset(width * 0.1f, height / 2f))
        }
        composeRule.waitForIdle()
        assertEquals(3, elegido)
        composeRule.onNodeWithTag("grafica").performMouseInput { exit(Offset(-10f, -10f)) }
        composeRule.waitForIdle()
        assertNull(elegido)
    }

    @Test
    fun `el movimiento sintetico es el que no viene de otro lugar`() {
        assertFalse(elMouseSeMovio(null, Offset(10f, 10f)), "el primero que llega no elige")
        assertFalse(elMouseSeMovio(Offset(10f, 10f), Offset(10f, 10f)), "el que repite la posición tampoco")
        assertTrue(elMouseSeMovio(Offset(10f, 10f), Offset(12f, 10f)))
    }
}
