package com.jvillada.movi.ui.categorias

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasAnyChild
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import com.jvillada.movi.data.PropuestasDescartadasStore
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.RepositorioDePrueba
import com.jvillada.movi.data.UsedCategoriesCache
import com.jvillada.movi.shared.model.CategoryRewriteResult
import com.jvillada.movi.shared.model.CategoryScope
import com.jvillada.movi.shared.model.CategoryUsage
import com.jvillada.movi.theme.MoviTheme
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * # «Ordena tus categorías» (Ola B, tarea 6): la tarjeta y una acción de punta a punta
 *
 * `propuestasDeOrden`/`claveDePropuesta`/etc. ya están probadas puras en `CategoriasLogicTest`.
 * Lo que falta afirmar acá es lo que solo se ve montando la pantalla: que la tarjeta aparece
 * arriba de la lista cuando hay algo pendiente, que «Revisar» abre la hoja de revisión, y que
 * tocar el botón de una propuesta de verdad llama al server y hace desaparecer la tarjeta —no
 * solo que la función pura devuelve la lista correcta.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h731dp-xhdpi")
class TarjetaDeOrdenTest {

    @get:Rule val composeRule = createComposeRule()

    @Before
    fun limpiarAntes() {
        UsedCategoriesCache.clear()
        PropuestasDescartadasStore.clear()
    }

    @After
    fun limpiar() {
        Repositories.sustitutoDePrueba = null
        UsedCategoriesCache.clear()
        PropuestasDescartadasStore.clear()
    }

