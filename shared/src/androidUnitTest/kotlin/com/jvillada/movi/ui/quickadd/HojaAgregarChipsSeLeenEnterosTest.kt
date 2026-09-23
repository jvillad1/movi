package com.jvillada.movi.ui.quickadd

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.RepositorioDePrueba
import com.jvillada.movi.data.UsedCategoriesCache
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.UsedCategory
import com.jvillada.movi.theme.MoviTheme
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Revisión final de la ola A: **los chips de frecuentes se leían recortados a letra normal.** La
 * fila medía `.height(40.dp)` con 8 dp de relleno vertical propio, y cada chip sumaba otros 8 dp
 * arriba y abajo: al texto le quedaban 8 dp para una línea de 16 sp. En pantalla, el nombre de la
 * categoría salía cortado por arriba y por abajo.
 *
 * Las pruebas miden el nodo de TEXTO del chip (sin fusionar) contra el chip que lo contiene (el
 * nodo fusionado, que es el `clickable`): el texto tiene que caber entero —ni desbordar su propio
 * alto ni salirse del chip— a letra normal y con la escala de letra agrandada.
 *
 * `@GraphicsMode(NATIVE)` + `sdk = [34]`: sin el motor de texto real, Robolectric mide todo texto
 * con `lineHeight` a ~17,5 dp sin importar el estilo (ver el KDoc de `EsqueletosDelInicioTest`), y
 * en SDK 24 el texto con interlineado mide ancho cero — cualquiera de las dos cosas haría que esta
 * medición no significara nada.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h731dp-xhdpi")
class HojaAgregarChipsSeLeenEnterosTest {

    @get:Rule val composeRule = createComposeRule()

    private val ahorros = Account("a1", "Bancolombia Ahorros", AccountType.SAVINGS, 15_000_000)

    @After
    fun limpiarLaCostura() {
        Repositories.sustitutoDePrueba = null
    }

    @Test
    fun a_letra_normal_el_chip_muestra_su_texto_entero() {
        montarHojaConChips(escalaDeLetra = 1f)
        assertChipSinRecortar("Transporte")
    }

    /** Con la escala de letra agrandada, la fila crece: el texto sigue entrando entero. */
    @Test
    fun con_la_letra_agrandada_el_chip_crece_y_el_texto_sigue_entero() {
        montarHojaConChips(escalaDeLetra = 1.5f)
        assertChipSinRecortar("Transporte")
    }

    // ── Andamio ───────────────────────────────────────────────────────────────────────────

    private fun montarHojaConChips(escalaDeLetra: Float) {
        // «Mercado» es la más frecuente, así que es la que arranca en la fila «Categoría»;
        // «Transporte» aparece SOLO como chip — un único nodo de texto, sin ambigüedad.
        UsedCategoriesCache.recordFromServer(
            listOf(
                UsedCategory("Mercado", listOf(TransactionType.EXPENSE), usosRecientes = 10),
                UsedCategory("Transporte", listOf(TransactionType.EXPENSE), usosRecientes = 3),
            ),
        )
        Repositories.sustitutoDePrueba = object : RepositorioDePrueba() {
            override suspend fun getAccounts(): List<Account> = listOf(ahorros)
        }
        composeRule.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(base.density, escalaDeLetra)) {
                MoviTheme {
                    Column(Modifier.fillMaxSize()) {
                        Box(Modifier.fillMaxWidth().weight(1f)) {
                            QuickAddScreen(onDismiss = {})
                        }
                        Spacer(Modifier.height(64.dp))
                    }
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun assertChipSinRecortar(nombre: String) {
        val textos = composeRule.onAllNodesWithText(nombre, useUnmergedTree = true).fetchSemanticsNodes()
        assertTrue("«$nombre» tendría que aparecer una sola vez, como chip", textos.size == 1)
        val texto = textos.single()
        val chip = composeRule.onAllNodesWithText(nombre).fetchSemanticsNodes().single()

        val layout = layoutDe(texto)
        assertFalse(
            "El texto de «$nombre» desborda su propio alto: el chip lo recorta " +
                "(alto disponible ${texto.size.height}px, alto del texto ${layout.multiParagraph.height}px)",
            layout.didOverflowHeight,
        )
        val bordesTexto = texto.boundsInRoot
        val bordesChip = chip.boundsInRoot
        assertTrue(
            "El texto de «$nombre» ($bordesTexto) se sale del chip ($bordesChip)",
            contiene(bordesChip, bordesTexto),
        )
        // Y el texto mide al menos su línea entera — la versión recortada le daba 8 dp.
        assertTrue(
            "El texto de «$nombre» mide ${bordesTexto.height}px, menos que su línea de " +
                "${layout.multiParagraph.height}px",
            bordesTexto.height + 0.5f >= layout.multiParagraph.height,
        )
    }

    private fun layoutDe(nodo: SemanticsNode): TextLayoutResult {
        val resultados = mutableListOf<TextLayoutResult>()
        val accion = nodo.config[SemanticsActions.GetTextLayoutResult].action
        assertTrue("El nodo de texto no expuso su layout", accion?.invoke(resultados) == true)
        return resultados.single()
    }

    private fun contiene(afuera: Rect, adentro: Rect): Boolean =
        adentro.top >= afuera.top - 0.5f && adentro.bottom <= afuera.bottom + 0.5f &&
            adentro.left >= afuera.left - 0.5f && adentro.right <= afuera.right + 0.5f
}
