package com.jvillada.movi.server.ai

import com.jvillada.movi.server.db.AccionesPropuestas
import com.jvillada.movi.server.db.AiTurns
import com.jvillada.movi.server.db.ConversacionesConElAsistente
import com.jvillada.movi.server.db.ConversacionesDelAsistente
import com.jvillada.movi.server.routes.FabricaDeModelos
import com.jvillada.movi.server.routes.PedidoDelModelo
import com.jvillada.movi.server.routes.aiRoutes
import com.jvillada.movi.shared.model.AiChatRequest
import com.jvillada.movi.shared.model.AiChatResponse
import com.jvillada.movi.shared.model.ChatMessage
import com.jvillada.movi.shared.model.ChatRole
import com.jvillada.movi.shared.model.ConversacionDelAsistente
import com.jvillada.movi.shared.model.ConversacionEnLaLista
import com.jvillada.movi.shared.model.EstadoDePropuesta
import com.jvillada.movi.shared.model.ListaDeConversaciones
import com.jvillada.movi.shared.model.ResolverPropuestaRequest
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * # Varias conversaciones con Movi AI (vuelta 117)
 *
 * El dueño pidió listar los chats del pasado, abrir uno, seguirlo y borrarlo. Lo que se fija acá, con
 * un modelo de mentira (ninguna prueba llama a Anthropic): crear, listar, abrir, seguir y borrar; que
 * nadie vea ni borre la conversación de otro (404); que el chat sin id siga a la más reciente; que los
 * turnos de antes queden agrupados; y que las rutas de siempre contesten igual para un APK viejo.
 */
class VariasConversacionesTest {

    private lateinit var a: AndamioDelAsistente
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @BeforeTest
    fun setUp() {
        a = AndamioDelAsistente("varias_conversaciones")
    }

    /** Un modelo que contesta siempre lo mismo y guarda lo que le mandaron. */
    private class ModeloQueContesta(private val respuesta: RespuestaDelModelo) : ElModeloQueSeCorrige {
        override suspend fun siguienteVuelta(puedeUsarHerramientas: Boolean) = respuesta
        override fun anotarResultados(resultados: List<Pair<String, String>>) = Unit
        override fun anotarCorreccion(correccion: String) = Unit
    }

    private val pedidos = mutableListOf<PedidoDelModelo>()
    private var siguiente: RespuestaDelModelo = RespuestaDelModelo.Texto("Listo.")
    private val fabrica: FabricaDeModelos = { pedido -> pedidos += pedido; ModeloQueContesta(siguiente) }

    private fun ApplicationTestBuilder.montar() = with(a) { application { modulo { aiRoutes(fabrica) } } }

