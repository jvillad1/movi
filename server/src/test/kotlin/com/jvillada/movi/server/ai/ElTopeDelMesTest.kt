package com.jvillada.movi.server.ai

import com.jvillada.movi.server.db.AiTurns
import com.jvillada.movi.server.routes.FabricaDeModelos
import com.jvillada.movi.server.routes.adminIaRoutes
import com.jvillada.movi.server.routes.aiRoutes
import com.jvillada.movi.server.time.AppClock
import com.jvillada.movi.shared.model.AiChatRequest
import com.jvillada.movi.shared.model.AiChatResponse
import com.jvillada.movi.shared.model.ChatMessage
import com.jvillada.movi.shared.model.ChatRole
import com.jvillada.movi.shared.model.ConversacionDelAsistente
import com.jvillada.movi.shared.model.GuardarTopeDeLaIa
import com.jvillada.movi.shared.model.IA_TOPE_DEL_MES
import com.jvillada.movi.shared.model.PreguntaSugerida
import com.jvillada.movi.shared.model.TOPE_DE_PREGUNTAS_POR_DEFECTO
import com.jvillada.movi.shared.model.TopesDeLaIa
import com.jvillada.movi.shared.model.fraseDelTopeDelMes
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.ZonedDateTime
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * # El tope de preguntas libres por mes (vuelta 106)
 *
 * N preguntas libres por mes calendario: en la N-1 contesta, en la N contesta y avisa que fue la
 * última, en la N+1 da 429 `IA_TOPE_DEL_MES` con la frase legible. Solo cuenta lo que va al modelo
 * (las tres sugeridas no), el admin no tiene tope, una excepción por usuario gana sobre el global, y
 * el mes nuevo empieza de cero. Con un modelo de mentira: ninguna prueba llama a Anthropic.
 */
class ElTopeDelMesTest {

    private lateinit var a: AndamioDelAsistente
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private var llamadasAlModelo = 0

    /** Un modelo que contesta sin cifras (el verificador no tiene nada que revisar). */
    private val fabrica: FabricaDeModelos = {
        llamadasAlModelo++
        object : ElModeloQueSeCorrige {
            override suspend fun siguienteVuelta(puedeUsarHerramientas: Boolean) = RespuestaDelModelo.Texto("Listo, te cuento.")
            override fun anotarResultados(resultados: List<Pair<String, String>>) = Unit
            override fun anotarCorreccion(correccion: String) = Unit
        }
    }

    @BeforeTest
    fun setUp() {
        a = AndamioDelAsistente("tope_del_mes")
        System.clearProperty("movi.admin.userIds")
        llamadasAlModelo = 0
    }

    @AfterTest
    fun limpiar() { System.clearProperty("movi.admin.userIds") }

    private fun ApplicationTestBuilder.montar() = with(a) {
        application { modulo { aiRoutes(fabrica); adminIaRoutes() } }
    }