    private fun hay(texto: String) =
        composeRule.onAllNodesWithText(texto, substring = true, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun tocar(texto: String) {
        composeRule.onAllNodes(hasClickAction() and (hasText(texto) or hasAnyChild(hasText(texto))), useUnmergedTree = true)
            .onFirst().performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
    }

    @Test
    fun `la tarjeta aparece con una propuesta pendiente y Revisar abre la hoja una por una`() {
        Repositories.sustitutoDePrueba = object : RepositorioDePrueba() {
            override suspend fun getCategories(): List<CategoryUsage> = listOf(
                // Del catálogo, sin uso: la única propuesta pendiente (regla 2).
                CategoryUsage(name = "Arriendo recibido", scope = CategoryScope.PREDEFINED),
                // No debe sumar ninguna otra propuesta: no está sola, tiene 3 movimientos.
                CategoryUsage(name = "Comida", movements = 3),
            )
        }
        composeRule.setContent { MoviTheme { Box(Modifier.fillMaxSize()) { CategoriasScreen(onNavigate = {}) } } }

        composeRule.waitUntil(5_000) { hay("Movi encontró 1 cosa para ordenar") }
        // Todavía no se abrió ninguna hoja.
        assertTrue(!hay("Ordena tus categorías"))

        tocar("Revisar")
        composeRule.waitUntil(5_000) { hay("Ordena tus categorías") }
        assertTrue(hay("Arriendo recibido"))
        assertTrue(hay("Esconder"))
        assertTrue(hay("Ahora no"))
    }

    @Test
    fun `esconder desde la hoja de orden llama al server y hace desaparecer la tarjeta`() {
        var escondida = false
        Repositories.sustitutoDePrueba = object : RepositorioDePrueba() {
            override suspend fun getCategories(): List<CategoryUsage> =
                if (escondida) emptyList()
                else listOf(CategoryUsage(name = "Arriendo recibido", scope = CategoryScope.PREDEFINED))

            override suspend fun setCategoryPrefs(
                name: String,
                hidden: Boolean,
                pinnedType: String?,
                icono: String?,
                color: String?,
            ): CategoryUsage {
                escondida = hidden
                return CategoryUsage(name = name, scope = CategoryScope.PREDEFINED, hidden = hidden, pinnedType = pinnedType)
            }
        }
        composeRule.setContent { MoviTheme { Box(Modifier.fillMaxSize()) { CategoriasScreen(onNavigate = {}) } } }
        composeRule.waitUntil(5_000) { hay("Movi encontró 1 cosa para ordenar") }

        tocar("Revisar")
        composeRule.waitUntil(5_000) { hay("Esconder") }
        tocar("Esconder")

        // Sin la categoría (el server ya no la devuelve, escondida), no queda nada que ordenar:
        // la hoja se cierra sola y la tarjeta desaparece de la lista.
        composeRule.waitUntil(5_000) { !hay("Movi encontró") }
        assertTrue(!hay("Ordena tus categorías"))
        assertEquals(true, UsedCategoriesCache.prefs["Arriendo recibido"]?.hidden)
    }

    @Test
    fun `Ahora no descarta la propuesta y no vuelve a ofrecerse`() {
        Repositories.sustitutoDePrueba = object : RepositorioDePrueba() {
            override suspend fun getCategories(): List<CategoryUsage> =
                listOf(CategoryUsage(name = "Arriendo recibido", scope = CategoryScope.PREDEFINED))
        }
        composeRule.setContent { MoviTheme { Box(Modifier.fillMaxSize()) { CategoriasScreen(onNavigate = {}) } } }
        composeRule.waitUntil(5_000) { hay("Movi encontró 1 cosa para ordenar") }

        tocar("Revisar")
        composeRule.waitUntil(5_000) { hay("Ahora no") }
        tocar("Ahora no")

        // La propuesta sigue siendo válida del lado del server (la categoría sigue sin usarse),
        // pero el dueño ya dijo que no: la tarjeta no vuelve a aparecer.
        composeRule.waitUntil(5_000) { !hay("Movi encontró") }
        assertTrue(claveDePropuesta(PropuestaDeOrden.EsconderNuncaUsada(CategoryUsage("Arriendo recibido"))) in PropuestasDescartadasStore.descartadas())
    }

    // ── Fix round 1 ───────────────────────────────────────────────────────────

    @Test
    fun `hallazgo 2 - unificar parecidas muestra el mismo aviso previo que Unificar en otra`() {
        Repositories.sustitutoDePrueba = object : RepositorioDePrueba() {
            override suspend fun getCategories(): List<CategoryUsage> = listOf(
                CategoryUsage(name = "Crédito", movements = 1, budgets = 1, budgetLimit = 50_000),
                CategoryUsage(name = "Cuota de crédito", movements = 10),
            )
        }
        composeRule.setContent { MoviTheme { Box(Modifier.fillMaxSize()) { CategoriasScreen(onNavigate = {}) } } }
        composeRule.waitUntil(5_000) { hay("Movi encontró 1 cosa para ordenar") }

        tocar("Revisar")
        composeRule.waitUntil(5_000) { hay("Unificar en «Cuota de crédito»") }
        // El aviso previo (avisoDeUnificacion) es el que dice que TAMBIÉN se lleva el presupuesto,
        // no solo los movimientos — sin él, el dueño confirmaba a ciegas.
        assertTrue(hay("su presupuesto"))
        assertTrue(hay("No se borra nada"))
    }

    @Test
    fun `hallazgo 3 - un solo uso abre la hoja de unificar con busqueda y confirmacion, sin escondidas`() {
        var mergeLlamado = false
        Repositories.sustitutoDePrueba = object : RepositorioDePrueba() {
            override suspend fun getCategories(): List<CategoryUsage> = listOf(
                CategoryUsage(name = "Ñoquis", movements = 1),
                CategoryUsage(name = "Pasta escondida", movements = 5, hidden = true),
                CategoryUsage(name = "Pasta", movements = 5),
            )

            override suspend fun mergeCategory(from: String, into: String): CategoryRewriteResult {
                mergeLlamado = true
                return CategoryRewriteResult(name = into, movements = 1)
            }
        }
        composeRule.setContent { MoviTheme { Box(Modifier.fillMaxSize()) { CategoriasScreen(onNavigate = {}) } } }
        composeRule.waitUntil(5_000) { hay("Movi encontró 1 cosa para ordenar") }

        tocar("Revisar")
        composeRule.waitUntil(5_000) { hay("Unificar con…") }
        tocar("Unificar con…")

        // Abrió la MISMA hoja de unificar de siempre (con su búsqueda) — todavía no fusionó nada.
        // Las filas candidatas llevan un testTag propio (`tagDeCandidataDeUnificar`) para no
        // confundirlas con la fila de la misma categoría en la lista de atrás, que sigue montada
        // —tapada— mientras esta hoja está abierta.
        composeRule.waitUntil(5_000) { hay("Unificar «Ñoquis» en…") }
        assertFalse(mergeLlamado)
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag(tagDeCandidataDeUnificar("Pasta"), useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        // La escondida no se ofrece como destino desde acá (soloVisibles).
        assertTrue(
            composeRule.onAllNodesWithTag(tagDeCandidataDeUnificar("Pasta escondida"), useUnmergedTree = true)
                .fetchSemanticsNodes().isEmpty(),
        )

        composeRule.onNodeWithTag(tagDeCandidataDeUnificar("Pasta"), useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitUntil(5_000) { hay("Unificar en «Pasta»") }
        assertFalse(mergeLlamado) // elegir todavía no confirma
        // El botón de confirmar, por su texto EXACTO y no por `tocar` (que puede toparse antes
        // con el `clickable(enabled = false)` de `HojaBase`, un padre del botón sin scroll de
        // por medio en esta hoja en particular).
        composeRule.onNodeWithText("Unificar en «Pasta»", useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitUntil(5_000) { mergeLlamado }
    }

    @Test
    fun `hallazgo 6 - un merge exitoso desde la tarjeta muestra el mismo texto de resultado que el detalle`() {
        Repositories.sustitutoDePrueba = object : RepositorioDePrueba() {
            override suspend fun getCategories(): List<CategoryUsage> = listOf(
                CategoryUsage(name = "Crédito", movements = 1),
                CategoryUsage(name = "Cuota de crédito", movements = 10),
            )

            override suspend fun mergeCategory(from: String, into: String): CategoryRewriteResult =
                CategoryRewriteResult(name = into, movements = 1)
        }
        composeRule.setContent { MoviTheme { Box(Modifier.fillMaxSize()) { CategoriasScreen(onNavigate = {}) } } }
        composeRule.waitUntil(5_000) { hay("Movi encontró 1 cosa para ordenar") }

        tocar("Revisar")
        composeRule.waitUntil(5_000) { hay("Unificar en «Cuota de crédito»") }
        tocar("Unificar en «Cuota de crédito»")

        // Mismo `textoDeResultado` que ve quien unifica desde el detalle de una categoría — no un
        // «listo» distinto según por dónde entró.
        composeRule.waitUntil(5_000) { hay("Listo:") }
        assertTrue(hay("1 movimiento"))
    }

    @Test
    fun `hallazgo 8 - un merge fallido muestra el error y la propuesta sigue pendiente`() {
        Repositories.sustitutoDePrueba = object : RepositorioDePrueba() {
            override suspend fun getCategories(): List<CategoryUsage> = listOf(
                CategoryUsage(name = "Crédito", movements = 1),
                CategoryUsage(name = "Cuota de crédito", movements = 10),
            )

            override suspend fun mergeCategory(from: String, into: String): CategoryRewriteResult =
                throw RuntimeException("boom")
        }
        composeRule.setContent { MoviTheme { Box(Modifier.fillMaxSize()) { CategoriasScreen(onNavigate = {}) } } }
        composeRule.waitUntil(5_000) { hay("Movi encontró 1 cosa para ordenar") }

        tocar("Revisar")
        composeRule.waitUntil(5_000) { hay("Unificar en «Cuota de crédito»") }
        tocar("Unificar en «Cuota de crédito»")

        composeRule.waitUntil(5_000) { hay("Algo salió mal") }
        // La categoría no cambió del lado del server: la tarjeta sigue ofreciendo la propuesta
        // (el nodo convive con la hoja abierta, tapada mientras la hoja está encima).
        assertTrue(hay("Movi encontró 1 cosa para ordenar"))
    }
}
