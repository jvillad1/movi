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
import androidx.compose.ui.test.performSemanticsAction
import com.jvillada.movi.data.PropuestasDescartadasStore
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.RepositorioDePrueba
import com.jvillada.movi.data.UsedCategoriesCache
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
}
