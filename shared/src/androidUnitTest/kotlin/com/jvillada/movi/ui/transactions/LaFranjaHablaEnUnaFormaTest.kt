package com.jvillada.movi.ui.transactions

import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import com.jvillada.movi.shared.model.PeriodoFinanciero
import com.jvillada.movi.theme.Medida
import com.jvillada.movi.theme.MoviTheme
import com.jvillada.movi.ui.components.ds.TAG_CIFRA
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * # La franja Entró · Salió · Neto habla en una sola forma (vuelta de diseño 32)
 *
 * A 390 px decía «Entró +$850.000 · Salió $1,8M · Neto −$902.250»: una sola iba compacta. Ahora, si
 * todas caben enteras van enteras; si una no cabe, las tres van abreviadas. Con el motor de texto de
 * verdad (`sdk = [34]`, `NATIVE`), a 360 dp, el teléfono más angosto.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h800dp-xhdpi")
class LaFranjaHablaEnUnaFormaTest {

    @get:Rule val composeRule = createComposeRule()

    private fun cifrasDeLaFranja(entro: Long, salio: Long): List<String> {
        val resumen = ResumenDelPanel(PeriodoFinanciero(2026, 10), entro = entro, salio = salio, categorias = emptyList())
        composeRule.setContent {
            MoviTheme {
                ResumenDelPeriodoEnElTelefono(
                    resumen = resumen,
                    onVerEnQueSeVa = {},
                    modifier = Modifier.padding(horizontal = Medida.margenDePantalla),
                )
            }
        }
        composeRule.waitForIdle()
        return composeRule.onAllNodes(
            hasTestTag(TAG_CIFRA) and hasAnyAncestor(hasTestTag(TAG_RESUMEN_EN_EL_TELEFONO)),
            useUnmergedTree = true,
        ).fetchSemanticsNodes().map { n -> n.config.getOrNull(SemanticsProperties.Text)?.joinToString("") { it.text }.orEmpty() }
    }

    /** ¿Escrita entera? Las enteras de mil para arriba agrupan con punto; las abreviadas dicen «mil» o «M». */
    private fun esEntera(texto: String) = !texto.contains("mil") && !texto.endsWith("M")

    @Test
    fun `si caben enteras van enteras las tres`() {
        val cifras = cifrasDeLaFranja(entro = 850_000, salio = 1_752_250)
        assertEquals(listOf("+\$850.000", "\$1.752.250", "−\$902.250"), cifras)
    }

    @Test
    fun `si una no cabe entera van abreviadas las tres`() {
        // Vuelta 29: con Figtree tabular «$14.752.250» y «$147.522.500» ya entran a 360 dp (con
        // Martian Mono no); hace falta una cifra de catorce caracteres para que una no quepa.
        val cifras = cifrasDeLaFranja(entro = 850_000, salio = 1_475_225_000)
        assertEquals(3, cifras.size)
        assertTrue(cifras.none(::esEntera), "cuando una es abreviada, las tres lo son: $cifras")
        assertEquals(listOf("+\$850 mil", "\$1.475,2M", "−\$1.474,4M"), cifras)
    }
}
