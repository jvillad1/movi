package com.jvillada.movi.ui.categorias

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import com.jvillada.movi.data.PropuestasDescartadasStore
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.RepositorioDePrueba
import com.jvillada.movi.data.UsedCategoriesCache
import com.jvillada.movi.shared.model.CategoryUsage
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.UserProfile
import com.jvillada.movi.theme.MoviTheme
import com.jvillada.movi.ui.components.ds.TAG_SECCION
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
 * # Vuelta 35 · en Categorías, las cifras dicen de qué son
 *
 * «Carro · 3 movimientos · $260.000» y «Freelance · +$850.000» no decían si era este período, este
 * mes o desde siempre. Y medían cosas distintas: el conteo era de **toda la historia** y la cifra
 * **del período** (la de «En qué se va», vuelta 15). Ahora los dos son del período y el título de la
 * lista lo nombra («Este período»; «Este mes» si el período es el mes del calendario).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w390dp-h1600dp-xhdpi")
class CategoriasDicenDeQueSonLasCifrasTest {

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

    private fun montar(corte: Int) {
        Repositories.sustitutoDePrueba = object : RepositorioDePrueba() {
            override suspend fun getUserProfile(): UserProfile =
                UserProfile(id = "u1", email = "juan@movi.test", name = "Juan", avatarColor = "#4F7CFF", periodCutoffDay = corte)
            override suspend fun getCategories(): List<CategoryUsage> = listOf(
                // 40 en toda la historia, 3 en el período: la cifra es del período.
                CategoryUsage(
                    name = "Carro", movements = 40, total = 9_000_000, monthMovements = 3, monthTotal = 260_000,
                    usedTypes = listOf(TransactionType.EXPENSE),
                ),
                CategoryUsage(
                    name = "Freelance", movements = 6, incomeTotal = 5_100_000, monthMovements = 1, monthIncomeTotal = 850_000,
                    usedTypes = listOf(TransactionType.INCOME),
                ),
                // Con historia y nada en el período: sin cifra, y lo dice.
                CategoryUsage(name = "Viajes", movements = 7, total = 3_000_000, usedTypes = listOf(TransactionType.EXPENSE)),
            )
        }
        composeRule.setContent { MoviTheme { Box(Modifier.fillMaxSize()) { CategoriasScreen(onNavigate = {}) } } }
        composeRule.waitUntil(5_000) { hay("Carro") }
    }

    private fun hay(texto: String) =
        composeRule.onAllNodesWithText(texto, substring = true, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun enLaFila(nombre: String, texto: String): Boolean =
        composeRule.onAllNodes(hasText(texto) and hasAnyAncestor(hasTestTag(tagDeFilaDeCategoria(nombre))), useUnmergedTree = true)
            .fetchSemanticsNodes().isNotEmpty()

    @Test
    fun `el titulo de la lista nombra el periodo y va encima de las filas`() {
        montar(corte = 25)
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodes(hasText(ROTULO_ESTE_PERIODO) and hasAnyAncestor(hasTestTag(TAG_ALCANCE_DE_CATEGORIAS)), useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        assertEquals(1, composeRule.onAllNodes(hasTestTag(TAG_ALCANCE_DE_CATEGORIAS), useUnmergedTree = true).fetchSemanticsNodes().size)
        // Es una `Seccion` del sistema, el patrón de los títulos de toda la app.
        assertEquals(
            1,
            composeRule.onAllNodes(hasTestTag(TAG_SECCION) and hasAnyAncestor(hasTestTag(TAG_ALCANCE_DE_CATEGORIAS)), useUnmergedTree = true)
                .fetchSemanticsNodes().size,
        )
        val titulo = composeRule.onNodeWithTag(TAG_ALCANCE_DE_CATEGORIAS, useUnmergedTree = true).fetchSemanticsNode()
        val primeraFila = composeRule.onNodeWithTag(tagDeFilaDeCategoria("Carro"), useUnmergedTree = true).fetchSemanticsNode()
        assertTrue(titulo.boundsInRoot.bottom <= primeraFila.boundsInRoot.top + 0.5f, "el título va arriba de la lista")
    }

    @Test
    fun `con el periodo igual al mes del calendario dice Este mes`() {
        montar(corte = 1)
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodes(hasText(ROTULO_ESTE_MES) and hasAnyAncestor(hasTestTag(TAG_ALCANCE_DE_CATEGORIAS)), useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun `el conteo de la fila es del periodo, como su cifra`() {
        montar(corte = 25)
        assertTrue(enLaFila("Carro", "3 movimientos"), "Carro cuenta los 3 del período")
        assertTrue(!enLaFila("Carro", "40 movimientos"), "no los 40 de toda la historia")
        assertTrue(enLaFila("Carro", "$260.000"), "y su cifra es la del período")
        assertTrue(enLaFila("Freelance", "1 movimiento"))
        assertTrue(enLaFila("Freelance", "+$850.000"))
        assertTrue(enLaFila("Viajes", "Sin movimientos"), "con historia y nada en el período")
        assertTrue(!enLaFila("Viajes", "$3.000.000"), "sin cifra de toda la historia")
    }
}
