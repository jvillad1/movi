package com.jvillada.movi.server.ai

import com.jvillada.movi.server.routes.FabricaDeModelos
import com.jvillada.movi.server.routes.MOVI_AI_NO_DISPONIBLE
import com.jvillada.movi.server.routes.aiRoutes
import com.jvillada.movi.shared.model.AiChatRequest
import com.jvillada.movi.shared.model.AiChatResponse
import com.jvillada.movi.shared.model.ChatMessage
import com.jvillada.movi.shared.model.ChatRole
import com.jvillada.movi.shared.model.IA_NO_DISPONIBLE
import com.jvillada.movi.shared.model.IA_SIN_CREDITO
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

/**
 * # Movi AI cuando la cuenta de Anthropic no contesta
 *
 * Con un modelo de mentira que lanza lo que lanzaría el SDK: ninguna prueba llama a Anthropic.
 */
class MoviAiSinCreditoTest {

    private lateinit var a: AndamioDelAsistente
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @BeforeTest
    fun setUp() {
        a = AndamioDelAsistente("movi_ai_sin_credito")
    }

    /** Un modelo que falla en la primera vuelta con [falla]. */
    private fun fabricaQueFallaCon(falla: Exception): FabricaDeModelos = {
        object : ElModeloQueSeCorrige {
            override suspend fun siguienteVuelta(puedeUsarHerramientas: Boolean): RespuestaDelModelo = throw falla
            override fun anotarResultados(resultados: List<Pair<String, String>>) = Unit
            override fun anotarCorreccion(correccion: String) = Unit
        }
    }

    private suspend fun ApplicationTestBuilder.preguntar(): HttpResponse =
        client.post("/api/ai/chat") {
            with(a) { como(a.duenoA) }
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(AiChatRequest.serializer(), AiChatRequest(listOf(ChatMessage(ChatRole.USER, "¿Cuánto gasté?")))))
        }

    @Test
    fun `sin credito contesta 503 con el codigo y una frase en español`() = testApplication {
        with(a) { application { modulo { aiRoutes(fabricaQueFallaCon(FallasDeLaApiDePrueba.sinCredito())) } } }
        val res = preguntar()
        assertEquals(HttpStatusCode.ServiceUnavailable, res.status)
        val cuerpo = json.decodeFromString(AiChatResponse.serializer(), res.bodyAsText())
        assertEquals(IA_SIN_CREDITO, cuerpo.codigo)
        assertEquals(MOVI_AI_NO_DISPONIBLE, cuerpo.text, "un APK que no conoce el código lee esto")
        assertFalse(cuerpo.text.contains("credit balance"))
    }

    @Test
    fun `la API saturada es no disponible`() = testApplication {
        with(a) { application { modulo { aiRoutes(fabricaQueFallaCon(FallasDeLaApiDePrueba.limiteDeUso())) } } }
        val res = preguntar()
        assertEquals(HttpStatusCode.ServiceUnavailable, res.status)
        assertEquals(IA_NO_DISPONIBLE, json.decodeFromString(AiChatResponse.serializer(), res.bodyAsText()).codigo)
    }

    @Test
    fun `cualquier otra falla sigue siendo un 500 sin codigo`() = testApplication {
        with(a) { application { modulo { aiRoutes(fabricaQueFallaCon(FallasDeLaApiDePrueba.pedidoMalArmado())) } } }
        val res = preguntar()
        assertEquals(HttpStatusCode.InternalServerError, res.status)
        assertNull(json.decodeFromString(AiChatResponse.serializer(), res.bodyAsText()).codigo)
    }
}