    private suspend fun ApplicationTestBuilder.preguntar(
        pregunta: String,
        conversacion: String? = null,
        uid: String = a.duenoA,
        antes: List<ChatMessage> = emptyList(),
    ): AiChatResponse {
        val res = client.post("/api/ai/chat") {
            with(a) { como(uid) }
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(AiChatRequest.serializer(), AiChatRequest(antes + ChatMessage(ChatRole.USER, pregunta), conversacion)))
        }
        assertEquals(HttpStatusCode.OK, res.status, res.bodyAsText())
        return json.decodeFromString(AiChatResponse.serializer(), res.bodyAsText())
    }

    private suspend fun ApplicationTestBuilder.lista(uid: String = a.duenoA): ListaDeConversaciones =
        json.decodeFromString(ListaDeConversaciones.serializer(), client.get("/api/ai/conversaciones") { with(a) { como(uid) } }.bodyAsText())

    private suspend fun ApplicationTestBuilder.abrir(id: String, uid: String = a.duenoA): ConversacionDelAsistente {
        val res = client.get("/api/ai/conversaciones/$id") { with(a) { como(uid) } }
        assertEquals(HttpStatusCode.OK, res.status, res.bodyAsText())
        return json.decodeFromString(ConversacionDelAsistente.serializer(), res.bodyAsText())
    }

    private suspend fun ApplicationTestBuilder.nueva(uid: String = a.duenoA): ConversacionEnLaLista {
        val res = client.post("/api/ai/conversaciones") { with(a) { como(uid) } }
        assertEquals(HttpStatusCode.Created, res.status, res.bodyAsText())
        return json.decodeFromString(ConversacionEnLaLista.serializer(), res.bodyAsText())
    }

    private suspend fun ApplicationTestBuilder.reciente(uid: String = a.duenoA): ConversacionDelAsistente =
        json.decodeFromString(ConversacionDelAsistente.serializer(), client.get("/api/ai/conversacion") { with(a) { como(uid) } }.bodyAsText())

    private fun textoDe(pedido: PedidoDelModelo): String = pedido.mensajes.joinToString("\n") { m ->
        m.content().string().orElse(null)
            ?: m.content().blockParams().orElse(emptyList()).mapNotNull { it.text().orElse(null)?.text() }.joinToString("\n")
    }

    // ── Crear, listar, abrir, seguir, borrar ─────────────────────────────────

    @Test
    fun `crear, listar, abrir, seguir y borrar`() = testApplication {
        montar()
        val primera = nueva()
        assertEquals(emptyList(), lista().conversaciones, "una conversación vacía no se lista")

        val r1 = preguntar("¿Cuánto gasté en restaurantes el mes pasado con la tarjeta?", primera.id)
        assertEquals(primera.id, r1.conversacionId)

        val segunda = nueva()
        assertNotEquals(primera.id, segunda.id)
        preguntar("¿Cómo voy con el carro?", segunda.id)
        preguntar("¿y el mes que viene?", segunda.id)

        val conversaciones = lista().conversaciones
        assertEquals(listOf(segunda.id, primera.id), conversaciones.map { it.id }, "la más reciente primero")
        assertEquals("¿Cuánto gasté en restaurantes el mes…", conversaciones[1].titulo, "la primera pregunta, recortada sin partir palabras")
        assertEquals("¿Cómo voy con el carro?", conversaciones[0].titulo)
        assertEquals(listOf(4, 2), conversaciones.map { it.mensajes }, "cada pregunta y su respuesta son dos mensajes")

        // Abrir la primera trae solo lo suyo, con su título y su id.
        val abierta = abrir(primera.id)
        assertEquals(primera.id, abierta.id)
        assertEquals(listOf("¿Cuánto gasté en restaurantes el mes pasado con la tarjeta?", "Listo."), abierta.mensajes.map { it.content })

        // Seguirla: la pregunta queda en ella y pasa a ser la más reciente.
        preguntar("¿y este mes?", primera.id, antes = abierta.mensajes)
        assertEquals(primera.id, lista().conversaciones.first().id)
        assertEquals(4, abrir(primera.id).mensajes.size)
        assertEquals(4, abrir(segunda.id).mensajes.size, "la otra no cambió")

        // Borrarla: se va de la lista y ya no se abre.
        assertEquals(HttpStatusCode.NoContent, client.delete("/api/ai/conversaciones/${primera.id}") { with(a) { como(a.duenoA) } }.status)
        assertEquals(listOf(segunda.id), lista().conversaciones.map { it.id })
        assertEquals(HttpStatusCode.NotFound, client.get("/api/ai/conversaciones/${primera.id}") { with(a) { como(a.duenoA) } }.status)
        assertEquals(HttpStatusCode.NotFound, client.delete("/api/ai/conversaciones/${primera.id}") { with(a) { como(a.duenoA) } }.status)
    }

    @Test
    fun `al modelo viaja la conversacion abierta, no las otras`() = testApplication {
        montar()
        val vieja = nueva()
        preguntar("pregunta de la vieja", vieja.id)
        val otra = nueva()
        preguntar("pregunta de la otra", otra.id)

        // La app abre la vieja y la sigue: manda sus mensajes, y el modelo ve esos.
        val abierta = abrir(vieja.id)
        preguntar("¿y entonces?", vieja.id, antes = abierta.mensajes)
        val loQueVio = textoDe(pedidos.last())
        assertTrue("pregunta de la vieja" in loQueVio, loQueVio)
        assertFalse("pregunta de la otra" in loQueVio, loQueVio)
    }

    @Test
    fun `nueva sobre una vacia no crea otra`() = testApplication {
        montar()
        val una = nueva()
        val otra = nueva()
        assertEquals(una.id, otra.id)
        assertEquals(1L, transaction { ConversacionesConElAsistente.selectAll().count() })
        // La vieja ruta también la reusa.
        assertEquals(HttpStatusCode.NoContent, client.post("/api/ai/conversacion/nueva") { with(a) { como(a.duenoA) } }.status)
        assertEquals(1L, transaction { ConversacionesConElAsistente.selectAll().count() })
    }

    @Test
    fun `borrar se lleva los turnos y las propuestas`() = testApplication {
        montar()
        siguiente = RespuestaDelModelo.PideHerramientas(
            listOf(LlamadaDeHerramienta("t1", PROPONER_MOVIMIENTO, mapOf("monto" to "45000", "cuenta" to "Nu", "categoria" to "Comida"))),
        )
        val r = preguntar("anota 45 mil de comida")
        assertEquals(1, r.propuestas.size)
        val id = assertNotNull(r.conversacionId)
        siguiente = RespuestaDelModelo.Texto("Listo.")
        assertEquals(1L, transaction { AccionesPropuestas.selectAll().count() })

        client.delete("/api/ai/conversaciones/$id") { with(a) { como(a.duenoA) } }

        assertEquals(0L, transaction { AccionesPropuestas.selectAll().count() }, "las propuestas se van")
        val queda = transaction { AiTurns.selectAll().map { it[AiTurns.pregunta] to it[AiTurns.respuesta] } }
        // Fue al modelo hoy: queda solo la cifra del tope, sin texto.
        assertEquals(listOf("" to ""), queda)
    }

    // ── De quién es cada una ─────────────────────────────────────────────────

    @Test
    fun `nadie ve ni borra ni escribe en la conversacion de otro`() = testApplication {
        montar()
        val deA = assertNotNull(preguntar("secreto de A").conversacionId)

        assertEquals(HttpStatusCode.NotFound, client.get("/api/ai/conversaciones/$deA") { with(a) { como(a.duenoB) } }.status)
        assertEquals(HttpStatusCode.NotFound, client.delete("/api/ai/conversaciones/$deA") { with(a) { como(a.duenoB) } }.status)
        assertEquals(emptyList(), lista(a.duenoB).conversaciones)
        assertTrue("secreto de A" !in client.get("/api/ai/conversaciones") { with(a) { como(a.duenoB) } }.bodyAsText())

        // B pregunta con el id de A: su pregunta va a una conversación suya, nueva.
        val deB = preguntar("pregunta de B", deA, uid = a.duenoB).conversacionId
        assertNotEquals(deA, deB)
        assertEquals(listOf("secreto de A", "Listo."), abrir(deA).mensajes.map { it.content }, "la de A sigue intacta")
        assertEquals(listOf("pregunta de B"), lista(a.duenoB).conversaciones.map { it.titulo })
    }

    @Test
    fun `sin id el chat sigue la mas reciente, y si no hay crea una`() = testApplication {
        montar()
        val primera = assertNotNull(preguntar("primera").conversacionId)
        assertEquals(primera, preguntar("segunda").conversacionId, "sin id, la más reciente")
        val otra = nueva()
        assertEquals(otra.id, preguntar("en la nueva").conversacionId, "la nueva es la más reciente")
        // Las tres sugeridas, sin la IA, también quedan en la conversación.
        val sugerida = client.post("/api/ai/chat") {
            with(a) { como(a.duenoA) }
            contentType(ContentType.Application.Json)
            setBody(
                json.encodeToString(
                    AiChatRequest.serializer(),
                    AiChatRequest(listOf(ChatMessage(ChatRole.USER, "¿Cómo voy este período?", preguntaSugerida = "como-voy")), primera),
                ),
            )
        }
        assertEquals(primera, json.decodeFromString(AiChatResponse.serializer(), sugerida.bodyAsText()).conversacionId)
        assertEquals(6, abrir(primera).mensajes.size)
    }

    @Test
    fun `las decisiones sobre propuestas que el modelo lee son las de esta conversacion`() = testApplication {
        montar()
        siguiente = RespuestaDelModelo.PideHerramientas(
            listOf(LlamadaDeHerramienta("t1", PROPONER_MOVIMIENTO, mapOf("monto" to "45000", "cuenta" to "Nu", "categoria" to "Comida"))),
        )
        val r = preguntar("anota 45 mil de comida")
        val p = r.propuestas.single()
        client.post("/api/ai/propuestas/${p.id}/estado") {
            with(a) { como(a.duenoA) }
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(ResolverPropuestaRequest.serializer(), ResolverPropuestaRequest(EstadoDePropuesta.RECHAZADA)))
        }
        siguiente = RespuestaDelModelo.Texto("Entendido.")

        preguntar("¿y en Comida?", r.conversacionId)
        assertTrue("dijo NO" in textoDe(pedidos.last()), "en la misma conversación lo sabe")

        val otra = nueva()
        preguntar("¿y en Comida?", otra.id)
        assertFalse("dijo NO" in textoDe(pedidos.last()), "en otra conversación no")
    }

    // ── Lo de antes y los clientes viejos ───────────────────────────────────

    @Test
    fun `los turnos de antes quedan agrupados por la marca de la ultima nueva conversacion`() = testApplication {
        montar()
        // Como estaba la base antes de la vuelta 117: turnos sin conversación y la marca de «Nueva».
        transaction {
            listOf(
                Triple("ai_v1", "¿Cuánto llevo en Comida?", 1_000L),
                Triple("ai_v2", "¿y en julio?", 2_000L),
                Triple("ai_n1", "¿Cómo voy con el carro?", 5_000L),
            ).forEach { (id, pregunta, cuando) -> turnoViejo(id, a.duenoA, pregunta, cuando) }
            turnoViejo("ai_b1", a.duenoB, "la de B", 1_500L)
            ConversacionesDelAsistente.insert { it[userId] = a.duenoA; it[empezadaEn] = 3_000L }
        }
        val agrupados = transaction { agruparLosTurnosSueltos(this, uid = null) }
        assertEquals(4, agrupados)
        assertEquals(0, transaction { agruparLosTurnosSueltos(this, uid = null) }, "la segunda vez no hace nada")

        val conversaciones = lista().conversaciones
        assertEquals(listOf("¿Cómo voy con el carro?", "¿Cuánto llevo en Comida?"), conversaciones.map { it.titulo })
        assertEquals(listOf(2, 4), conversaciones.map { it.mensajes })
        assertEquals(listOf(2_000L, 5_000L), conversaciones.map { it.ultimaEn }.sorted())
        assertEquals(listOf("la de B"), lista(a.duenoB).conversaciones.map { it.titulo }, "sin marca, todo en una")

        // La vieja ruta sigue viendo la que estaba en curso (la posterior a la marca).
        assertEquals(listOf("¿Cómo voy con el carro?", "r"), reciente().mensajes.map { it.content })
    }

    @Test
    fun `si el dueno acababa de tocar nueva, sigue viendo la conversacion vacia`() = testApplication {
        montar()
        transaction {
            turnoViejo("ai_v1", a.duenoA, "vieja", 1_000L)
            ConversacionesDelAsistente.insert { it[userId] = a.duenoA; it[empezadaEn] = 9_000L }
        }
        assertTrue(reciente().mensajes.isEmpty(), "la marca era posterior: el chat seguía vacío")
        assertEquals(listOf("vieja"), lista().conversaciones.map { it.titulo }, "la vieja está en la lista")
    }

    @Test
    fun `las rutas viejas responden igual`() = testApplication {
        montar()
        // Un APK viejo: pregunta sin id, recarga con GET conversacion, empieza otra con POST nueva.
        preguntar("¿cuánto gasté?")
        val antes = reciente()
        assertEquals(listOf("¿cuánto gasté?", "Listo."), antes.mensajes.map { it.content })
        assertEquals(listOf(ChatRole.USER, ChatRole.ASSISTANT), antes.mensajes.map { it.role })

        assertEquals(HttpStatusCode.NoContent, client.post("/api/ai/conversacion/nueva") { with(a) { como(a.duenoA) } }.status)
        assertTrue(reciente().mensajes.isEmpty(), "después de «Nueva», vacía")
        preguntar("otra cosa")
        assertEquals(listOf("otra cosa", "Listo."), reciente().mensajes.map { it.content })
        // Y lo de antes no se perdió: está en la lista.
        assertEquals(listOf("otra cosa", "¿cuánto gasté?"), lista().conversaciones.map { it.titulo })
    }

    private fun turnoViejo(id: String, uid: String, pregunta: String, cuando: Long) {
        AiTurns.insert {
            it[AiTurns.id] = id; it[userId] = uid; it[creadoEn] = cuando
            it[AiTurns.pregunta] = pregunta; it[respuesta] = "r"; it[consultas] = "[]"
            it[modelo] = MODELO_DE_TODOS_LOS_DIAS; it[criterio] = false; it[vueltas] = 1
            it[fichasEntrada] = 1; it[fichasCache] = 1; it[fichasSalida] = 1; it[imagen] = false
        }
    }
}
