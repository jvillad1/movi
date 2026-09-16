package com.jvillada.movi.ui.credits

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.jvillada.movi.theme.MoviTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

/**
 * # Un formulario lleno sigue diciendo qué es cada cosa.
 *
 * El defecto, visto en el teléfono del dueño: abrir las condiciones de un crédito ya cargado
 * mostraba **«240»** y **«5»** sueltos, uno al lado del otro, sin ninguna palabra alrededor. El
 * nombre del campo vivía en el `placeholder`, y un placeholder desaparece al escribir — o sea que
 * el rótulo existía justo mientras no hacía falta, y faltaba justo cuando sí.
 *
 * Un formulario de condiciones se abre más veces para **revisar** que para llenar.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h731dp-xhdpi")
class ElCampoLlenoSigueRotuladoTest {

    @get:Rule val composeRule = createComposeRule()

    private fun montar(valor: String, rotulo: String? = "Plazo (meses)") {
        composeRule.setContent {
            MoviTheme {
                Box(Modifier.fillMaxSize()) {
                    FieldBox(
                        placeholder = "60",
                        value = valor,
                        onValueChange = {},
                        keyboardType = KeyboardType.Number,
                        rotulo = rotulo,
                    )
                }
            }
        }
        composeRule.waitForIdle()
    }

    @Test
    fun conElCampoLlenoElRotuloSigueAhi() {
        montar(valor = "240")
        composeRule.onNodeWithText("Plazo (meses)", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun conElCampoVacioTambien() {
        montar(valor = "")
        composeRule.onNodeWithText("Plazo (meses)", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("60", useUnmergedTree = true)
            .assertIsDisplayed()
    }

    /** El ejemplo sí desaparece al escribir: para eso es un ejemplo y no un rótulo. */
    @Test
    fun elEjemploSeVaAlEscribir() {
        montar(valor = "240")
        composeRule.onAllNodesWithText("60", useUnmergedTree = true).assertCountEquals(0)
    }

    /** Sin rótulo el campo se comporta como siempre: los que tienen un ejemplo por placeholder. */
    @Test
    fun sinRotuloNoAparecePalabraDeMas() {
        montar(valor = "240", rotulo = null)
        composeRule.onAllNodesWithText("Plazo (meses)", useUnmergedTree = true).assertCountEquals(0)
    }

    /**
     * **Los dos campos de una fila quedan a la misma altura.** Es la razón por la que el rótulo se
     * pone de a pares: uno con rótulo y otro sin él dejaban las dos cajas desalineadas, que es
     * justo el defecto que el comentario de `FieldBox` ya pedía no reintroducir.
     */
    @Test
    fun dosCamposDeLaMismaFilaMidenLoMismo() {
        composeRule.setContent {
            MoviTheme {
                Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.weight(1f)) {
                        FieldBox("60", "240", {}, KeyboardType.Number, rotulo = "Plazo (meses)")
                    }
                    Box(Modifier.weight(1f)) {
                        FieldBox("5", "", {}, KeyboardType.Number, rotulo = "Día de pago")
                    }
                }
            }
        }
        composeRule.waitForIdle()

        val plazo = composeRule.onNodeWithText("Plazo (meses)", useUnmergedTree = true)
            .fetchSemanticsNode().positionInRoot.y
        val dia = composeRule.onNodeWithText("Día de pago", useUnmergedTree = true)
            .fetchSemanticsNode().positionInRoot.y
        assertEquals(plazo, dia, "los dos rótulos tienen que arrancar a la misma altura")
    }
}
