package com.jvillada.movi.ui.categorias

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasAnyChild
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.RepositorioDePrueba
import com.jvillada.movi.data.UsedCategoriesCache
import com.jvillada.movi.shared.model.CATEGORY_TYPE_BOTH
import com.jvillada.movi.shared.model.CategoryScope
import com.jvillada.movi.shared.model.CategoryUsage
import com.jvillada.movi.theme.MoviTheme
import kotlinx.coroutines.CompletableDeferred
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * Ola B · tarea 5: la fila compacta (sin «Tuya» ni «Ambos») y la hoja de detalle con Ícono/Color
 * editables ahí mismo.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h731dp-xhdpi")
class ElegirIconoYColorEnLaHojaTest {

    @get:Rule val composeRule = createComposeRule()

    @Before
    fun limpiarAntes() = UsedCategoriesCache.clear()

    @After
    fun limpiar() {
        Repositories.sustitutoDePrueba = null
        UsedCategoriesCache.clear()
    }

    private fun hay(texto: String) =
        composeRule.onAllNodesWithText(texto, substring = true, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    /** Por la acción semántica y no por coordenadas: las hojas son más altas que la pantalla de prueba. */
    private fun tocar(texto: String) {
        composeRule.onAllNodes(hasClickAction() and (hasText(texto) or hasAnyChild(hasText(texto))), useUnmergedTree = true)
            .onFirst().performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
    }

    @Test
    fun `la fila compacta no dice Tuya ni Ambos`() {
        Repositories.sustitutoDePrueba = object : RepositorioDePrueba() {
            override suspend fun getCategories(): List<CategoryUsage> = listOf(
                CategoryUsage(name = "Fútbol", scope = CategoryScope.CUSTOM, pinnedType = CATEGORY_TYPE_BOTH, movements = 3),
            )
        }
        composeRule.setContent { MoviTheme { Box(Modifier.fillMaxSize()) { CategoriasScreen(onNavigate = {}) } } }
        composeRule.waitUntil(5_000) { hay("Fútbol") }

        // Ni la etiqueta de tipo (ahora «Gasto e ingreso», antes «Ambos») ni «Tuya» se dicen en
        // la fila — el tipo se mudó a la hoja de detalle, que acá ni siquiera está abierta.
        assertFalse(hay("Tuya"))
        assertFalse(hay("Ambos"))
        assertFalse(hay("Gasto e ingreso"))
    }

    @Test
    fun `las reservadas no salen como fila`() {
        Repositories.sustitutoDePrueba = object : RepositorioDePrueba() {
            override suspend fun getCategories(): List<CategoryUsage> = listOf(
                CategoryUsage(name = "Comida", movements = 4),
                CategoryUsage(name = "Traspaso", reserved = true),
            )
        }
        composeRule.setContent { MoviTheme { Box(Modifier.fillMaxSize()) { CategoriasScreen(onNavigate = {}) } } }
        composeRule.waitUntil(5_000) { hay("Comida") }

        assertFalse(hay("Traspaso"))
        // El pie explica por qué hay menos filas de las que el server tiene.
        assertEquals(true, hay("categorías propias para traspasos"))
    }

    @Test
    fun `elegir icono y color llama al repositorio con las claves, y la fila se repinta`() {
        val llamadas = mutableListOf<Triple<String?, String?, Boolean>>()
        var actual = CategoryUsage(name = "Mercado", movements = 2)
        Repositories.sustitutoDePrueba = object : RepositorioDePrueba() {
            override suspend fun getCategories(): List<CategoryUsage> = listOf(actual)
            override suspend fun setCategoryPrefs(
                name: String,
                hidden: Boolean,
                pinnedType: String?,
                icono: String?,
                color: String?,
            ): CategoryUsage {
                llamadas += Triple(icono, color, hidden)
                actual = actual.copy(
                    icono = when (icono) { null -> actual.icono; "" -> null; else -> icono },
                    color = when (color) { null -> actual.color; "" -> null; else -> color },
                )
                return actual
            }
        }
        composeRule.setContent { MoviTheme { Box(Modifier.fillMaxSize()) { CategoriasScreen(onNavigate = {}) } } }
        composeRule.waitUntil(5_000) { hay("Mercado") }

        tocar("Mercado")
        composeRule.waitUntil(5_000) { hay("ÍCONO") }

        // Elegir un ícono del catálogo: manda su clave y no toca el color.
        tocar("Restaurante")
        composeRule.waitUntil(5_000) { llamadas.size == 1 }
        assertEquals(Triple("restaurante", null, false), llamadas[0])

        // Elegir un color: sin rótulo visible, se toca por su testTag. Por la acción semántica
        // (como [tocar]) y no por coordenadas: la fila de colores se desplaza, así que el círculo
        // puede quedar fuera del viewport que mide `performClick`.
        composeRule.onNodeWithTag(tagDeColorDelCatalogo("violeta"), useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitUntil(5_000) { llamadas.size == 2 }
        assertEquals(Triple(null, "violeta", false), llamadas[1])

        // El caché que lee la fila (y toda la app) ya tiene el ícono y el color elegidos: la
        // fila se repinta sin recargar nada.
        assertEquals("restaurante", UsedCategoriesCache.prefs["Mercado"]?.icono)
        assertEquals("violeta", UsedCategoriesCache.prefs["Mercado"]?.color)

        // «Volver al de Movi» solo aparece porque ya eligió algo, y manda "" en los dos campos.
        tocar("Volver al de Movi")
        composeRule.waitUntil(5_000) { llamadas.size == 3 }
        assertEquals(Triple("", "", false), llamadas[2])
    }

    /**
     * Revisión final de la Ola B: el server guarda la preferencia borrando e insertando la fila, así
     * que dos guardados en paralelo chocaban en la llave primaria (500). Mientras el primero está
     * en vuelo, un segundo toque —otro ícono, un color— se ignora.
     */
    @Test
    fun `un segundo toque mientras se guarda el primero se ignora`() {
        val puerta = CompletableDeferred<Unit>()
        val llamadas = mutableListOf<Pair<String?, String?>>()
        val mercado = CategoryUsage(name = "Mercado", movements = 2)
        Repositories.sustitutoDePrueba = object : RepositorioDePrueba() {
            override suspend fun getCategories(): List<CategoryUsage> = listOf(mercado)
            override suspend fun setCategoryPrefs(
                name: String,
                hidden: Boolean,
                pinnedType: String?,
                icono: String?,
                color: String?,
            ): CategoryUsage {
                llamadas += icono to color
                puerta.await()
                return mercado.copy(icono = icono ?: mercado.icono, color = color ?: mercado.color)
            }
        }
        composeRule.setContent { MoviTheme { Box(Modifier.fillMaxSize()) { CategoriasScreen(onNavigate = {}) } } }
        composeRule.waitUntil(5_000) { hay("Mercado") }
        tocar("Mercado")
        composeRule.waitUntil(5_000) { hay("ÍCONO") }

        tocar("Restaurante")
        composeRule.waitUntil(5_000) { llamadas.size == 1 }
        // El primero sigue en vuelo: ni otro ícono ni un color salen hacia el server.
        tocar("Restaurante")
        composeRule.onNodeWithTag(tagDeColorDelCatalogo("violeta"), useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
        assertEquals(listOf<Pair<String?, String?>>("restaurante" to null), llamadas)

        // Cuando contesta, la hoja vuelve a aceptar toques.
        puerta.complete(Unit)
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(tagDeColorDelCatalogo("violeta"), useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitUntil(5_000) { llamadas.size == 2 }
        assertEquals(null to "violeta", llamadas[1])
    }
}
