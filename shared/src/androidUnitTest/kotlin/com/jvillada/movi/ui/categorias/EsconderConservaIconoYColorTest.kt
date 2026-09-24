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
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.RepositorioDePrueba
import com.jvillada.movi.data.UsedCategoriesCache
import com.jvillada.movi.shared.model.CategoryPref
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

/**
 * Ola B — fix round 1, hallazgo 1: `onCambiarVisibilidad`/`onFijarTipo` en `CategoriasScreen.kt`
 * armaban `CategoryPref(it.hidden, it.pinnedType)` a mano, sin `icono`/`color`. Esconder o fijar
 * el tipo de una categoría que YA tenía ícono/color guardados los tiraba del caché local — y
 * como `applyPref` trata "sin icono ni color" como el default, ni siquiera dejaba un rastro:
 * `UsedCategoriesCache.prefs` perdía la entrada entera si `hidden`/`pinnedType` también volvían a
 * su default (el caso de «Volver a sugerirla» con un ícono puesto).
 *
 * El server SÍ los conserva (ver `CategoryRoutesTest`), así que la respuesta de
 * `setCategoryPrefs` trae el ícono/color intactos — el bug estaba en no leerlos de esa respuesta
 * al armar la copia local.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h731dp-xhdpi")
class EsconderConservaIconoYColorTest {

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

    private fun tocar(texto: String) {
        composeRule.onAllNodes(hasClickAction() and (hasText(texto) or hasAnyChild(hasText(texto))), useUnmergedTree = true)
            .onFirst().performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
    }

    @Test
    fun `esconder una categoria con icono y color no se los borra del caché local`() {
        Repositories.sustitutoDePrueba = object : RepositorioDePrueba() {
            override suspend fun getCategories(): List<CategoryUsage> =
                listOf(CategoryUsage(name = "Mercado", movements = 1, icono = "restaurante", color = "naranja"))

            // Mismo comportamiento que el server real: un PUT que solo manda `hidden` conserva
            // el ícono y el color que ya había (ver `CategoryRoutesTest`, "un PUT sin los campos
            // nuevos no borra...").
            override suspend fun setCategoryPrefs(
                name: String,
                hidden: Boolean,
                pinnedType: String?,
                icono: String?,
                color: String?,
            ): CategoryUsage = CategoryUsage(name = name, hidden = hidden, pinnedType = pinnedType, icono = "restaurante", color = "naranja")
        }
        composeRule.setContent { MoviTheme { Box(Modifier.fillMaxSize()) { CategoriasScreen(onNavigate = {}) } } }
        composeRule.waitUntil(5_000) { hay("Mercado") }

        tocar("Mercado")
        composeRule.waitUntil(5_000) { hay("Esconder") }
        tocar("Esconder")
        composeRule.waitUntil(5_000) { hay("vuelve a sugerirse") || hay("ya no se te va a sugerir") }

        assertEquals(
            CategoryPref(hidden = true, pinnedType = null, icono = "restaurante", color = "naranja"),
            UsedCategoriesCache.prefs["Mercado"],
        )
    }
}
