package com.jvillada.movi.ui.components.ds

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import com.jvillada.movi.data.BusquedasRecientesStore
import com.jvillada.movi.data.PropuestasDescartadasStore
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.RepositorioDePrueba
import com.jvillada.movi.data.UsedCategoriesCache
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.CategoryUsage
import com.jvillada.movi.shared.model.DestinoConocido
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.UserProfile
import com.jvillada.movi.theme.Medida
import com.jvillada.movi.theme.MoviTheme
import com.jvillada.movi.ui.buscar.BuscarScreen
import com.jvillada.movi.ui.buscar.TAG_CAMPO_DE_BUSCAR
import com.jvillada.movi.ui.categorias.CategoriasScreen
import com.jvillada.movi.ui.categorias.GUIA_DE_BUSCAR_CATEGORIA
import com.jvillada.movi.ui.categorias.tagDeFilaDeCategoria
import com.jvillada.movi.ui.components.SelectorDeCategoria
import com.jvillada.movi.ui.components.TAG_BUSCAR_CATEGORIA
import com.jvillada.movi.ui.destinos.DestinosScreen
import com.jvillada.movi.ui.destinos.GUIA_DE_BUSCAR_TERCEROS
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * # Vuelta 35 · una sola caja de búsqueda
 *
 * La de Buscar era blanca, con lupa y una × para borrar; la de Categorías («Buscar categoría»), el
 * `Campo` gris de los formularios, sin lupa ni ×. Ahora todo buscador de la app fuera de las
 * carpetas de la otra sesión es el [CampoDeBusqueda] del sistema: Buscar, Categorías, el selector de
 * categoría (Agregar, recategorizar, Presupuestos, Recurrentes) y Personas y comercios. Se prueba
 * que cada uno pinta su texto dentro de esa caja, que la × aparece solo con texto y borra, y que
 * la caja mide lo mismo con la × o sin ella.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w390dp-h1600dp-xhdpi")
class UnSoloCampoDeBusquedaTest {

    @get:Rule val composeRule = createComposeRule()

    @After
    fun limpiar() {
        Repositories.sustitutoDePrueba = null
        UsedCategoriesCache.clear()
        PropuestasDescartadasStore.clear()
    }

    private val perfil = UserProfile(id = "u1", email = "juan@movi.test", name = "Juan", avatarColor = "#4F7CFF", periodCutoffDay = 25)

    private fun conTag(tag: String) = composeRule.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().size

