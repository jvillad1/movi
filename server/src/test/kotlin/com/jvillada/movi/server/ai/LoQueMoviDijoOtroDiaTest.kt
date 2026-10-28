package com.jvillada.movi.server.ai

import com.jvillada.movi.server.db.AiTurns
import com.jvillada.movi.server.routes.FabricaDeModelos
import com.jvillada.movi.server.routes.aiRoutes
import com.jvillada.movi.server.time.appDateToEpochMillis
import com.jvillada.movi.server.time.AppClock
import com.jvillada.movi.shared.model.AiChatRequest
import com.jvillada.movi.shared.model.AiChatResponse
import com.jvillada.movi.shared.model.ChatMessage
import com.jvillada.movi.shared.model.ChatRole
import com.jvillada.movi.shared.model.ConversacionDelAsistente
import com.jvillada.movi.shared.model.IA_SIN_CREDITO
import com.jvillada.movi.shared.model.PreguntaSugerida
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * # Lo que Movi dijo otro día, y si la IA contesta hoy (vuelta de diseño 30)
 *
 * - `GET /api/ai/conversacion` devuelve **cuándo se dijo** cada globo (la fecha del turno, para que el
 *   chat separe los días y atenúe lo de ayer) y **el id de la pregunta sugerida** que contestó un turno
 *   sin modelo (para que «Preguntar de nuevo» la siga contestando sin la IA).
 * - Y si la IA contesta preguntas libres ahora (`iaDisponible`): sin crédito no, y el chat no ofrece
 *   preguntas que van a dar el aviso.
 */
class LoQueMoviDijoOtroDiaTest {

    private lateinit var a: AndamioDelAsistente
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @BeforeTest
    fun setUp() {
        a = AndamioDelAsistente("lo_que_movi_dijo_otro_dia")
        EstadoDeLaIa.olvidar()
    }

    @AfterTest
    fun limpiar() = EstadoDeLaIa.olvidar()

    private val fabricaQueContesta: FabricaDeModelos = {
        object : ElModeloQueSeCorrige {
            override suspend fun siguienteVuelta(puedeUsarHerramientas: Boolean): RespuestaDelModelo = RespuestaDelModelo.Texto("Claro.")
            override fun anotarResultados(resultados: List<Pair<String, String>>) = Unit
            override fun anotarCorreccion(correccion: String) = Unit
        }
    }

    private val fabricaSinCredito: FabricaDeModelos = {
        object : ElModeloQueSeCorrige {
            override suspend fun siguienteVuelta(puedeUsarHerramientas: Boolean): RespuestaDelModelo = throw FallasDeLaApiDePrueba.sinCredito()
            override fun anotarResultados(resultados: List<Pair<String, String>>) = Unit
            override fun anotarCorreccion(correccion: String) = Unit
        }
    }

    private suspend fun ApplicationTestBuilder.preguntar(texto: String, sugerida: PreguntaSugerida? = null) =
        client.post("/api/ai/chat") {
            with(a) { como(a.duenoA) }
            contentType(ContentType.Application.Json)
            setBody(
                json.encodeToString(
                    AiChatRequest.serializer(),
                    AiChatRequest(listOf(ChatMessage(ChatRole.USER, texto, preguntaSugerida = sugerida?.id))),
                ),
            )
        }

    private suspend fun ApplicationTestBuilder.conversacion(): ConversacionDelAsistente =
        json.decodeFromString(
            ConversacionDelAsistente.serializer(),
            client.get("/api/ai/conversacion") { with(a) { como(a.duenoA) } }.bodyAsText(),
        )

