package com.jvillada.movi.ui.categorias

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import com.jvillada.movi.data.PropuestasDescartadasStore
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.RepositorioDePrueba
import com.jvillada.movi.data.UsedCategoriesCache
import com.jvillada.movi.shared.model.CategoryScope
import com.jvillada.movi.shared.model.CategoryUsage
import com.jvillada.movi.theme.MoviTheme
import com.jvillada.movi.ui.components.ds.TAG_CIFRA
import com.jvillada.movi.ui.components.ds.TAG_TARJETA_DE_PROPUESTA
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

/**
 * # Categorías habla el sistema (vuelta de diseño 8)
 *
 * Los montos de cada categoría iban en la letra de cuerpo y el aviso «Movi encontró N cosas para
 * ordenar» era una caja con borde propia. Ahora los montos son la `Cifra` del sistema (el gasto
 * neutro, lo que entró en `entra` con «+») y el aviso es una `TarjetaDePropuesta`, como en el Inicio.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h1600dp-xhdpi")
class CategoriasHablaElSistemaTest {

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

    private fun montar(categorias: List<CategoryUsage>) {
        Repositories.sustitutoDePrueba = object : RepositorioDePrueba() {
            override suspend fun getCategories(): List<CategoryUsage> = categorias
        }
        composeRule.setContent { MoviTheme { Box(Modifier.fillMaxSize()) { CategoriasScreen(onNavigate = {}) } } }
    }

    private fun esperar(texto: String) = composeRule.waitUntil(5_000) {
        composeRule.onAllNodesWithText(texto, substring = true, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    }

    private fun cifrasDentroDe(tag: String): List<String> =
        composeRule.onAllNodes(hasTestTag(TAG_CIFRA) and hasAnyAncestor(hasTestTag(tag)), useUnmergedTree = true)
            .fetchSemanticsNodes()
            .map { nodo -> nodo.config.getOrNull(SemanticsProperties.Text).orEmpty().joinToString("") { it.text } }

    @Test
    fun `los montos del mes son la Cifra del sistema`() {
        montar(
            listOf(
                CategoryUsage(name = "Mercado", movements = 4, monthMovements = 3, monthTotal = 260_000),
                CategoryUsage(name = "Sueldo", movements = 1, monthMovements = 1, monthIncomeTotal = 850_000),
                CategoryUsage(name = "Otros", movements = 3, monthMovements = 2, monthTotal = 10_000, monthIncomeTotal = 5_000),
            ),
        )
        esperar("Mercado")
        assertEquals(listOf("$260.000"), cifrasDentroDe(tagDeFilaDeCategoria("Mercado")))
        assertEquals(listOf("+$850.000"), cifrasDentroDe(tagDeFilaDeCategoria("Sueldo")))
        assertEquals(listOf("$10.000", "+$5.000"), cifrasDentroDe(tagDeFilaDeCategoria("Otros")))
    }

    @Test
    fun `el aviso de ordenar es una propuesta de Movi`() {
        montar(
            listOf(
                CategoryUsage(name = "Arriendo recibido", scope = CategoryScope.PREDEFINED),
                CategoryUsage(name = "Comida", movements = 3),
            ),
        )
        esperar("Movi encontró 1 cosa para ordenar")
        assertEquals(
            1,
            composeRule.onAllNodes(
                hasTestTag(TAG_TARJETA_DE_PROPUESTA) and hasAnyAncestor(hasTestTag(TAG_TARJETA_DE_ORDEN)),
                useUnmergedTree = true,
            ).fetchSemanticsNodes().size,
        )
    }
}
