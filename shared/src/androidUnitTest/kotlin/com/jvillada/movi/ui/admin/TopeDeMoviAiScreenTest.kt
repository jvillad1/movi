package com.jvillada.movi.ui.admin

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.hasSetTextAction
import com.jvillada.movi.shared.model.CostoDeLaIa
import com.jvillada.movi.shared.model.TopeDeUnUsuario
import com.jvillada.movi.shared.model.TopesDeLaIa
import com.jvillada.movi.theme.MoviTheme
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * # «Preguntas de Movi AI», la pantalla del admin (vuelta 106)
 *
 * El valor global y las excepciones por usuario se eligen desde la app: nada que solo se cambie en el
 * código.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w390dp-h2400dp-xhdpi")
class TopeDeMoviAiScreenTest {

    @get:Rule val composeRule = createComposeRule()

    @After fun limpiar() { MoviAiParaAdmin.sustitutoDePrueba = null }

    private val guardados = mutableListOf<String>()

    private var estado = TopesDeLaIa(
        global = 100,
        globalElegido = false,
        excepciones = listOf(TopeDeUnUsuario("u-papa", "papa@movi.test", usadasEsteMes = 12, tope = 300)),
        usuarios = listOf(
            TopeDeUnUsuario("u-papa", "papa@movi.test", usadasEsteMes = 12, tope = 300),
            TopeDeUnUsuario("u-caro", "caro@movi.test", usadasEsteMes = 4),
        ),
    )

    private val api = object : MoviAiParaAdmin {
        override suspend fun costo(): CostoDeLaIa = error("no se usa")
        override suspend fun topes() = estado
        override suspend fun guardarGlobal(tope: Int): TopesDeLaIa {
            guardados += "global=$tope"
            estado = estado.copy(global = tope, globalElegido = true)
            return estado
        }
        override suspend fun guardarExcepcion(usuarioId: String, tope: Int): TopesDeLaIa {
            guardados += "$usuarioId=$tope"
            return estado
        }
        override suspend fun quitarExcepcion(usuarioId: String): TopesDeLaIa {
            guardados += "quitar $usuarioId"
            estado = estado.copy(excepciones = emptyList())
            return estado
        }
    }

    private fun hay(texto: String) =
        composeRule.onAllNodesWithText(texto, substring = true, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun montar() {
        MoviAiParaAdmin.sustitutoDePrueba = api
        composeRule.setContent { MoviTheme { Box(Modifier.fillMaxSize()) { TopeDeMoviAiScreen(onNavigate = {}) } } }
        composeRule.waitUntil(5_000) { hay("papa@movi.test") }
    }

    @Test
    fun `pinta el global, las excepciones y a quien se le puede dar una`() {
        montar()
        assertTrue(hay("Es el de Movi: 100."))
        assertTrue(hay("Usó 12 este mes · 300 al mes"))
        assertTrue(hay("caro@movi.test"), "Caro no tiene excepción: se le puede dar una")
        assertTrue(hay("las tres sugeridas no gastan ninguna"))
    }

    @Test
    fun `el admin cambia el global desde la app`() {
        montar()
        composeRule.onAllNodes(hasSetTextAction())[0].performTextReplacement("40")
        composeRule.onNodeWithText("Guardar").performClick()
        composeRule.waitUntil(5_000) { guardados.isNotEmpty() }
        assertEquals(listOf("global=40"), guardados)
        composeRule.waitUntil(5_000) { hay("Lo eligió quien administra.") }
    }

    @Test
    fun `el admin le da y le quita una excepcion a alguien`() {
        montar()
        composeRule.onNodeWithText("caro@movi.test").performClick()
        composeRule.waitUntil(5_000) { hay("Preguntas por mes para caro@movi.test") }
        composeRule.onAllNodes(hasSetTextAction())[1].performTextReplacement("25")
        composeRule.onNodeWithText("Guardar excepción").performClick()
        composeRule.waitUntil(5_000) { guardados.isNotEmpty() }
        assertEquals("u-caro=25", guardados.last())

        composeRule.onNodeWithText("Quitar").performClick()
        composeRule.waitUntil(5_000) { guardados.size == 2 }
        assertEquals("quitar u-papa", guardados.last())
    }
}
