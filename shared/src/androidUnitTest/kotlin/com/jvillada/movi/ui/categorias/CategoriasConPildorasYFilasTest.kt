package com.jvillada.movi.ui.categorias

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasAnyChild
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performSemanticsAction
import com.jvillada.movi.data.PropuestasDescartadasStore
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.RepositorioDePrueba
import com.jvillada.movi.data.UsedCategoriesCache
import com.jvillada.movi.shared.model.CategoryUsage
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.theme.MoviTheme
import com.jvillada.movi.ui.components.ds.TAG_FILA
import com.jvillada.movi.ui.components.ds.TAG_PILDORA_DE_FILTRO
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * # Vuelta de diseño 20: Categorías con píldoras y filas del sistema
 *
 * - Los filtros de la lista («Todas · Gastos · Ingresos») son `PildoraDeFiltro`: el elegido lo dice
 *   su semántica, y elegir otro filtra la lista.
 * - En la hoja de una categoría, las acciones («Renombrar», «Unificar en otra», «Esconder») son
 *   `Fila` del sistema, y el tipo se elige con `PildoraDeFiltro` («Automático» elegido de entrada).
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h1600dp-xhdpi")
class CategoriasConPildorasYFilasTest {

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

    private fun montar() {
        Repositories.sustitutoDePrueba = object : RepositorioDePrueba() {
            override suspend fun getCategories(): List<CategoryUsage> = listOf(
                CategoryUsage(name = "Mercado propio", movements = 4, usedTypes = listOf(TransactionType.EXPENSE)),
                CategoryUsage(name = "Sueldo propio", movements = 1, usedTypes = listOf(TransactionType.INCOME)),
            )
        }
        composeRule.setContent { MoviTheme { Box(Modifier.fillMaxSize()) { CategoriasScreen(onNavigate = {}) } } }
        composeRule.waitUntil(5_000) { hay("Mercado propio") }
    }

    private fun hay(texto: String) =
        composeRule.onAllNodesWithText(texto, substring = true, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun filtroElegido(texto: String): Boolean? =
        composeRule.onNode(hasTestTag(TAG_PILDORA_DE_FILTRO) and hasText(texto)).fetchSemanticsNode()
            .config.getOrNull(SemanticsProperties.Selected)

    private fun tocar(texto: String) {
        composeRule.onAllNodes(hasClickAction() and (hasText(texto) or hasAnyChild(hasText(texto))), useUnmergedTree = true)
            .onFirst().performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
    }

    @Test
    fun los_filtros_son_pildoras_del_sistema_y_filtran() {
        montar()
        assertEquals(true, filtroElegido("Todas"))
        assertEquals(false, filtroElegido("Gastos"))

        composeRule.onNode(hasTestTag(TAG_PILDORA_DE_FILTRO) and hasText("Gastos"))
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()

        assertEquals(true, filtroElegido("Gastos"))
        assertEquals(false, filtroElegido("Todas"))
        assertTrue(hay("Mercado propio"))
        assertTrue(!hay("Sueldo propio"), "el filtro de gastos saca lo que es solo ingreso")
    }

    @Test
    fun las_acciones_de_la_hoja_son_filas_y_el_tipo_se_elige_con_pildoras() {
        montar()
        tocar("Mercado propio")
        composeRule.waitUntil(5_000) { hay("Cambia el nombre en todos tus movimientos") }

        listOf("Renombrar", "Unificar en otra", "Esconder").forEach { accion ->
            val filas = composeRule.onAllNodes(hasTestTag(TAG_FILA) and hasText(accion)).fetchSemanticsNodes()
            assertEquals(1, filas.size, "«$accion» es una Fila del sistema")
        }
        assertEquals(true, filtroElegido("Automático"))
        assertEquals(false, filtroElegido("Gasto"))
    }
}
