package com.jvillada.movi.ui.categorias

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import com.jvillada.movi.data.PropuestasDescartadasStore
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.RepositorioDePrueba
import com.jvillada.movi.data.UsedCategoriesCache
import com.jvillada.movi.shared.model.CategoryUsage
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.UserProfile
import com.jvillada.movi.theme.MoviTheme
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * # Vuelta 36 · ningún rótulo de la cuadrícula de íconos se parte dentro de una palabra
 *
 * A 390 px, en la hoja de una categoría, el ícono «Medicamentos» decía «Medicament» y debajo «os».
 * Se puede partir **entre** palabras («Arreglos del hogar» en dos renglones está bien), nunca dentro
 * de una; y si una palabra sola no cabe, el rótulo se cambia por uno más corto del mismo significado
 * —no se achica la letra—. La clave guardada no cambia ([IconoDelCatalogo.clave]).
 *
 * Se mide con el motor de texto real (`NATIVE`, sdk 34: en SDK 24 el texto mide ancho 0), a 360 dp,
 * con la escala de letra de la app (×1,12, la de `App.kt`), la hoja de detalle abierta de verdad.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h2400dp-xhdpi")
class LosRotulosDeIconosNoSePartenTest {

    @get:Rule val composeRule = createComposeRule()

    @Before
    fun antes() {
        UsedCategoriesCache.clear()
        PropuestasDescartadasStore.clear()
    }

    @After
    fun despues() {
        Repositories.sustitutoDePrueba = null
        UsedCategoriesCache.clear()
        PropuestasDescartadasStore.clear()
    }

    private fun abrirLaHojaDeUnaCategoria() {
        Repositories.sustitutoDePrueba = object : RepositorioDePrueba() {
            override suspend fun getUserProfile(): UserProfile =
                UserProfile(id = "u1", email = "juan@movi.test", name = "Juan", avatarColor = "#4F7CFF")
            override suspend fun getCategories(): List<CategoryUsage> = listOf(
                CategoryUsage(name = "Droguería", movements = 2, total = 80_000, usedTypes = listOf(TransactionType.EXPENSE)),
            )
        }
        composeRule.setContent {
            MoviTheme {
                val base = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(base.density, base.fontScale * 1.12f)) {
                    Box(Modifier.fillMaxSize()) { CategoriasScreen(onNavigate = {}) }
                }
            }
        }
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("Droguería", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag(tagDeFilaDeCategoria("Droguería"), useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodes(hasTestTag(tagDeIconoDelCatalogo(ICONO_DE_CATEGORIA_RESPALDO)), useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun medidaDelRotulo(icono: IconoDelCatalogo): TextLayoutResult {
        val nodos = composeRule.onAllNodes(
            hasText(icono.rotulo) and hasAnyAncestor(hasTestTag(tagDeIconoDelCatalogo(icono.clave))),
            useUnmergedTree = true,
        ).fetchSemanticsNodes()
        assertEquals(1, nodos.size, "la celda de «${icono.rotulo}» tiene su rótulo")
        val medidas = mutableListOf<TextLayoutResult>()
        nodos.single().config[SemanticsActions.GetTextLayoutResult].action?.invoke(medidas)
        return medidas.single()
    }

    /** Los rótulos que se parten o se cortan en la hoja abierta; vacío si ninguno. */
    private fun rotulosPartidos(): List<String> {
        abrirLaHojaDeUnaCategoria()
        return ICONOS_DEL_CATALOGO.mapNotNull { icono ->
            val medida = medidaDelRotulo(icono)
            val texto = icono.rotulo
            val renglones = (0 until medida.lineCount).map { medida.getLineStart(it) to medida.getLineEnd(it) }
            // Un renglón que termina entre dos letras partió una palabra («Medicament» / «os»).
            val corteDentroDeUnaPalabra = renglones.dropLast(1).any { (_, fin) ->
                fin in 1 until texto.length && !texto[fin - 1].isWhitespace() && !texto[fin].isWhitespace()
            }
            val cortado = medida.isLineEllipsized(medida.lineCount - 1)
            if (corteDentroDeUnaPalabra || cortado || medida.lineCount > 2) {
                "«$texto» → " + renglones.joinToString(" / ") { (inicio, fin) -> texto.substring(inicio, fin).trim() } +
                    if (cortado) " (con «…»)" else ""
            } else {
                null
            }
        }
    }

    private fun assertNingunoPartido() {
        val partidos = rotulosPartidos()
        assertTrue(partidos.isEmpty(), "Rótulos partidos o cortados:\n" + partidos.joinToString("\n"))
    }

    /** El teléfono de la revisión: tres columnas de ~106 dp. */
    @Test
    fun `ningun rotulo se parte a 360 dp`() = assertNingunoPartido()

    /**
     * **La celda más angosta de un teléfono**: desde 364 dp entran cuatro columnas de 80 dp justos
     * ([com.jvillada.movi.ui.components.ANCHO_MINIMO_DE_CELDA]), y el rótulo tiene 72. Lo que cabe acá
     * cabe en cualquier ancho de teléfono.
     */
    @Test
    @Config(sdk = [34], qualifiers = "w364dp-h2400dp-xhdpi")
    fun `ningun rotulo se parte en la celda mas angosta, a 364 dp`() = assertNingunoPartido()

    /** El de la revisión: a 390 px «Medicamentos» salía «Medicament» / «os». */
    @Test
    @Config(sdk = [34], qualifiers = "w390dp-h2400dp-xhdpi")
    fun `ningun rotulo se parte a 390 dp`() = assertNingunoPartido()

    @Test
    fun `las claves no cambian aunque cambie el rotulo`() {
        val porClave = ICONOS_DEL_CATALOGO.associate { it.clave to it.rotulo }
        // Vuelta 36: cambió lo que se lee, no lo que se guarda (`category_prefs.icono`).
        assertEquals("Medicinas", porClave["medicamentos"], "la clave publicada sigue siendo «medicamentos»")
        assertEquals("Comer fuera", porClave["restaurante"])
        assertEquals("Diversión", porClave["entretenimiento"])
        assertEquals("Membresía", porClave["suscripciones"])
        assertEquals("Traspaso", porClave["transferencia"])
        assertEquals(ICONOS_DEL_CATALOGO.size, porClave.size, "las claves no se repiten")
        assertEquals(ICONOS_DEL_CATALOGO.size, ICONOS_DEL_CATALOGO.map { it.rotulo }.toSet().size, "los rótulos tampoco")
    }
}