    @Test
    fun `cada globo recargado dice cuando se dijo, y la sugerida vuelve con su id`() = testApplication {
        with(a) { application { modulo { aiRoutes(fabricaQueContesta) } } }
        assertEquals(HttpStatusCode.OK, preguntar(PreguntaSugerida.COMO_VOY.texto, PreguntaSugerida.COMO_VOY).status)
        assertEquals(HttpStatusCode.OK, preguntar("¿Cuánto gasté en Comida?").status)

        val turnos = transaction {
            AiTurns.selectAll().where { AiTurns.userId eq a.duenoA }.associate { it[AiTurns.pregunta] to it[AiTurns.creadoEn] }
        }
        val mensajes = conversacion().mensajes
        assertEquals(4, mensajes.size)
        val (sugerida, suRespuesta, libre, laOtra) = mensajes
        assertEquals(turnos.getValue(PreguntaSugerida.COMO_VOY.texto), sugerida.enviadoEn)
        assertEquals(sugerida.enviadoEn, suRespuesta.enviadoEn, "la respuesta lleva la fecha de su turno")
        assertEquals(PreguntaSugerida.COMO_VOY.id, sugerida.preguntaSugerida, "contestada sin modelo: vuelve con su id")
        assertEquals(turnos.getValue("¿Cuánto gasté en Comida?"), libre.enviadoEn)
        assertNull(libre.preguntaSugerida, "lo escrito a mano nunca lleva id")
        assertNotNull(laOtra.enviadoEn)
    }

    @Test
    fun `el id de la sugerida sale solo de un turno sin modelo`() {
        val consultas = """[{"herramienta":"$CONSULTA_DE_LA_PREGUNTA_SUGERIDA","argumentos":{"id":"${PreguntaSugerida.EN_QUE_SE_VA.id}"},"devolvio":""}]"""
        assertEquals(PreguntaSugerida.EN_QUE_SE_VA.id, preguntaSugeridaDelTurno(MODELO_SIN_IA, consultas))
        assertNull(preguntaSugeridaDelTurno("claude-haiku", consultas), "un turno del modelo no es una sugerida")
        assertNull(preguntaSugeridaDelTurno(MODELO_SIN_IA, "[]"))
        assertNull(preguntaSugeridaDelTurno(MODELO_SIN_IA, "no es json"))
        assertNull(
            preguntaSugeridaDelTurno(MODELO_SIN_IA, consultas.replace(PreguntaSugerida.EN_QUE_SE_VA.id, "inventada")),
            "un id que no es de las tres no viaja",
        )
    }

    @Test
    fun `sin credito la conversacion dice que la IA no contesta, y una respuesta del modelo la prende`() {
        testApplication {
            with(a) { application { modulo { aiRoutes(fabricaSinCredito) } } }
            assertTrue(conversacion().iaDisponible, "antes de fallar, se supone que contesta")
            val libre = preguntar("¿Por qué salieron $902.250 de más?")
            assertEquals(HttpStatusCode.ServiceUnavailable, libre.status)
            assertEquals(IA_SIN_CREDITO, json.decodeFromString(AiChatResponse.serializer(), libre.bodyAsText()).codigo)
            assertFalse(conversacion().iaDisponible, "después del aviso, el chat ya no ofrece preguntas libres")
            // Las tres sugeridas siguen contestando y no la prenden: no pasaron por el modelo.
            assertEquals(HttpStatusCode.OK, preguntar(PreguntaSugerida.COMO_VOY.texto, PreguntaSugerida.COMO_VOY).status)
            assertFalse(conversacion().iaDisponible)
        }
        testApplication {
            with(a) { application { modulo { aiRoutes(fabricaQueContesta) } } }
            assertEquals(HttpStatusCode.OK, preguntar("¿Y ahora?").status)
            assertTrue(conversacion().iaDisponible, "una respuesta del modelo prueba que la cuenta anda")
        }
    }

    @Test
    fun `la falla de la cuenta vence, la saturada no apaga y sin clave nunca hay IA`() {
        val ahora = appDateToEpochMillis(AppClock.today())
        EstadoDeLaIa.anotarFalla(FallaDeLaIa.SATURADA, ahora)
        assertTrue(EstadoDeLaIa.disponible(hayClave = true, ahora = ahora), "saturada es del momento")
        EstadoDeLaIa.anotarFalla(FallaDeLaIa.SIN_CREDITO, ahora)
        assertFalse(EstadoDeLaIa.disponible(hayClave = true, ahora = ahora + 1_000))
        assertTrue(EstadoDeLaIa.disponible(hayClave = true, ahora = ahora + EstadoDeLaIa.VIGENCIA_DE_LA_FALLA_MS))
        EstadoDeLaIa.olvidar()
        assertFalse(EstadoDeLaIa.disponible(hayClave = false, ahora = ahora))
    }
}
