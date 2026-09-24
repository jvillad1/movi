package com.jvillada.movi.ui.categorias

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.RepositorioDePrueba
import com.jvillada.movi.shared.model.CategoryUsage
import com.jvillada.movi.theme.MoviTheme
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Ola B, tarea 2: el título de Categorías decía «35 categorías» contando las reservadas de Movi
 * (traspasos, saldos iniciales, ajustes…) que `filtrarCategorias` ya saca de la lista — el título
 * y lo que se ve abajo se contradecían. Cuenta las que la pantalla de verdad muestra.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h731dp-xhdpi")
class TituloDeCategoriasTest {

    @get:Rule val composeRule = createComposeRule()

    @After
    fun limpiar() {
        Repositories.sustitutoDePrueba = null
    }

    @Test
    fun `el titulo no cuenta las reservadas`() {
        Repositories.sustitutoDePrueba = object : RepositorioDePrueba() {
            override suspend fun getCategories(): List<CategoryUsage> = listOf(
                CategoryUsage(name = "Comida", movements = 3),
                CategoryUsage(name = "Transporte", movements = 1),
                CategoryUsage(name = "Traspaso", reserved = true),
                CategoryUsage(name = "Saldo inicial", reserved = true),
                CategoryUsage(name = "Ajuste", reserved = true),
            )
        }
        composeRule.setContent {
            MoviTheme { Box(Modifier.fillMaxSize()) { CategoriasScreen(onNavigate = {}) } }
        }
        composeRule.waitForIdle()

        // 2 categorías de verdad, no 5 (las 3 reservadas no se listan — ver `filtrarCategorias`).
        composeRule.onNodeWithText("2 categorías", useUnmergedTree = true).assertExists()
    }

    @Test
    fun `el titulo cuenta la escondida aparte y sin las reservadas`() {
        Repositories.sustitutoDePrueba = object : RepositorioDePrueba() {
            override suspend fun getCategories(): List<CategoryUsage> = listOf(
                CategoryUsage(name = "Comida", movements = 3),
                CategoryUsage(name = "Transporte", movements = 1, hidden = true),
                CategoryUsage(name = "Traspaso", reserved = true),
            )
        }
        composeRule.setContent {
            MoviTheme { Box(Modifier.fillMaxSize()) { CategoriasScreen(onNavigate = {}) } }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("2 categorías · 1 escondida", useUnmergedTree = true).assertExists()
    }
}