    private suspend fun ApplicationTestBuilder.preguntar(texto: String = "¿cuánto gasté en Comida?", uid: String = a.duenoA, sugerida: PreguntaSugerida? = null): HttpResponse =
        client.post("/api/ai/chat") {
            with(a) { como(uid) }
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(AiChatRequest.serializer(), AiChatRequest(listOf(ChatMessage(ChatRole.USER, texto, preguntaSugerida = sugerida?.id)))))
        }

    private fun HttpResponse.cuerpo(): AiChatResponse = runBlocking { json.decodeFromString(AiChatResponse.serializer(), bodyAsText()) }

    /** [cuantas] preguntas libres ya hechas por [uid], en el instante [cuando]. */
    private fun yaPregunto(cuantas: Int, uid: String = a.duenoA, cuando: Long = System.currentTimeMillis() - 60_000, modelo: String = MODELO_DE_TODOS_LOS_DIAS) =
        transaction {
            repeat(cuantas) { i ->
                AiTurns.insert {
                    it[id] = "ai_previa_${uid}_${cuando}_$i"; it[userId] = uid; it[creadoEn] = cuando - i
                    it[pregunta] = "previa $i"; it[respuesta] = "r"; it[consultas] = "[]"; it[AiTurns.modelo] = modelo
                    it[criterio] = false; it[vueltas] = 1; it[fichasEntrada] = 100; it[fichasCache] = 0; it[fichasSalida] = 50
                    it[imagen] = false
                }
            }
        }

    private fun topeGlobal(n: Int) = runBlocking { ElTopeDelMes.guardarGlobal(n) }

    @Test
    fun `en la N-1 contesta, en la N avisa y en la N+1 da 429 con la frase`() = testApplication {
        montar()
        topeGlobal(3)
        yaPregunto(1)

        // La segunda de tres (N-1): contesta, y como quedan pocas ya avisa que queda una.
        val n1 = preguntar()
        assertEquals(HttpStatusCode.OK, n1.status, n1.bodyAsText())
        assertEquals("Listo, te cuento.", n1.cuerpo().text)
        assertEquals(1, n1.cuerpo().preguntasQueQuedan)

        // La tercera (N): contesta y avisa que fue la última.
        val n = preguntar()
        assertEquals(HttpStatusCode.OK, n.status)
        assertEquals("Listo, te cuento.", n.cuerpo().text)
        assertEquals(0, n.cuerpo().preguntasQueQuedan)
        assertEquals(3, n.cuerpo().topeDelMes)

        // La cuarta (N+1): 429, sin llamar al modelo, con la frase que entiende un APK viejo.
        val antes = llamadasAlModelo
        val pasada = preguntar()
        assertEquals(HttpStatusCode.TooManyRequests, pasada.status)
        assertEquals(IA_TOPE_DEL_MES, pasada.cuerpo().codigo)
        assertEquals("Llegaste a las 3 preguntas de este mes. Se renuevan el 1.º", pasada.cuerpo().text)
        assertEquals(fraseDelTopeDelMes(3), pasada.cuerpo().text)
        assertEquals(antes, llamadasAlModelo, "pasado el tope no se le pregunta al modelo")
    }

    @Test
    fun `con muchas por delante no avisa nada`() = testApplication {
        montar()
        val r = preguntar()
        assertEquals(HttpStatusCode.OK, r.status)
        assertNull(r.cuerpo().preguntasQueQuedan, "con ${TOPE_DE_PREGUNTAS_POR_DEFECTO - 1} por delante no hay aviso")
        assertNull(r.cuerpo().topeDelMes)
    }

    @Test
    fun `por defecto son 100 al mes`() = testApplication {
        montar()
        yaPregunto(TOPE_DE_PREGUNTAS_POR_DEFECTO - 10)
        val aviso = preguntar()
        assertEquals(9, aviso.cuerpo().preguntasQueQuedan, "a las 91: «Te quedan 9 preguntas este mes»")

        yaPregunto(9, cuando = System.currentTimeMillis() - 30_000)
        assertEquals(HttpStatusCode.TooManyRequests, preguntar().status)
    }

    @Test
    fun `las sugeridas no cuentan y siguen funcionando pasado el tope`() = testApplication {
        montar()
        topeGlobal(1)
        // Diez sugeridas contestadas sin el modelo: no gastan ninguna pregunta libre.
        yaPregunto(10, modelo = MODELO_SIN_IA)
        assertEquals(HttpStatusCode.OK, preguntar().status, "las sugeridas no gastaron el tope")
        assertEquals(HttpStatusCode.TooManyRequests, preguntar().status)

        // Pasado el tope, las tres siguen contestando.
        val sugerida = preguntar(PreguntaSugerida.COMO_VOY.texto, sugerida = PreguntaSugerida.COMO_VOY)
        assertEquals(HttpStatusCode.OK, sugerida.status, sugerida.bodyAsText())
        assertNull(sugerida.cuerpo().codigo)
    }

    @Test
    fun `el admin no tiene tope`() = testApplication {
        montar()
        System.setProperty("movi.admin.userIds", a.duenoA)
        topeGlobal(2)
        yaPregunto(50)

        val r = preguntar()
        assertEquals(HttpStatusCode.OK, r.status)
        assertNull(r.cuerpo().preguntasQueQuedan, "sin tope no hay aviso")
    }

    @Test
    fun `una excepcion por usuario gana sobre el global`() = testApplication {
        montar()
        topeGlobal(2)
        runBlocking { ElTopeDelMes.guardarExcepcion(a.duenoB, 20) }
        yaPregunto(2, uid = a.duenoA)
        yaPregunto(2, uid = a.duenoB)

        assertEquals(HttpStatusCode.TooManyRequests, preguntar(uid = a.duenoA).status, "A tiene el global: 2")
        assertEquals(HttpStatusCode.OK, preguntar(uid = a.duenoB).status, "B tiene su excepción: 20")

        // Quitada la excepción, a B le vuelve a tocar el global.
        runBlocking { ElTopeDelMes.quitarExcepcion(a.duenoB) }
        assertEquals(HttpStatusCode.TooManyRequests, preguntar(uid = a.duenoB).status)
    }

    @Test
    fun `el mes nuevo empieza de cero`() = testApplication {
        montar()
        topeGlobal(2)
        val delMesPasado = ElTopeDelMes.inicioDelMes(System.currentTimeMillis()) - 60_000
        yaPregunto(30, cuando = delMesPasado)

        assertEquals(HttpStatusCode.OK, preguntar().status, "lo del mes pasado no cuenta")
    }

    @Test
    fun `el mes es el de calendario en la zona de la app`() {
        val zona = AppClock.zone
        val ultimoMinutoDeOctubre = ZonedDateTime.of(2026, 10, 31, 23, 59, 0, 0, zona).toInstant().toEpochMilli()
        val primeroDeNoviembre = ZonedDateTime.of(2026, 11, 1, 0, 0, 0, 0, zona).toInstant().toEpochMilli()
        assertEquals(ZonedDateTime.of(2026, 10, 1, 0, 0, 0, 0, zona).toInstant().toEpochMilli(), ElTopeDelMes.inicioDelMes(ultimoMinutoDeOctubre))
        assertEquals(primeroDeNoviembre, ElTopeDelMes.inicioDelMes(primeroDeNoviembre + 5_000))
    }

    @Test
    fun `al llegar al tope, la conversacion dice que la IA no esta para preguntas libres`() = testApplication {
        montar()
        topeGlobal(1)
        val get = suspend { json.decodeFromString(ConversacionDelAsistente.serializer(), client.get("/api/ai/conversacion") { with(a) { como(a.duenoA) } }.bodyAsText()) }
        assertTrue(get().iaDisponible)
        yaPregunto(1)
        assertFalse(get().iaDisponible, "solo quedan las tres sugeridas")
    }

    // ── La pantalla del admin ───────────────────────────────────────────────

    @Test
    fun `solo el admin ve y cambia los topes`() = testApplication {
        montar()
        assertEquals(HttpStatusCode.Forbidden, client.get("/api/admin/ia/topes") { with(a) { como(a.duenoA) } }.status)
        assertEquals(HttpStatusCode.Forbidden, client.get("/api/admin/ia/costo") { with(a) { como(a.duenoA) } }.status)
        assertEquals(
            HttpStatusCode.Forbidden,
            client.put("/api/admin/ia/topes/global") {
                with(a) { como(a.duenoA) }; contentType(ContentType.Application.Json); setBody("""{"tope":5}""")
            }.status,
        )
    }

    @Test
    fun `el admin elige el global y las excepciones desde la app`() = testApplication {
        montar()
        System.setProperty("movi.admin.userIds", a.duenoA)
        fun topes(r: HttpResponse) = runBlocking { json.decodeFromString(TopesDeLaIa.serializer(), r.bodyAsText()) }

        val inicial = topes(client.get("/api/admin/ia/topes") { with(a) { como(a.duenoA) } })
        assertEquals(TOPE_DE_PREGUNTAS_POR_DEFECTO, inicial.global)
        assertFalse(inicial.globalElegido)

        val global = client.put("/api/admin/ia/topes/global") {
            with(a) { como(a.duenoA) }; contentType(ContentType.Application.Json)
            setBody(json.encodeToString(GuardarTopeDeLaIa.serializer(), GuardarTopeDeLaIa(40)))
        }
        assertEquals(HttpStatusCode.OK, global.status)
        assertEquals(40, topes(global).global)
        assertTrue(topes(global).globalElegido)

        yaPregunto(3, uid = a.duenoB)
        val excepcion = client.put("/api/admin/ia/topes/usuarios/${a.duenoB}") {
            with(a) { como(a.duenoA) }; contentType(ContentType.Application.Json)
            setBody(json.encodeToString(GuardarTopeDeLaIa.serializer(), GuardarTopeDeLaIa(300)))
        }
        val conExcepcion = topes(excepcion)
        assertEquals(listOf(a.duenoB to 300), conExcepcion.excepciones.map { it.usuarioId to it.tope })
        assertEquals(3, conExcepcion.usuarios.first { it.usuarioId == a.duenoB }.usadasEsteMes)
        assertTrue(conExcepcion.usuarios.first { it.usuarioId == a.duenoA }.esAdmin)

        val quitada = client.delete("/api/admin/ia/topes/usuarios/${a.duenoB}") { with(a) { como(a.duenoA) } }
        assertTrue(topes(quitada).excepciones.isEmpty())

        val mala = client.put("/api/admin/ia/topes/global") {
            with(a) { como(a.duenoA) }; contentType(ContentType.Application.Json); setBody("""{"tope":-1}""")
        }
        assertEquals(HttpStatusCode.UnprocessableEntity, mala.status)
        val nadie = client.put("/api/admin/ia/topes/usuarios/no-existe") {
            with(a) { como(a.duenoA) }; contentType(ContentType.Application.Json); setBody("""{"tope":5}""")
        }
        assertEquals(HttpStatusCode.NotFound, nadie.status)
    }

    @Test
    fun `el tope no bloquea si no se puede leer su configuracion`() = runBlocking {
        transaction { org.jetbrains.exposed.sql.SchemaUtils.drop(com.jvillada.movi.server.db.AjustesDeLaIa) }
        assertEquals(ElTopeDelMes.Estado.SIN_TOPE, ElTopeDelMes.estadoDe(a.duenoA))
    }
}
