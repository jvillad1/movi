package com.jvillada.movi.ui.ai

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.withKeyDown
import androidx.compose.ui.text.LinkAnnotation
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.RepositorioDePrueba
import com.jvillada.movi.shared.model.AiChatRequest
import com.jvillada.movi.shared.model.AiChatResponse
import com.jvillada.movi.shared.model.ChatMessage
import com.jvillada.movi.shared.model.ChatRole
import com.jvillada.movi.shared.model.ConversacionDelAsistente
import com.jvillada.movi.shared.model.PreguntaSugerida
import com.jvillada.movi.theme.MoviTheme
import com.jvillada.movi.ui.Screen
import com.jvillada.movi.ui.components.LocalHayTecladoFisico
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * # Movi AI funciona mejor sin la IA (vuelta 61)
 *
 * - **Enter envía** con teclado físico (la web) y Shift+Enter no: era un salto de renglón y nada más.
 * - **«¿Cómo voy?» escrito a mano viaja con el id** de «¿Cómo voy este período?», como el chip: el
 *   server la contesta sin la IA.
 * - **Los nombres de pantalla de una respuesta son enlaces**: «…lo tilda solo en Pagos» lleva a Pagos.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h731dp-xhdpi")
class MoviAiSinIaFuncionaMejorTest {

    @get:Rule val composeRule = createComposeRule()

    private val enviados = mutableListOf<AiChatRequest>()
    private val abiertas = mutableListOf<Screen>()

    @After
    fun limpiar() {
        Repositories.sustitutoDePrueba = null
    }

    private fun montar(vararg mensajes: ChatMessage, tecladoFisico: Boolean = true) {
        Repositories.sustitutoDePrueba = object : RepositorioDePrueba() {
            override suspend fun getConversacionDelAsistente() = ConversacionDelAsistente(mensajes.toList())
            override suspend fun chatAi(request: AiChatRequest): AiChatResponse {
                enviados += request
                return AiChatResponse(text = "Vas bien.")
            }
        }
        composeRule.setContent {
            CompositionLocalProvider(LocalHayTecladoFisico provides tecladoFisico) {
                MoviTheme { Box(Modifier.fillMaxSize()) { AIChatScreen(onNavigate = { abiertas += it }) } }
            }
        }
        composeRule.waitForIdle()
    }

    private fun hay(texto: String) =
        composeRule.onAllNodesWithText(texto, substring = true, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun escribir(texto: String) {
        composeRule.onNodeWithTag(TAG_CAMPO_DE_MOVI_AI, useUnmergedTree = true).performTextInput(texto)
        composeRule.waitForIdle()
    }

    @Test
    fun `Enter envia con teclado fisico`() {
        montar()
        escribir("¿Cuánto gasté en Comida?")
        composeRule.onNodeWithTag(TAG_CAMPO_DE_MOVI_AI, useUnmergedTree = true).performKeyInput { pressKey(Key.Enter) }
        composeRule.waitUntil(5_000) { hay("Vas bien.") }
        assertEquals("¿Cuánto gasté en Comida?", enviados.single().messages.last().content)
    }

    @Test
    fun `Shift+Enter no envia`() {
        montar()
        escribir("Primera línea")
        composeRule.onNodeWithTag(TAG_CAMPO_DE_MOVI_AI, useUnmergedTree = true).performKeyInput {
            withKeyDown(Key.ShiftLeft) { pressKey(Key.Enter) }
        }
        composeRule.waitForIdle()
        assertTrue(enviados.isEmpty(), "Shift+Enter es un salto de renglón")
        assertTrue(hay("Primera línea"), "lo escrito sigue en el campo")
    }

    @Test
    fun `sin teclado fisico Enter no envia`() {
        montar(tecladoFisico = false)
        escribir("Hola")
        composeRule.onNodeWithTag(TAG_CAMPO_DE_MOVI_AI, useUnmergedTree = true).performKeyInput { pressKey(Key.Enter) }
        composeRule.waitForIdle()
        assertTrue(enviados.isEmpty(), "en el teléfono, Enter del teclado en pantalla es un salto de renglón")
    }

    @Test
    fun `Como voy escrito a mano viaja con el id de la pregunta sugerida`() {
        montar()
        escribir("¿Cómo voy?")
        composeRule.onNodeWithTag(TAG_CAMPO_DE_MOVI_AI, useUnmergedTree = true).performKeyInput { pressKey(Key.Enter) }
        composeRule.waitUntil(5_000) { enviados.isNotEmpty() }
        assertEquals(PreguntaSugerida.COMO_VOY.id, enviados.single().messages.last().preguntaSugerida)
    }

    @Test
    fun `una pregunta libre viaja sin id`() {
        montar()
        escribir("¿Cómo voy con el carro?")
        composeRule.onNodeWithTag(TAG_CAMPO_DE_MOVI_AI, useUnmergedTree = true).performKeyInput { pressKey(Key.Enter) }
        composeRule.waitUntil(5_000) { enviados.isNotEmpty() }
        assertEquals(null, enviados.single().messages.last().preguntaSugerida)
    }

    @Test
    fun `el nombre de una pantalla en la respuesta lleva a ella`() {
        val respuesta = "Si ya lo pagaste, anótalo y Movi lo tilda solo en Pagos."
        montar(ChatMessage(ChatRole.USER, "¿Qué reviso?"), ChatMessage(ChatRole.ASSISTANT, respuesta))
        composeRule.waitUntil(5_000) { hay("lo tilda solo en") }

        val texto = composeRule.onAllNodesWithText(respuesta, useUnmergedTree = true).fetchSemanticsNodes().single()
            .config[SemanticsProperties.Text].single()
        val enlace = assertNotNull(texto.getLinkAnnotations(0, texto.length).singleOrNull(), "un enlace: ${texto.getLinkAnnotations(0, texto.length)}")
        assertEquals("Pagos", texto.substring(enlace.start, enlace.end))
        val clic = enlace.item as LinkAnnotation.Clickable
        composeRule.runOnIdle { clic.linkInteractionListener?.onClick(clic) }
        assertEquals(listOf<Screen>(Screen.Plan()), abiertas)
    }
}
