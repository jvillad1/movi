package com.jvillada.movi.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import com.jvillada.movi.ConClaseDeAncho
import com.jvillada.movi.EsqueletoDeLaCascara
import com.jvillada.movi.theme.Medida
import com.jvillada.movi.ui.components.NavTab
import com.jvillada.movi.ui.components.PanelDeTablero
import com.jvillada.movi.ui.components.RelevoDeScroll
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertTrue

/**
 * # Vuelta 81 · el barrido a 1.280 × 800: el ancho máximo de lectura
 *
 * Dentro de la cáscara de verdad (el rail de 216 y el tope de cada disposición), lo que una pantalla del
 * barrido pinta en **una** columna nunca pasa de [Medida.anchoMaximoDeLectura] (720), y en **dos** cada
 * columna tampoco. Sin la regla, una fila a lo ancho dejaba la cifra a 1.000 px de su nombre. Se mide con
 * `boundsInRoot` en `mdpi` (un px = un dp).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w1280dp-h800dp-mdpi")
class Vuelta81EnPantallaTest {

    @get:Rule val composeRule = createComposeRule()

    private val pantallasDelBarrido = listOf(
        Screen.Tu, Screen.Espacios, Screen.Credits, Screen.Accounts, Screen.Buscar, Screen.PorRevisar,
    )

    /** La pantalla montada y si va en dos columnas: se cambia sin volver a montar (`setContent` va una vez). */
    private val actual = mutableStateOf<Pair<Screen, Boolean>?>(null)

    /** Monta [pantalla] en la cáscara con un contenido de prueba que llena lo que le den. */
    private fun montar(pantalla: Screen, dosColumnas: Boolean) {
        val primera = actual.value == null
        actual.value = pantalla to dosColumnas
        if (primera) composeRule.setContent {
            val (pantallaActual, dosColumnasActual) = actual.value ?: return@setContent
            ConClaseDeAncho {
                EsqueletoDeLaCascara(
                    pantalla = pantallaActual,
                    activeTab = NavTab.TU,
                    conNavegacion = true,
                    onTabSelected = {},
                    relevoDeScroll = remember { RelevoDeScroll() },
                ) {
                    PanelDeTablero(enDosColumnas = { dosColumnasActual }) { dos ->
                        if (dos) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = Medida.margenDePantalla),
                                horizontalArrangement = Arrangement.spacedBy(Medida.margenDePantalla),
                            ) {
                                Box(Modifier.weight(1f).height(Medida.altoDeFila).testTag(TAG_COLUMNA))
                                Box(Modifier.weight(1f).height(Medida.altoDeFila).testTag(TAG_COLUMNA))
                            }
                        } else {
                            Box(Modifier.fillMaxSize().testTag(TAG_COLUMNA))
                        }
                    }
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun anchos(): List<Float> =
        composeRule.onAllNodesWithTag(TAG_COLUMNA, useUnmergedTree = true).fetchSemanticsNodes().map { it.boundsInRoot.width }

    @Test
    fun `en una columna ninguna pantalla del barrido pasa del ancho de lectura`() {
        pantallasDelBarrido.forEach { pantalla ->
            montar(pantalla, dosColumnas = false)
            val ancho = anchos().single()
            assertTrue(ancho <= Medida.anchoMaximoDeLectura.value + 0.5f, "$pantalla en una columna mide $ancho")
        }
    }

    @Test
    fun `en dos columnas ninguna pasa del ancho de lectura`() {
        pantallasDelBarrido.filter { it != Screen.Buscar }.forEach { pantalla ->
            montar(pantalla, dosColumnas = true)
            val anchos = anchos()
            assertTrue(anchos.size == 2, "$pantalla: $anchos")
            anchos.forEach { assertTrue(it <= Medida.anchoMaximoDeLectura.value + 0.5f, "$pantalla: una columna de $it") }
        }
    }

    private companion object {
        const val TAG_COLUMNA = "vuelta81:columna"
    }
}
