package com.jvillada.movi.ui.components.ds

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.jvillada.movi.theme.COLORES_CLAROS
import com.jvillada.movi.theme.MoviTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * # Vuelta 42: una sola fila de navegación, y el amarillo solo en la acción
 *
 * Las filas que llevan a otra pantalla («Cómo salir de tus deudas», «Tus períodos», «Subir
 * comprobante», «Personas y comercios») iban con el ícono en un círculo amarillo lleno, y las de Tú en
 * gris. Ahora todas son [FilaDeNavegacion], con el **círculo neutro** (la capa 3 con el ícono en la
 * tinta); la que avisa algo, en el tinte. Y ninguna persona va en amarillo: «Tú» va en la tinta.
 */
@RunWith(RobolectricTestRunner::class)
class LaFilaDeNavegacionEsUnaSolaTest {

    @get:Rule val composeRule = createComposeRule()

    private fun montar(contenido: @Composable () -> Unit) {
        composeRule.setContent { MoviTheme { Box(Modifier.fillMaxSize()) { contenido() } } }
        composeRule.waitForIdle()
    }

    private fun tonos(tag: String): List<TonoDeIcono> =
        composeRule.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().map { it.config[TonoDeIconoKey] }

    @Test
    fun `la fila de navegacion lleva el circulo neutro y la flecha, y la que avisa va en el tinte`() {
        var abierta = false
        montar {
            Column {
                FilaDeNavegacion("Tus períodos", Icons.Rounded.History, onClick = { abierta = true }, subtitulo = "Cómo te fue")
                FilaDeNavegacion("La captura dejó de andar", Icons.Rounded.WarningAmber, onClick = {}, aviso = true)
            }
        }
        assertEquals(listOf(TonoDeIcono.Neutro, TonoDeIcono.Aviso), tonos(TAG_ICONO_EN_CIRCULO))
        // Las dos son una Fila del sistema (con su tag, el que cuentan las guardas).
        assertEquals(2, composeRule.onAllNodesWithTag(TAG_FILA, useUnmergedTree = true).fetchSemanticsNodes().size)
        composeRule.onNodeWithText("Tus períodos").performClick()
        assertTrue(abierta)
        // El neutro es la capa 3 con la tinta; el aviso, el tinte con el ocre. Ninguno es la marca.
        val c = COLORES_CLAROS
        assertEquals(c.capa3, fondoDelTonoDeIcono(tonoDeLaFilaDeNavegacion(aviso = false), c))
        assertEquals(c.tinte, fondoDelTonoDeIcono(tonoDeLaFilaDeNavegacion(aviso = true), c))
    }

    @Test
    fun `un icono y una persona sin tono son neutros, nunca amarillos`() {
        montar {
            Column {
                IconoEnCirculo(Icons.Rounded.Inbox)
                AvatarDePersona("Caro")
                AvatarDePersona("Juan", tono = TonoDeIcono.Tinta)
            }
        }
        assertEquals(listOf(TonoDeIcono.Neutro), tonos(TAG_ICONO_EN_CIRCULO))
        assertEquals(listOf(TonoDeIcono.Neutro, TonoDeIcono.Tinta), tonos(TAG_AVATAR_DE_PERSONA))
    }

    @Test
    fun `ninguna pildora pinta el amarillo lleno`() {
        val c = COLORES_CLAROS
        TonoDePildora.entries.forEach { tono ->
            assertTrue(coloresDeLaPildora(tono, c).first != c.marca, "la píldora $tono es amarilla")
        }
        // «En curso» y «Por confirmar» son estados: el tinte con el ocre.
        assertEquals(c.tinte to c.aviso, coloresDeLaPildora(TonoDePildora.Aviso, c))
    }
}
