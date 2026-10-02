package com.jvillada.movi.server.ai

import com.jvillada.movi.server.routes.aiRoutes
import com.jvillada.movi.shared.model.ChatMessage
import com.jvillada.movi.shared.model.ChatRole
import com.jvillada.movi.shared.model.ConversacionDelAsistente
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.selectAll
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * # La conversación sobrevive a salir de la pantalla (Ola 3 · 3)
 *
 * El chat vivía en un `remember`: salir y volver era empezar de cero, aunque cada turno ya quedaba
 * en `ai_turns`. Lo que se fija acá: que vuelva, que «Nueva conversación» la vacíe sin borrar el
 * diagnóstico, que cada quien vea solo la suya, y que al modelo viaje un tramo con tope.
 */
class LaConversacionSobreviveTest {

    private lateinit var a: AndamioDelAsistente
    private val json = Json { ignoreUnknownKeys = true }

    @BeforeTest
    fun setUp() {
        a = AndamioDelAsistente("conversacion_sobrevive")
    }

    private fun guardar(uid: String, pregunta: String, respuesta: String, cuando: Long, imagen: Boolean = false) = runBlocking {
        guardarLaConversacion(
            uid = uid, pregunta = pregunta, respuesta = respuesta, consultas = emptyList(),
            modelo = MODELO_DE_TODOS_LOS_DIAS, criterio = false, fichasEntrada = 1, fichasCache = 1,
            fichasSalida = 1, hayImagen = imagen, ahora = cuando,
        )
    }

    @Test
    fun `al volver se recarga la conversacion, en orden`() = testApplication {
        with(a) { application { modulo { aiRoutes() } } }
        guardar(a.duenoA, "¿cuánto gasté en Comida?", "Llevas \$1.161.535.", cuando = 1_000)
        guardar(a.duenoA, "¿y en julio?", "En julio de calendario, \$980.000.", cuando = 2_000, imagen = true)

        val res = client.get("/api/ai/conversacion") { with(a) { como(a.duenoA) } }
        assertEquals(HttpStatusCode.OK, res.status)
        val conversacion = json.decodeFromString(ConversacionDelAsistente.serializer(), res.bodyAsText())

        assertEquals(
            listOf("¿cuánto gasté en Comida?", "Llevas \$1.161.535.", "¿y en julio?", "En julio de calendario, \$980.000."),
            conversacion.mensajes.map { it.content },
        )
        assertEquals(listOf(ChatRole.USER, ChatRole.ASSISTANT, ChatRole.USER, ChatRole.ASSISTANT), conversacion.mensajes.map { it.role })
        assertTrue(conversacion.mensajes[2].teniaImagen, "la foto no vuelve, pero se sabe que hubo")
    }

    @Test
    fun `cada quien ve solo su conversacion`() = testApplication {
        with(a) { application { modulo { aiRoutes() } } }
        guardar(a.duenoA, "pregunta de A", "respuesta para A", cuando = 1_000)

        val deB = client.get("/api/ai/conversacion") { with(a) { como(a.duenoB) } }.bodyAsText()
        assertTrue("pregunta de A" !in deB && "respuesta para A" !in deB, deB)
        assertTrue(json.decodeFromString(ConversacionDelAsistente.serializer(), deB).mensajes.isEmpty())
    }

    @Test
    fun `nueva conversacion la vacia sin borrar el diagnostico, y solo la de quien la pide`() = testApplication {
        with(a) { application { modulo { aiRoutes() } } }
        guardar(a.duenoA, "vieja de A", "r", cuando = 1_000)
        guardar(a.duenoB, "vieja de B", "r", cuando = 1_000)

        val nueva = client.post("/api/ai/conversacion/nueva") { with(a) { como(a.duenoA) } }
        assertEquals(HttpStatusCode.NoContent, nueva.status)

        val deA = json.decodeFromString(ConversacionDelAsistente.serializer(), client.get("/api/ai/conversacion") { with(a) { como(a.duenoA) } }.bodyAsText())
        assertTrue(deA.mensajes.isEmpty(), "A empezó otra")
        val deB = json.decodeFromString(ConversacionDelAsistente.serializer(), client.get("/api/ai/conversacion") { with(a) { como(a.duenoB) } }.bodyAsText())
        assertEquals("vieja de B", deB.mensajes.first().content, "la de B sigue")
        // El turno viejo sigue en ai_turns para diagnosticar.
        val turnosDeA = org.jetbrains.exposed.sql.transactions.transaction {
            com.jvillada.movi.server.db.AiTurns.selectAll().where { com.jvillada.movi.server.db.AiTurns.userId eq a.duenoA }.count()
        }
        assertEquals(1L, turnosDeA)

        // Y lo que se pregunta después sí aparece. Una segunda «nueva» también es válida.
        guardar(a.duenoA, "nueva de A", "r", cuando = System.currentTimeMillis() + 1_000)
        val despues = json.decodeFromString(ConversacionDelAsistente.serializer(), client.get("/api/ai/conversacion") { with(a) { como(a.duenoA) } }.bodyAsText())
        assertEquals(listOf("nueva de A", "r"), despues.mensajes.map { it.content })
        assertEquals(HttpStatusCode.NoContent, client.post("/api/ai/conversacion/nueva") { with(a) { como(a.duenoA) } }.status)
    }

    // ── El tramo que viaja al modelo ─────────────────────────────────────────

    private fun u(t: String) = ChatMessage(ChatRole.USER, t)
    private fun r(t: String) = ChatMessage(ChatRole.ASSISTANT, t)

    @Test
    fun `al modelo viaja un tramo reciente, no todo el historial`() {
        val largo = (1..30).flatMap { listOf(u("pregunta $it"), r("respuesta $it")) }
        val tramo = tramoParaElModelo(largo)
        assertTrue(tramo.size <= ULTIMOS_MENSAJES_QUE_VIAJAN)
        assertEquals("respuesta 30", tramo.last().content)
        assertEquals(ChatRole.USER, tramo.first().role, "nunca empieza con el asistente")
    }

    @Test
    fun `el tramo tiene tope de caracteres y la ultima pregunta va siempre`() {
        val consejo = "x".repeat(TOPE_DE_CARACTERES_DEL_TRAMO / 2)
        val hilo = listOf(u("vieja"), r(consejo), u("otra"), r(consejo), u("¿y en julio?"))
        val tramo = tramoParaElModelo(hilo)
        assertTrue(tramo.sumOf { it.content.length } <= TOPE_DE_CARACTERES_DEL_TRAMO, "${tramo.sumOf { it.content.length }}")
        assertEquals("¿y en julio?", tramo.last().content)
        assertEquals(ChatRole.USER, tramo.first().role)

        // Una sola pregunta más larga que el tope igual viaja: es lo que hay que contestar.
        val enorme = u("y".repeat(TOPE_DE_CARACTERES_DEL_TRAMO + 10))
        assertEquals(listOf(enorme), tramoParaElModelo(listOf(u("antes"), r("r"), enorme)))
    }
}