    private fun hay(texto: String) =
        composeRule.onAllNodesWithText(texto, substring = true, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    /** Las cajas de texto que están dentro de la del sistema, y las que no. */
    private fun camposDentroYFuera(): Pair<Int, Int> {
        val dentro = composeRule.onAllNodes(hasSetTextAction() and hasAnyAncestor(hasTestTag(TAG_CAJA_DE_BUSQUEDA)), useUnmergedTree = true)
            .fetchSemanticsNodes().size
        val todos = composeRule.onAllNodes(hasSetTextAction(), useUnmergedTree = true).fetchSemanticsNodes().size
        return dentro to (todos - dentro)
    }

    private fun borrar() {
        composeRule.onNode(hasClickAction() and hasAnyAncestor(hasTestTag(TAG_BORRAR_LA_BUSQUEDA)), useUnmergedTree = true).performClick()
        composeRule.waitForIdle()
    }

    private fun altoDeLaCaja(): Float {
        val caja = composeRule.onNodeWithTag(TAG_CAJA_DE_BUSQUEDA, useUnmergedTree = true).fetchSemanticsNode()
        return caja.size.height / caja.layoutInfo.density.density
    }

    @Test
    fun `la x aparece con texto, borra y deja el foco, y la caja mide lo mismo`() {
        var escrito = ""
        composeRule.setContent {
            MoviTheme {
                var valor by remember { mutableStateOf("") }
                Box(Modifier.fillMaxSize()) {
                    CampoDeBusqueda(valor, { valor = it; escrito = it }, guia = "Buscar categoría")
                }
            }
        }
        composeRule.waitForIdle()

        assertEquals(0, conTag(TAG_BORRAR_LA_BUSQUEDA), "sin texto no hay ×")
        assertEquals(1, conTag(TAG_GUIA_DE_BUSQUEDA), "la guía se ve")
        val altoVacia = altoDeLaCaja()
        assertTrue(altoVacia >= Medida.objetivoTactil.value - 0.5f, "la caja llega a 48: $altoVacia")
        // La lupa y la guía también para quien no ve la pantalla: la caja se llama como su guía.
        assertEquals(1, composeRule.onAllNodes(hasSetTextAction() and hasContentDescription("Buscar categoría"), useUnmergedTree = true).fetchSemanticsNodes().size)

        composeRule.onNodeWithTag(TAG_CAMPO_DE_BUSQUEDA).performTextInput("merc")
        composeRule.waitForIdle()
        assertEquals("merc", escrito)
        assertEquals(1, conTag(TAG_BORRAR_LA_BUSQUEDA), "con texto aparece la ×")
        assertEquals(0, conTag(TAG_GUIA_DE_BUSQUEDA), "y la guía se va")
        assertEquals(altoVacia, altoDeLaCaja(), 0.5f, "la × no cambia el alto de la caja")

        borrar()
        assertEquals("", escrito, "la × borra")
        assertEquals(0, conTag(TAG_BORRAR_LA_BUSQUEDA))
        assertEquals(1, conTag(TAG_GUIA_DE_BUSQUEDA))
        composeRule.onNodeWithTag(TAG_CAMPO_DE_BUSQUEDA).assertIsFocused()
    }

    @Test
    fun `Buscar usa la caja del sistema`() {
        BusquedasRecientesStore.almacenDePrueba = mutableMapOf()
        Repositories.sustitutoDePrueba = object : RepositorioDePrueba() {
            override suspend fun getUserProfile(): UserProfile = perfil
            override suspend fun getAccounts(): List<Account> = emptyList()
        }
        composeRule.setContent { MoviTheme { Box(Modifier.fillMaxSize()) { BuscarScreen(onNavigate = {}) } } }
        composeRule.waitForIdle()

        assertEquals(1 to 0, camposDentroYFuera())
        assertEquals(1, composeRule.onAllNodes(hasTestTag(TAG_CAMPO_DE_BUSCAR) and hasAnyAncestor(hasTestTag(TAG_CAJA_DE_BUSQUEDA)), useUnmergedTree = true).fetchSemanticsNodes().size)
        composeRule.onNodeWithTag(TAG_CAMPO_DE_BUSCAR).performTextReplacement("uber")
        composeRule.waitForIdle()
        borrar()
        assertEquals(
            "",
            composeRule.onNodeWithTag(TAG_CAMPO_DE_BUSCAR).fetchSemanticsNode().config.getOrNull(SemanticsProperties.EditableText)?.text,
        )
    }

    @Test
    fun `Categorias usa la caja del sistema y la x vuelve a mostrar todas`() {
        UsedCategoriesCache.clear()
        PropuestasDescartadasStore.clear()
        Repositories.sustitutoDePrueba = object : RepositorioDePrueba() {
            override suspend fun getUserProfile(): UserProfile = perfil
            override suspend fun getCategories(): List<CategoryUsage> = listOf(
                CategoryUsage(name = "Mercado propio", movements = 4, usedTypes = listOf(TransactionType.EXPENSE)),
                CategoryUsage(name = "Sueldo propio", movements = 1, usedTypes = listOf(TransactionType.INCOME)),
            )
        }
        composeRule.setContent { MoviTheme { Box(Modifier.fillMaxSize()) { CategoriasScreen(onNavigate = {}) } } }
        composeRule.waitUntil(5_000) { hay("Mercado propio") }

        assertEquals(1 to 0, camposDentroYFuera())
        assertEquals(1, composeRule.onAllNodes(hasTestTag(TAG_GUIA_DE_BUSQUEDA), useUnmergedTree = true).fetchSemanticsNodes().size)
        assertTrue(hay(GUIA_DE_BUSCAR_CATEGORIA))

        composeRule.onNodeWithTag(TAG_CAMPO_DE_BUSQUEDA).performTextInput("sueldo")
        composeRule.waitForIdle()
        assertEquals(0, conTag(tagDeFilaDeCategoria("Mercado propio")), "la búsqueda filtra")
        borrar()
        composeRule.waitUntil(5_000) { conTag(tagDeFilaDeCategoria("Mercado propio")) == 1 }
        assertEquals(1, conTag(tagDeFilaDeCategoria("Sueldo propio")))
    }

    @Test
    fun `el selector de categoria usa la caja del sistema`() {
        composeRule.setContent {
            MoviTheme {
                Box(Modifier.fillMaxSize()) {
                    SelectorDeCategoria(
                        elegida = "",
                        onElegir = {},
                        tipo = TransactionType.EXPENSE,
                        usadas = emptyMap(),
                        prefs = emptyMap(),
                        usos = emptyMap(),
                    )
                }
            }
        }
        composeRule.waitForIdle()

        assertEquals(1 to 0, camposDentroYFuera())
        assertEquals(1, composeRule.onAllNodes(hasTestTag(TAG_BUSCAR_CATEGORIA) and hasAnyAncestor(hasTestTag(TAG_CAJA_DE_BUSQUEDA)), useUnmergedTree = true).fetchSemanticsNodes().size)
        composeRule.onNodeWithTag(TAG_BUSCAR_CATEGORIA).performTextInput("Colegio")
        composeRule.waitForIdle()
        assertEquals(1, conTag(TAG_BORRAR_LA_BUSQUEDA))
        borrar()
        assertEquals(
            "",
            composeRule.onNodeWithTag(TAG_BUSCAR_CATEGORIA).fetchSemanticsNode().config.getOrNull(SemanticsProperties.EditableText)?.text,
        )
    }

    @Test
    fun `Personas y comercios usa la caja del sistema`() {
        val terceros = (1..9).map { DestinoConocido(id = "d$it", nombre = "Persona $it", numero = "5550000000$it") }
        Repositories.sustitutoDePrueba = object : RepositorioDePrueba() {
            override suspend fun getUserProfile(): UserProfile = perfil
            override suspend fun getDestinos(): List<DestinoConocido> = terceros
            override suspend fun getAccounts(): List<Account> = emptyList()
        }
        composeRule.setContent { MoviTheme { Box(Modifier.fillMaxSize()) { DestinosScreen(onNavigate = {}) } } }
        composeRule.waitUntil(5_000) { hay(GUIA_DE_BUSCAR_TERCEROS) }

        assertEquals(1 to 0, camposDentroYFuera())
    }
}
