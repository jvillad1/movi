package com.jvillada.movi.ui.ai

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import com.jvillada.movi.data.RepositorioDePrueba
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.shared.model.AiChatRequest
import com.jvillada.movi.shared.model.AiChatResponse
import com.jvillada.movi.shared.model.ConversacionDelAsistente
import com.jvillada.movi.shared.model.IA_NO_DISPONIBLE
import com.jvillada.movi.shared.model.IA_SIN_CREDITO
import com.jvillada.movi.theme.MoviTheme
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * # Movi AI cuando la cuenta de Anthropic no contesta
 *
 * El 2026-10-04 la cuenta se quedó sin crédito y la burbuja decía «Error llamando a Claude: 400
 * …credit balance…». Ahora el server manda 503 con un código y la app muestra su propia frase.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h731dp-xhdpi")
class MoviAiSinCreditoTest {

    @get:Rule val composeRule = createComposeRule()

    @After
    fun limpiar() {
        Repositories.sustitutoDePrueba = null
    }

    private fun hay(texto: String) =
        composeRule.onAllNodesWithText(texto, substring = true, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun `con el codigo de la IA se muestra la frase de la app y no el error crudo`() {
        Repositories.sustitutoDePrueba = object : RepositorioDePrueba() {
            override suspend fun getConversacionDelAsistente() = ConversacionDelAsistente(emptyList())
            override suspend fun chatAi(request: AiChatRequest) =
                AiChatResponse(text = "Error llamando a Claude: 400 credit balance is too low", codigo = IA_SIN_CREDITO)
        }
        composeRule.setContent {
            MoviTheme { Box(Modifier.fillMaxSize()) { AIChatScreen(onNavigate = {}, preguntaInicial = "¿Cuánto gasté?") } }
        }

        composeRule.waitUntil(5_000) { hay(MOVI_AI_NO_DISPONIBLE) }
        assertFalse(hay("credit balance"))
    }

    @Test
    fun `que texto va en la burbuja`() {
        assertEquals(MOVI_AI_NO_DISPONIBLE, textoDeLaRespuesta(AiChatResponse(text = "x", codigo = IA_NO_DISPONIBLE), null))
        assertEquals(MOVI_AI_NO_DISPONIBLE, textoDeLaRespuesta(AiChatResponse(text = "x", codigo = IA_SIN_CREDITO), null))
        assertEquals("Llevas \$30.000.", textoDeLaRespuesta(AiChatResponse(text = "Llevas \$30.000."), null))
        // Un código que la app no conoce no esconde el texto.
        assertEquals("hola", textoDeLaRespuesta(AiChatResponse(text = "hola", codigo = "OTRA_COSA"), null))
        assertEquals("No pude conectarme con el AI. sin red", textoDeLaRespuesta(null, RuntimeException("sin red")))
        assertEquals("Movi AI no está disponible ahora. Inténtalo de nuevo más tarde.", MOVI_AI_NO_DISPONIBLE)
    }
}
