package com.jvillada.movi.ui.ai

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import com.jvillada.movi.data.RepositorioDePrueba
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.shared.model.RecuerdoDelAsistente
import com.jvillada.movi.theme.MoviTheme
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * # «Lo que Movi sabe de ti» en Ajustes (Ola 3 · 2)
 *
 * Lo que Movi recuerda entra en cada conversación, así que tiene que poder verse y borrarse sin
 * ayuda. Borrar pregunta antes (la regla de la ola 22: borrar pregunta antes) y solo después llama
 * al repositorio.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h731dp-xhdpi")
class LoQueMoviSabeScreenTest {

    @get:Rule val composeRule = createComposeRule()

    private val borrados = mutableListOf<String>()

    @After
    fun limpiar() {
        Repositories.sustitutoDePrueba = null
    }

    private fun con(vararg recuerdos: RecuerdoDelAsistente) {
        Repositories.sustitutoDePrueba = object : RepositorioDePrueba() {
            override suspend fun getMemoriaDelAsistente() = recuerdos.toList()
            override suspend fun borrarRecuerdo(id: String) {
                borrados += id
            }
        }
    }

    private fun hay(texto: String) =
        composeRule.onAllNodesWithText(texto, substring = true, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun tocar(texto: String) =
        composeRule.onNodeWithText(texto, useUnmergedTree = true).performSemanticsAction(SemanticsActions.OnClick)

    @Test
    fun `sin recuerdos explica como se llena`() {
        con()
        composeRule.setContent { MoviTheme { Box(Modifier.fillMaxSize()) { LoQueMoviSabeScreen(onNavigate = {}) } } }
        composeRule.waitUntil(5_000) { hay(NADA_TODAVIA) }
        assertTrue(hay("Nada se guarda sin que lo confirmes"))
    }

    @Test
    fun `se ve lo que recuerda, y borrar pregunta antes de olvidar`() {
        con(RecuerdoDelAsistente("m1", "Caro es mi esposa", creadoEn = 1_759_000_000_000L))
        composeRule.setContent { MoviTheme { Box(Modifier.fillMaxSize()) { LoQueMoviSabeScreen(onNavigate = {}) } } }
        composeRule.waitUntil(5_000) { hay("Caro es mi esposa") }
        assertTrue(hay("Lo confirmaste en el chat"))

        tocar(BORRAR)
        composeRule.waitUntil(5_000) { hay(PREGUNTA_DE_BORRAR) }
        assertTrue(borrados.isEmpty(), "tocar «Borrar» solo pregunta")

        tocar(SI_BORRAR)
        composeRule.waitUntil(5_000) { !hay("Caro es mi esposa") }
        assertEquals(listOf("m1"), borrados)
        assertFalse(hay(PREGUNTA_DE_BORRAR))
    }
}
