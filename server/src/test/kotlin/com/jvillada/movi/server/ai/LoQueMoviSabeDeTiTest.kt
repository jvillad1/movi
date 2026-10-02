package com.jvillada.movi.server.ai

import com.jvillada.movi.server.db.MemoriaDelAsistente
import com.jvillada.movi.server.routes.FabricaDeModelos
import com.jvillada.movi.server.routes.PedidoDelModelo
import com.jvillada.movi.server.routes.aiRoutes
import com.jvillada.movi.shared.model.AiChatRequest
import com.jvillada.movi.shared.model.AiChatResponse
import com.jvillada.movi.shared.model.ChatMessage
import com.jvillada.movi.shared.model.ChatRole
import com.jvillada.movi.shared.model.GuardarRecuerdoRequest
import com.jvillada.movi.shared.model.OrigenDelRecuerdo
import com.jvillada.movi.shared.model.RecuerdoDelAsistente
import com.jvillada.movi.shared.model.TipoDeAccion
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * # «Lo que Movi sabe de ti» (Ola 3 · 2)
 *
 * - `recordar` **no escribe** sin la confirmación del dueño: es una propuesta más;
 * - lo confirmado (con el mismo `POST /api/asistente/memoria` que usa Ajustes) aparece en el
 *   contexto del turno siguiente;
 * - cada quien ve, corrige y borra solo lo suyo;
 * - el bloque tiene tope y no crece con los meses.
 */
class LoQueMoviSabeDeTiTest {

    private lateinit var a: AndamioDelAsistente
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @BeforeTest
    fun setUp() {
        a = AndamioDelAsistente("lo_que_movi_sabe")
    }

    private class ModeloDeGuion(private val guion: List<RespuestaDelModelo>) : ElModeloQueSeCorrige {
        private var i = 0
        override suspend fun siguienteVuelta(puedeUsarHerramientas: Boolean) = guion[i++.coerceAtMost(guion.lastIndex)]
        override fun anotarResultados(resultados: List<Pair<String, String>>) = Unit
        override fun anotarCorreccion(correccion: String) = Unit
    }

    private val pedidos = mutableListOf<PedidoDelModelo>()
    private val modelos = ArrayDeque<ModeloDeGuion>()
    private val fabrica: FabricaDeModelos = { pedidos += it; modelos.removeFirst() }

    private fun ApplicationTestBuilder.montar() = with(a) { application { modulo { aiRoutes(fabrica) } } }

