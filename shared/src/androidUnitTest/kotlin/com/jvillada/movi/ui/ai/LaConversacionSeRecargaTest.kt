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
import com.jvillada.movi.shared.model.ChatMessage
import com.jvillada.movi.shared.model.ChatRole
import com.jvillada.movi.shared.model.ConversacionDelAsistente
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
 * # La conversación se recarga al volver (Ola 3 · 3)
 *
 * El chat vivía en un `remember`: salir a mirar un movimiento y volver era empezar de cero. Ahora la
 * pantalla pide la conversación en curso al abrir, y «Nueva conversación» la vacía — en el server
 * primero, para que al volver no reaparezca.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h731dp-xhdpi")
class LaConversacionSeRecargaTest {

    @get:Rule val composeRule = createComposeRule()

    private var nuevas = 0

    @After
    fun limpiar() {
        Repositories.sustitutoDePrueba = null
    }

    private fun conConversacionGuardada(vararg mensajes: ChatMessage) {
        Repositories.sustitutoDePrueba = object : RepositorioDePrueba() {
            override suspend fun getConversacionDelAsistente() = ConversacionDelAsistente(mensajes.toList())
            override suspend fun empezarConversacionNueva() {
                nuevas++
            }
        }
    }

    private fun hay(texto: String) =
        composeRule.onAllNodesWithText(texto, substring = true, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun esperar(texto: String) = composeRule.waitUntil(5_000) { hay(texto) }

    @Test
    fun `al abrir se ve lo que ya se hablo, sin el arranque`() {
        conConversacionGuardada(
            ChatMessage(ChatRole.USER, "¿Cuánto gasté en Comida?"),
            ChatMessage(ChatRole.ASSISTANT, "Llevas \$1.161.535 en Comida."),
        )
        composeRule.setContent { MoviTheme { Box(Modifier.fillMaxSize()) { AIChatScreen(onNavigate = {}) } } }

        esperar("Llevas \$1.161.535 en Comida.")
        assertTrue(hay("¿Cuánto gasté en Comida?"))
        assertTrue(hay(NUEVA_CONVERSACION))
        assertFalse(hay(NO_REEMPLAZA_A_UN_ASESOR), "con conversación no se pinta el arranque")
    }

    @Test
    fun `nueva conversacion la vacia y vuelve el arranque`() {
        conConversacionGuardada(
            ChatMessage(ChatRole.USER, "pregunta vieja"),
            ChatMessage(ChatRole.ASSISTANT, "respuesta vieja"),
        )
        composeRule.setContent { MoviTheme { Box(Modifier.fillMaxSize()) { AIChatScreen(onNavigate = {}) } } }
        esperar("respuesta vieja")

        composeRule.onNodeWithText(NUEVA_CONVERSACION, useUnmergedTree = true).performSemanticsAction(SemanticsActions.OnClick)
        esperar(NO_REEMPLAZA_A_UN_ASESOR)

        assertEquals(1, nuevas, "se le avisa al server, o al volver reaparecería")
        assertFalse(hay("respuesta vieja"))
    }
}
