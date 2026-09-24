package com.jvillada.movi.ui.categorias

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.RepositorioDePrueba
import com.jvillada.movi.shared.model.CategoryUsage
import com.jvillada.movi.theme.MoviTheme
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Fix round 1 (Ola B, tarea 5): [FilaDeCategoria] se recortaba a la escala de letra **por
 * defecto** de Movi, no solo a una escala grande de accesibilidad.
 *
 * `App.kt` multiplica la escala de letra ambiente por 1,12 en TODA la app —«el tamaño de Movi» no
 * es 1:1 con el del sistema—, así que la escala por defecto real no es `fontScale = 1`, es
 * `fontScale = 1,12`. Con `.height(60.dp)` fijo y 10 dp de relleno vertical, el contenido
 * disponible eran 40 dp; `titulo` (línea de 20 sp) + 2 dp + `apoyo` (línea de 16 sp) miden
 * `(20 + 16) × 1,12 + 2 ≈ 42,3 dp` a esa escala — el texto se recortaba **en el uso normal de la
 * app**, no en un ajuste de accesibilidad exagerado. La corrección: `heightIn(min = …)` en vez de
 * `height(…)` (la fila puede crecer) y el relleno vertical bajó a 8 dp (44 dp disponibles, con
 * margen).
 *
 * `@GraphicsMode(NATIVE)` + `sdk = [34]`: mismo motivo que `HojaAgregarChipsSeLeenEnterosTest` —
 * sin el motor de texto real, Robolectric mide toda línea con interlineado a ~17,5 dp sin importar
 * el estilo, y en SDK 24 el texto con interlineado mide ancho cero.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h1200dp-xhdpi")
class FilaDeCategoriaNoSeRecortaTest {

    @get:Rule val composeRule = createComposeRule()

    private val categoria = CategoryUsage(name = "Restaurantes y domicilios", movements = 3)

    @After
    fun limpiar() {
        Repositories.sustitutoDePrueba = null
    }

    @Test
    fun `a la escala por defecto de Movi el nombre y el uso entran enteros, sin recorte`() {
        montar(fontScaleAmbiente = 1f)

        assertTextoSinRecortar(categoria.name)
        assertTextoSinRecortar(resumenDeUsoCorto(categoria))

        // Y la fila no creció más allá del mínimo: el esqueleto de la tarea 9 puede confiar en
        // que este es el alto real a la escala de todos los días, no uno que ya superó.
        val fila = composeRule.onNodeWithTag(tagDeFilaDeCategoria(categoria.name)).getUnclippedBoundsInRoot()
        val altoDeLaFila = fila.bottom - fila.top
        assertTrue(
            "La fila mide $altoDeLaFila, se esperaba (poco más de) $ALTO_DE_FILA_DE_CATEGORIA",
            altoDeLaFila <= ALTO_DE_FILA_DE_CATEGORIA + 1.dp,
        )
    }

    @Test
    fun `con la letra bien agrandada la fila crece en vez de recortar`() {
        montar(fontScaleAmbiente = 1.6f)

        assertTextoSinRecortar(categoria.name)
        assertTextoSinRecortar(resumenDeUsoCorto(categoria))

        val fila = composeRule.onNodeWithTag(tagDeFilaDeCategoria(categoria.name)).getUnclippedBoundsInRoot()
        val altoDeLaFila = fila.bottom - fila.top
        assertTrue(
            "La fila se quedó en el mínimo ($altoDeLaFila) en vez de crecer con la letra agrandada",
            altoDeLaFila > ALTO_DE_FILA_DE_CATEGORIA,
        )
    }

    // ── Andamio ──────────────────────────────────────────────────────────────────────────

    /** [fontScaleAmbiente] simula lo que trae el sistema; adentro se le aplica el ×1,12 de `App.kt`. */
    private fun montar(fontScaleAmbiente: Float) {
        Repositories.sustitutoDePrueba = object : RepositorioDePrueba() {
            override suspend fun getCategories(): List<CategoryUsage> = listOf(categoria)
        }
        composeRule.setContent {
            val base = LocalDensity.current
            // La MISMA composición de densidad que App.kt (ver `App()`): escala ambiente × 1.12.
            CompositionLocalProvider(LocalDensity provides Density(base.density, fontScaleAmbiente * 1.12f)) {
                MoviTheme {
                    Column(Modifier.fillMaxSize()) {
                        Box(Modifier.fillMaxWidth().height(500.dp)) {
                            CategoriasScreen(onNavigate = {})
                        }
                        Spacer(Modifier.height(300.dp))
                    }
                }
            }
        }
        composeRule.waitForIdle()
    }

    /**
     * Con margen de medio píxel y no con `layout.didOverflowHeight` a secas: ese booleano dio un
     * falso positivo justo en el borde (65 px disponibles contra 65,0 px de texto — igual, pero
     * marcado como desborde), el mismo motivo por el que `HojaAgregarChipsSeLeenEnterosTest` mide
     * con tolerancia en vez de confiar en el booleano solo.
     */
    private fun assertTextoSinRecortar(texto: String) {
        val nodos = composeRule.onAllNodesWithText(texto, useUnmergedTree = true).fetchSemanticsNodes()
        assertTrue("«$texto» no se encontró en la fila", nodos.isNotEmpty())
        val nodo = nodos.first()
        val layout = layoutDe(nodo)
        assertTrue(
            "«$texto» desborda su propio alto — la fila lo recorta (alto disponible " +
                "${nodo.size.height}px, alto del texto ${layout.multiParagraph.height}px)",
            nodo.size.height + 0.5f >= layout.multiParagraph.height,
        )
    }

    private fun layoutDe(nodo: SemanticsNode): TextLayoutResult {
        val resultados = mutableListOf<TextLayoutResult>()
        val accion = nodo.config[SemanticsActions.GetTextLayoutResult].action
        assertTrue("El nodo de texto no expuso su layout", accion?.invoke(resultados) == true)
        return resultados.single()
    }
}