    private suspend fun ApplicationTestBuilder.preguntar(texto: String, uid: String = a.duenoA): AiChatResponse {
        val res = client.post("/api/ai/chat") {
            with(a) { como(uid) }
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(AiChatRequest.serializer(), AiChatRequest(listOf(ChatMessage(ChatRole.USER, texto)))))
        }
        assertEquals(HttpStatusCode.OK, res.status, res.bodyAsText())
        return json.decodeFromString(AiChatResponse.serializer(), res.bodyAsText())
    }

    private suspend fun ApplicationTestBuilder.guardar(texto: String, uid: String = a.duenoA) =
        client.post("/api/asistente/memoria") {
            with(a) { como(uid) }
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(GuardarRecuerdoRequest.serializer(), GuardarRecuerdoRequest(texto)))
        }

    private suspend fun ApplicationTestBuilder.memoria(uid: String): List<RecuerdoDelAsistente> =
        json.decodeFromString(
            ListSerializer(RecuerdoDelAsistente.serializer()),
            client.get("/api/asistente/memoria") { with(a) { como(uid) } }.bodyAsText(),
        )

    private fun filas() = transaction { MemoriaDelAsistente.selectAll().count() }

    @Test
    fun `recordar no escribe sin confirmacion, y lo confirmado entra al contexto del turno siguiente`() = testApplication {
        montar()
        modelos += ModeloDeGuion(
            listOf(RespuestaDelModelo.PideHerramientas(listOf(LlamadaDeHerramienta("t1", RECORDAR, mapOf("texto" to "Caro es mi esposa"))))),
        )

        val r = preguntar("Caro es mi esposa, por si te sirve")

        val p = r.propuestas.single()
        assertEquals(TipoDeAccion.RECORDAR, p.tipo)
        assertEquals("Recordar: «Caro es mi esposa»", p.frase)
        assertEquals(0L, filas(), "proponer recordar no guarda nada")
        assertNull(pedidos.last().memoria, "sin recuerdos no viaja ningún bloque")

        // «Hacerlo»: el mismo POST que usa la pantalla de Ajustes.
        val guardado = guardar(p.recuerdo!!)
        assertEquals(HttpStatusCode.Created, guardado.status, guardado.bodyAsText())

        modelos += ModeloDeGuion(listOf(RespuestaDelModelo.Texto("Claro.")))
        preguntar("¿cuánto le transferí a Caro?")
        val memoria = pedidos.last().memoria
        assertTrue(memoria != null && "Caro es mi esposa" in memoria, "$memoria")
    }

    @Test
    fun `lo que ya sabe no se propone de nuevo, y el mismo recuerdo dos veces queda uno`() = testApplication {
        montar()
        assertEquals(HttpStatusCode.Created, guardar("Caro es mi esposa").status)
        assertEquals(HttpStatusCode.Created, guardar("caro es mi ESPOSA").status, "un doble toque no es un error")
        assertEquals(1L, filas())

        val otra = kotlinx.coroutines.runBlocking { proponer(a.duenoA, LlamadaDeHerramienta("t", RECORDAR, mapOf("texto" to "Caro es mi esposa"))) }
        assertTrue(otra is ResultadoDePropuesta.Invalida, otra.paraElModelo)
    }

    @Test
    fun `cada quien ve, corrige y borra solo lo suyo`() = testApplication {
        montar()
        val deA = json.decodeFromString(RecuerdoDelAsistente.serializer(), guardar("El bono de Glim de \$55.500 no es mensual").bodyAsText())

        assertTrue(memoria(a.duenoB).isEmpty(), "B no ve lo de A")
        val editarComoB = client.put("/api/asistente/memoria/${deA.id}") {
            with(a) { como(a.duenoB) }
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(GuardarRecuerdoRequest.serializer(), GuardarRecuerdoRequest("hackeado")))
        }
        assertEquals(HttpStatusCode.NotFound, editarComoB.status)
        assertEquals(HttpStatusCode.NotFound, client.delete("/api/asistente/memoria/${deA.id}") { with(a) { como(a.duenoB) } }.status)
        assertEquals("El bono de Glim de \$55.500 no es mensual", memoria(a.duenoA).single().texto)

        // Y en el contexto de B no aparece.
        modelos += ModeloDeGuion(listOf(RespuestaDelModelo.Texto("Hola.")))
        preguntar("hola", uid = a.duenoB)
        assertNull(pedidos.last().memoria)
    }

    @Test
    fun `el dueno corrige y borra lo que Movi recuerda`() = testApplication {
        montar()
        val r = json.decodeFromString(RecuerdoDelAsistente.serializer(), guardar("Pago el colegio de mi hija el 25").bodyAsText())

        val editado = client.put("/api/asistente/memoria/${r.id}") {
            with(a) { como(a.duenoA) }
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(GuardarRecuerdoRequest.serializer(), GuardarRecuerdoRequest("Pago el colegio de mi hija el 5", OrigenDelRecuerdo.A_MANO)))
        }
        assertEquals(HttpStatusCode.OK, editado.status)
        val ahora = memoria(a.duenoA).single()
        assertEquals("Pago el colegio de mi hija el 5", ahora.texto)
        assertTrue(ahora.editadoEn != null)

        assertEquals(HttpStatusCode.NoContent, client.delete("/api/asistente/memoria/${r.id}") { with(a) { como(a.duenoA) } }.status)
        assertTrue(memoria(a.duenoA).isEmpty())
    }

    @Test
    fun `un recuerdo vacio o demasiado largo no se guarda`() = testApplication {
        montar()
        assertEquals(HttpStatusCode.BadRequest, guardar("   ").status)
        assertEquals(HttpStatusCode.BadRequest, guardar("x".repeat(301)).status)
        assertEquals(0L, filas())
    }

    @Test
    fun `el bloque de la memoria tiene tope y dice cuantos quedaron afuera`() {
        val muchos = (1..100).map { RecuerdoDelAsistente("m$it", "Recuerdo número $it con algo de texto para ocupar lugar", creadoEn = it.toLong()) }
        val bloque = memoriaParaElContexto(muchos)!!
        assertTrue(bloque.length <= TOPE_DE_LA_MEMORIA_EN_EL_CONTEXTO + 300, "${bloque.length}")
        assertTrue("Recuerdo número 100 " in bloque, "entran los más nuevos")
        assertFalse("Recuerdo número 1 " in bloque, "los más viejos quedan afuera")
        assertTrue("más viejos que no caben" in bloque)
        assertNull(memoriaParaElContexto(emptyList()))
    }
}
