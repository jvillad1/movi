package com.jvillada.movi.ui.ai

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performScrollToNode
import com.jvillada.movi.data.RepositorioDePrueba
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.shared.model.AiChatRequest
import com.jvillada.movi.shared.model.AiChatResponse
import com.jvillada.movi.shared.model.ConversacionDelAsistente
import com.jvillada.movi.shared.model.IA_TOPE_DEL_MES
import com.jvillada.movi.shared.model.fraseDeLasQueQuedan
import com.jvillada.movi.theme.MoviTheme
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * # Movi AI con el tope del mes (vuelta 106)
 *
 * Al llegar, la burbuja dice «Llegaste a las 100 preguntas de este mes. Se renuevan el 1.º» y debajo
 * van las tres sugeridas, que siguen funcionando; al acercarse, «Te quedan 10 preguntas este mes».
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h1400dp-xhdpi")
class MoviAiTopeDelMesTest {

    @get:Rule val composeRule = createComposeRule()

    @After
    fun limpiar() {
        Repositories.sustitutoDePrueba = null
    }

    private fun hay(texto: String) =
        composeRule.onAllNodesWithText(texto, substring = true, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun montar(respuesta: AiChatResponse) {
        Repositories.sustitutoDePrueba = object : RepositorioDePrueba() {
            override suspend fun getConversacionDelAsistente() = ConversacionDelAsistente(emptyList())
            override suspend fun chatAi(request: AiChatRequest) = respuesta
        }
        composeRule.setContent {
            MoviTheme { Box(Modifier.fillMaxSize()) { AIChatScreen(onNavigate = {}, preguntaInicial = "¿Qué deuda abono primero?") } }
        }
    }

    @Test
    fun `al llegar al tope dice la frase y ofrece las tres sugeridas debajo`() {
        // Lo que contesta el server con el 429: la frase en el texto (para un APK viejo) y el código.
        montar(
            AiChatResponse(
                text = "Llegaste a las 100 preguntas de este mes. Se renuevan el 1.º",
                codigo = IA_TOPE_DEL_MES, preguntasQueQuedan = 0, topeDelMes = 100,
            ),
        )

        composeRule.waitUntil(5_000) { hay("Llegaste a las 100 preguntas de este mes. Se renuevan el 1.º") }
        PREGUNTAS_PARA_TOCAR_DE_RESPALDO.forEach { pregunta ->
            composeRule.onAllNodes(hasScrollAction())[0].performScrollToNode(hasText(pregunta.texto))
            assertTrue(hay(pregunta.texto), "falta la sugerida «${pregunta.texto}» debajo del aviso")
        }
        // No se le suma el aviso de «te quedan»: la burbuja ya dice que llegó.
        assertTrue(composeRule.onAllNodesWithTag(TAG_AVISO_DEL_TOPE, useUnmergedTree = true).fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun `al acercarse dice cuantas le quedan debajo de la respuesta`() {
        montar(AiChatResponse(text = "Abona primero al vehículo.", preguntasQueQuedan = 10, topeDelMes = 100))

        composeRule.waitUntil(5_000) { hay("Te quedan 10 preguntas este mes") }
        assertTrue(hay("Abona primero al vehículo."))
        assertFalse(hay(PREGUNTAS_PARA_TOCAR_DE_RESPALDO.first().texto) && hay("Llegaste"), "no es el aviso del tope")
    }

    @Test
    fun `que dice cada respuesta`() {
        val tope = AiChatResponse(text = "texto del server", codigo = IA_TOPE_DEL_MES, preguntasQueQuedan = 0, topeDelMes = 40)
        assertEquals("Llegaste a las 40 preguntas de este mes. Se renuevan el 1.º", textoDeLaRespuesta(tope, null))
        assertTrue(seOfrecenLasQueFuncionan(tope))
        assertNull(avisoDelTope(tope), "con el 429 la burbuja ya lo dice")
        // Sin el número (un server viejo), el texto del server.
        assertEquals("texto del server", textoDeLaRespuesta(tope.copy(topeDelMes = null), null))

        val cerca = AiChatResponse(text = "Listo.", preguntasQueQuedan = 3, topeDelMes = 100)
        assertEquals("Listo.", textoDeLaRespuesta(cerca, null))
        assertFalse(seOfrecenLasQueFuncionan(cerca))
        assertEquals("Te quedan 3 preguntas este mes", avisoDelTope(cerca))
        assertEquals("Te queda 1 pregunta este mes", fraseDeLasQueQuedan(1))
        assertEquals("Esa fue tu última pregunta libre de este mes. Se renuevan el 1.º", avisoDelTope(cerca.copy(preguntasQueQuedan = 0)))
        assertNull(avisoDelTope(AiChatResponse(text = "Listo.")))
        assertNull(fraseDeLasQueQuedan(11))
    }
}
