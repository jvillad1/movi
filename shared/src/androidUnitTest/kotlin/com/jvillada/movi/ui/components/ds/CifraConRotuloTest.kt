package com.jvillada.movi.ui.components.ds

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import com.jvillada.movi.data.SaldosOcultosStore
import com.jvillada.movi.theme.Medida
import com.jvillada.movi.theme.MoviTheme
import com.jvillada.movi.ui.components.PLATA_OCULTA
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * # `CifraConRotulo` (vuelta de diseño 19)
 *
 * La pieza que reemplaza las cuatro maneras de escribir «rótulo + cifra» (Hoy, Tus períodos,
 * Créditos, el flujo libre de Plan). Tres cosas:
 *
 * - **el rótulo va como se escribe**, nunca en mayúsculas;
 * - **la cifra es la del sistema**: con «Ocultar los saldos» no queda una cifra en claro, y una cifra
 *   que todavía no se sabe es «—»;
 * - **nada se recorta a 360 dp** con la letra de la app (×1,12): tres compactas en un renglón, dos
 *   enteras de diez dígitos lado a lado, y una en renglón con un rótulo largo.
 *
 * `sdk = [34]` y `NATIVE`: el motor de texto real (ver `LasCifrasDelDisponibleEntranTest`).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h800dp-xhdpi")
class CifraConRotuloTest {

    @get:Rule val composeRule = createComposeRule()

    private fun montar(contenido: @Composable () -> Unit) {
        composeRule.setContent {
            MoviTheme {
                val base = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(base.density, base.fontScale * 1.12f)) {
                    Box(Modifier.fillMaxSize().padding(horizontal = Medida.margenDePantalla)) { contenido() }
                }
            }
        }
        composeRule.waitForIdle()
    }

    /** Lo de Hoy, Tus períodos, Plan y Créditos, con cifras del dueño, en una tarjeta a 360 dp. */
    @Composable
    private fun LasCuatro() {
        Superficie(modifier = Modifier.fillMaxWidth()) {
            Column {
                CifraConRotulo("Patrimonio neto", -662_300_000, variante = VarianteDeCifra.Grande)
                Row(Modifier.fillMaxWidth()) {
                    CifraConRotulo("Tienes", 1_528_700_000, Modifier.weight(1f))
                    CifraConRotulo("Debes", 2_191_000_000, alFinal = true)
                }
                Row(Modifier.fillMaxWidth()) {
                    CifraConRotulo("Entró", 15_600_000, Modifier.weight(1f), tono = TonoDeCifra.Ingreso, forma = FormaDeCifra.Compacta)
                    CifraConRotulo("Salió", 17_200_000, Modifier.weight(1f), forma = FormaDeCifra.Compacta)
                    CifraConRotulo("Neto del período", -1_600_000, forma = FormaDeCifra.Compacta, alFinal = true)
                }
                Row(Modifier.fillMaxWidth()) {
                    CifraConRotulo("Ingresos recurrentes", 15_600_000, Modifier.weight(1f), tono = TonoDeCifra.Ingreso)
                    CifraConRotulo("Gastos recurrentes", 14_998_426, Modifier.weight(1f))
                }
                CifraConRotulo(
                    "Lo pagan Caro y tu nómina, aunque cuentan en tu deuda",
                    12_345_678,
                    disposicion = DisposicionDeCifraConRotulo.EnRenglon,
                )
            }
        }
    }

    @Test
    fun `el rotulo va como se escribe, nunca en mayusculas`() {
        montar { LasCuatro() }
        listOf("Patrimonio neto", "Tienes", "Entró", "Neto del período", "Ingresos recurrentes").forEach { rotulo ->
            assertEquals(1, composeRule.onAllNodesWithText(rotulo, useUnmergedTree = true).fetchSemanticsNodes().size, rotulo)
            assertTrue(
                composeRule.onAllNodesWithText(rotulo.uppercase(), useUnmergedTree = true).fetchSemanticsNodes().isEmpty(),
                "«$rotulo» no se pinta en mayúsculas",
            )
        }
    }

    @Test
    fun `la cifra es la del sistema, con su signo y su tono`() {
        montar { LasCuatro() }
        listOf("−\$662.300.000", "\$1.528.700.000", "+\$15,6M", "−\$1,6M", "+\$15.600.000", "\$12.345.678").forEach { cifra ->
            assertEquals(1, composeRule.onAllNodesWithText(cifra, useUnmergedTree = true).fetchSemanticsNodes().size, cifra)
        }
    }

    @Test
    fun `con los saldos ocultos no queda ninguna cifra en claro`() {
        SaldosOcultosStore.poner(true)
        montar { LasCuatro() }
        val cifras = composeRule.onAllNodesWithTag(TAG_CIFRA, useUnmergedTree = true).fetchSemanticsNodes()
        assertEquals(9, cifras.size)
        cifras.forEach { nodo -> assertEquals(PLATA_OCULTA, layoutDe(nodo).layoutInput.text.text) }
        assertTrue(composeRule.onAllNodesWithText("\$1.528.700.000", useUnmergedTree = true).fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun `una cifra que todavia no se sabe es un guion`() {
        montar { CifraConRotulo("Ingresos recurrentes", null) }
        assertEquals(1, composeRule.onAllNodesWithText(CIFRA_SIN_DATO, useUnmergedTree = true).fetchSemanticsNodes().size)
        assertEquals(0, composeRule.onAllNodesWithTag(TAG_CIFRA, useUnmergedTree = true).fetchSemanticsNodes().size)
    }

    @Test
    fun `a 360 dp ninguna cifra se recorta ni se parte`() {
        montar { LasCuatro() }
        val cifras = composeRule.onAllNodesWithTag(TAG_CIFRA, useUnmergedTree = true).fetchSemanticsNodes()
        assertEquals(9, cifras.size)
        cifras.forEach { nodo ->
            val layout = layoutDe(nodo)
            val texto = layout.layoutInput.text.text
            val cabe = layout.layoutInput.constraints.maxWidth
            val pide = layout.multiParagraph.intrinsics.maxIntrinsicWidth
            assertTrue(pide <= cabe, "«$texto» pide ${pide}px y le dieron $cabe")
            assertEquals(1, layout.lineCount, "«$texto» quedó en más de un renglón")
        }
    }

    private fun layoutDe(nodo: SemanticsNode): TextLayoutResult {
        val resultados = mutableListOf<TextLayoutResult>()
        val accion = nodo.config[SemanticsActions.GetTextLayoutResult].action
        assertTrue(accion?.invoke(resultados) == true, "El nodo de texto no expuso su layout")
        return resultados.single()
    }
}
