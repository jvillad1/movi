package com.jvillada.movi.ui.categorias

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasAnyChild
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.RepositorioDePrueba
import com.jvillada.movi.shared.model.CategoryRewriteResult
import com.jvillada.movi.shared.model.CategoryUsage
import com.jvillada.movi.shared.repository.ApiException
import com.jvillada.movi.theme.MoviTheme
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

/**
 * Renombrar una categoría sin red cerraba la hoja: el motivo salía arriba en la pantalla y el
 * nombre nuevo se perdía. Ahora la hoja queda abierta con el motivo y con lo escrito, como las
 * hojas de presupuestos, metas y recurrentes.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h731dp-xhdpi")
class RenombrarNoPierdeLoEscritoTest {

    @get:Rule val composeRule = createComposeRule()

    @After
    fun limpiar() {
        Repositories.sustitutoDePrueba = null
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
    fun `si renombrar falla la hoja queda abierta con el motivo y el nombre escrito`() {
        var intentos = 0
        Repositories.sustitutoDePrueba = object : RepositorioDePrueba() {
            override suspend fun getCategories(): List<CategoryUsage> = listOf(CategoryUsage(name = "Trasnporte", movements = 3))
            override suspend fun renameCategory(from: String, to: String): CategoryRewriteResult {
                intentos++
                throw ApiException(422, "No se pudo renombrar ahora")
            }
        }
        composeRule.setContent { MoviTheme { Box(Modifier.fillMaxSize()) { CategoriasScreen(onNavigate = {}) } } }
        composeRule.waitUntil(5_000) { hay("Trasnporte") }

        tocar("Trasnporte")
        composeRule.waitUntil(5_000) { hay("Cambia el nombre en todos tus movimientos") }
        tocar("Renombrar")
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodes(hasSetTextAction(), useUnmergedTree = true).fetchSemanticsNodes().size >= 2
        }
        // El primer campo editable es el buscador de la pantalla; el de la hoja es el último.
        val campos = composeRule.onAllNodes(hasSetTextAction(), useUnmergedTree = true)
        campos[campos.fetchSemanticsNodes().size - 1].performTextReplacement("Transporte público")
        composeRule.waitForIdle()
        tocar("Renombrar")

        composeRule.waitUntil(5_000) { hay("No se pudo renombrar ahora") }
        assertEquals(1, intentos)
        // La hoja sigue ahí, con lo que el dueño escribió.
        composeRule.waitUntil(5_000) { hay("Transporte público") }
    }
}
