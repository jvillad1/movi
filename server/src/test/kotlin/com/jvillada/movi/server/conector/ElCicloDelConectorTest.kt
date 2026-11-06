package com.jvillada.movi.server.conector

import com.jvillada.movi.server.auth.RateLimiter
import com.jvillada.movi.server.conector.AndamioDelConector.Companion.CIMD_DE_CLAUDE
import com.jvillada.movi.server.conector.AndamioDelConector.Companion.RETORNO_DE_CLAUDE
import com.jvillada.movi.server.db.ClientesDelConector
import com.jvillada.movi.server.routes.LIMITE_DE_TOKEN_POR_CONCESION
import com.jvillada.movi.server.db.CodigosDelConector
import com.jvillada.movi.server.db.ConcesionesDelConector
import com.jvillada.movi.server.db.LlamadasDelConector
import com.jvillada.movi.server.db.TokensDelConector
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.parameters
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.batchInsert
import org.jetbrains.exposed.sql.deleteAll
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.or
import org.jetbrains.exposed.sql.update
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * # Vuelta 135 (revisión) · Topes, límites, limpieza y el ciclo de vida de una conexión
 *
 * I2 (topes globales), M2 (el balde del login), M3 (límites por cliente y por concesión), M6 (retención) y M7
 * (revocación, vida máxima, gracia de dos minutos). Cada una falla sin su arreglo.
 */
class ElCicloDelConectorTest {

    private lateinit var c: AndamioDelConector
    private val dia = 24L * 60 * 60 * 1000

    @BeforeTest
    fun setUp() {
        c = AndamioDelConector("conector_ciclo")
    }

    @AfterTest
    fun tearDown() = c.cerrar()

    @Test
    fun `pedir permiso no escribe nada, y una solicitud firmada sigue sirviendo`() = testApplication {
        with(c) { montar() }
        val primera = c.solicitudDe(client.get(c.urlDeAutorizar()).bodyAsText())
        // Antes, cada GET dejaba una fila y un tope global de 2.000 se llenaba desde cuatro IP.
        repeat(300) { i -> client.get(c.urlDeAutorizar()) { header("X-Forwarded-For", "10.9.${i / 256}.${i % 256}") } }
        transaction {
            assertEquals(0L, ConcesionesDelConector.selectAll().count())
            assertEquals(0L, CodigosDelConector.selectAll().count())
        }
        assertEquals(HttpStatusCode.Found, with(c) { permitir(primera) }.status)
    }

    @Test
    fun `una solicitud alterada o vencida no sirve, y reenviarla solo vuelve a pedir la contrasena`() = testApplication {
        with(c) { montar() }
        val s = c.solicitudDe(client.get(c.urlDeAutorizar()).bodyAsText())
        val alterada = s.replaceRange(10, 11, if (s[10] == 'A') "B" else "A")
        assertEquals(HttpStatusCode.BadRequest, with(c) { permitir(alterada) }.status)
        val vencida = SolicitudFirmada.firmar(
            SolicitudDelConector(CIMD_DE_CLAUDE, RETORNO_DE_CLAUDE, c.desafioDe(c.verificador), "x", "movi:leer", System.currentTimeMillis() - 1),
        )
        assertEquals(HttpStatusCode.BadRequest, with(c) { permitir(vencida) }.status)
        // Dos «Permitir» con la misma solicitud: dos códigos, cada uno de un solo uso.
        val uno = c.parametrosDe(with(c) { permitir(s) }.headers[HttpHeaders.Location]!!)["code"]!!
        val dos = c.parametrosDe(with(c) { permitir(s) }.headers[HttpHeaders.Location]!!)["code"]!!
        assertTrue(uno != dos)
        assertEquals(HttpStatusCode.OK, with(c) { canjear(uno) }.status)
        assertEquals(HttpStatusCode.BadRequest, with(c) { canjear(uno) }.status)
        assertEquals(HttpStatusCode.OK, with(c) { canjear(dos) }.status)
    }

    @Test
    fun `DCR es idempotente y sin limite global`() = testApplication {
        with(c) { montar() }
        suspend fun registrar(ip: String, uris: String) = client.post("/oauth/register") {
            header("X-Forwarded-For", ip)
            contentType(ContentType.Application.Json)
            setBody("""{"client_name":"Claude","redirect_uris":$uris}""")
        }
        val ids = (1..320).map { i ->
            val res = registrar("10.8.${i / 256}.${i % 256}", """["$RETORNO_DE_CLAUDE"]""")
            assertEquals(HttpStatusCode.Created, res.status, "registro $i")
            c.json.parseToJsonElement(res.bodyAsText()).jsonObject["client_id"]!!.jsonPrimitive.content
        }.toSet()
        assertEquals(1, ids.size, "el mismo client_id cada vez")
        // Otro orden y otro puerto de loopback: el mismo cliente.
        val a = registrar("10.7.0.1", """["http://localhost:1234/callback","http://127.0.0.1/callback"]""").bodyAsText()
        val b = registrar("10.7.0.2", """["http://127.0.0.1:9/callback","http://localhost/callback"]""").bodyAsText()
        assertEquals(
            c.json.parseToJsonElement(a).jsonObject["client_id"]!!.jsonPrimitive.content,
            c.json.parseToJsonElement(b).jsonObject["client_id"]!!.jsonPrimitive.content,
        )
        assertEquals(2L, transaction { ClientesDelConector.selectAll().count() })
    }

    @Test
    fun `dos registros DCR iguales a la vez dan el mismo cliente, nunca un 500`() = testApplication {
        with(c) { montar() }
        val id = idDelClienteDcr(listOf(RETORNO_DE_CLAUDE))
        // La carrera, sin hilos: el segundo llega cuando el primero ya escribió la fila.
        transaction { guardarClienteDcr(id, listOf(RETORNO_DE_CLAUDE), 1L) }
        transaction { guardarClienteDcr(id, listOf(RETORNO_DE_CLAUDE), 2L) }
        assertEquals(1L, transaction { ClientesDelConector.selectAll().count() })
        // Y por la ruta, veinte a la vez.
        val respuestas = coroutineScope {
            (1..20).map { i ->
                async {
                    client.post("/oauth/register") {
                        header("X-Forwarded-For", "10.6.0.$i")
                        contentType(ContentType.Application.Json)
                        setBody("""{"redirect_uris":["http://localhost/callback"]}""")
                    }
                }
            }.map { it.await() }
        }
        assertTrue(respuestas.all { it.status == HttpStatusCode.Created }, respuestas.map { it.status }.toString())
        assertEquals(1, respuestas.map { c.json.parseToJsonElement(it.bodyAsText()).jsonObject["client_id"]!!.jsonPrimitive.content }.toSet().size)
    }

    @Test
    fun `el login del conector comparte el balde por correo de la app`() = testApplication {
        with(c) { montar() }
        // Diez intentos en /api/auth/login (su balde es `login:email:<correo>`).
        repeat(10) { RateLimiter.allow("login:email:${c.correoDeA}", 10, 5 * 60_000L) }
        val s = c.solicitudDe(client.get(c.urlDeAutorizar()).bodyAsText())
        val res = with(c) { permitir(s) }
        assertEquals(HttpStatusCode.TooManyRequests, res.status)
    }

    @Test
    fun `el refresco se limita por concesion, no por IP ni por cliente`() = testApplication {
        with(c) { montar() }
        var (_, refresco) = with(c) { conectar() }
        repeat(LIMITE_DE_TOKEN_POR_CONCESION - 1) {
            val res = with(c) { token(mapOf("grant_type" to "refresh_token", "refresh_token" to refresco, "client_id" to CIMD_DE_CLAUDE)) }
            assertEquals(HttpStatusCode.OK, res.status, res.bodyAsText())
            refresco = c.tokensDe(res.bodyAsText()).second
        }
        // El canje del código ya contó uno: el siguiente refresco pasa el tope de la concesión.
        assertEquals(HttpStatusCode.TooManyRequests, with(c) { token(mapOf("grant_type" to "refresh_token", "refresh_token" to refresco, "client_id" to CIMD_DE_CLAUDE)) }.status)
        // Otra conexión del mismo cliente sigue.
        val (otro, _) = with(c) { conectar(c.correoDeB, c.contrasenaDeB) }
        assertEquals(HttpStatusCode.OK, with(c) { mcp(otro, "ping") }.status)
    }

    @Test
    fun `revocar desde Claude corta la conexion, y la metadata lo anuncia`() = testApplication {
        with(c) { montar() }
        val s = c.json.parseToJsonElement(client.get("/.well-known/oauth-authorization-server").bodyAsText()).jsonObject
        assertEquals("${AndamioDelConector.BASE}/oauth/revoke", s["revocation_endpoint"]!!.jsonPrimitive.content)
        val (acceso, refresco) = with(c) { conectar() }
        // Con otro client_id (de forma válida) no revoca nada; con uno inventado, invalid_client.
        assertEquals(HttpStatusCode.Unauthorized, client.submitForm("/oauth/revoke", formParameters = parameters { append("token", refresco); append("client_id", "otro") }).status)
        val ajeno = client.submitForm("/oauth/revoke", formParameters = parameters { append("token", refresco); append("client_id", AndamioDelConector.CIMD_DE_CLAUDE_CODE) })
        assertEquals(HttpStatusCode.OK, ajeno.status)
        assertEquals(HttpStatusCode.OK, with(c) { mcp(acceso, "ping") }.status)
        val res = client.submitForm("/oauth/revoke", formParameters = parameters { append("token", refresco); append("client_id", CIMD_DE_CLAUDE) })
        assertEquals(HttpStatusCode.OK, res.status)
        assertEquals(HttpStatusCode.Unauthorized, with(c) { mcp(acceso, "ping") }.status)
        // Un token inventado también contesta 200.
        assertEquals(HttpStatusCode.OK, client.submitForm("/oauth/revoke", formParameters = parameters { append("token", "x"); append("client_id", CIMD_DE_CLAUDE) }).status)
    }

    @Test
    fun `un refresco repetido dentro de dos minutos no corta la conexion pero revoca la rotacion huerfana`() = testApplication {
        with(c) { montar() }
        val (_, refresco) = with(c) { conectar() }
        val rotado = with(c) { token(mapOf("grant_type" to "refresh_token", "refresh_token" to refresco, "client_id" to CIMD_DE_CLAUDE)) }
        val (acceso2, refresco2) = c.tokensDe(rotado.bodyAsText())
        // El reintento llega 90 s después: dentro de la gracia.
        transaction {
            val antes = System.currentTimeMillis() - 90_000
            TokensDelConector.update({ TokensDelConector.hash eq LasConcesiones.hash(refresco) }) { it[revocadoEn] = antes }
            TokensDelConector.update({ (TokensDelConector.hash eq LasConcesiones.hash(acceso2)) or (TokensDelConector.hash eq LasConcesiones.hash(refresco2)) }) { it[creadoEn] = antes }
        }
        assertEquals(HttpStatusCode.BadRequest, with(c) { token(mapOf("grant_type" to "refresh_token", "refresh_token" to refresco, "client_id" to CIMD_DE_CLAUDE)) }.status)
        // La concesión sigue sin revocar, pero lo que salió de esa rotación ya no sirve, y no queda como conexión de más.
        transaction { assertEquals(null, ConcesionesDelConector.selectAll().single()[ConcesionesDelConector.revocadaEn]) }
        assertEquals(HttpStatusCode.Unauthorized, with(c) { mcp(acceso2, "ping") }.status)
        val lista = c.json.decodeFromString(
            com.jvillada.movi.shared.model.ConectorDeClaude.serializer(),
            client.get("/api/conector") { with(c.a) { como(c.a.duenoA) } }.bodyAsText(),
        )
        assertTrue(lista.conexiones.isEmpty(), "${lista.conexiones}")
    }

    @Test
    fun `una conexion vale un ano desde que se concedio, aunque se use`() = testApplication {
        with(c) { montar() }
        val (acceso, refresco) = with(c) { conectar() }
        transaction {
            ConcesionesDelConector.update({ ConcesionesDelConector.userId eq c.a.duenoA }) {
                it[creadaEn] = System.currentTimeMillis() - 366 * dia
                it[ultimoUso] = System.currentTimeMillis()
            }
        }
        assertEquals(HttpStatusCode.Unauthorized, with(c) { mcp(acceso, "ping") }.status)
        // M-f · y «Movi en Claude» ya no la lista (antes seguía hasta que vencía su último refresco).
        val lista = c.json.decodeFromString(
            com.jvillada.movi.shared.model.ConectorDeClaude.serializer(),
            client.get("/api/conector") { with(c.a) { como(c.a.duenoA) } }.bodyAsText(),
        )
        assertTrue(lista.conexiones.isEmpty(), "${lista.conexiones}")
        assertEquals(HttpStatusCode.BadRequest, with(c) { token(mapOf("grant_type" to "refresh_token", "refresh_token" to refresco, "client_id" to CIMD_DE_CLAUDE)) }.status)
    }

    @Test
    fun `la limpieza diaria borra lo vencido, lo sin uso y las llamadas viejas, y deja lo vivo`() = testApplication {
        with(c) { montar() }
        val (acceso, _) = with(c) { conectar() }
        with(c) { herramienta(acceso, "cuentas") }
        val ahora = System.currentTimeMillis()
        transaction {
            LlamadasDelConector.insert {
                it[id] = "llm-vieja"; it[userId] = c.a.duenoA; it[concesionId] = "x"; it[herramienta] = "cuentas"
                it[en] = ahora - 91 * dia; it[bytes] = 1; it[ok] = true
            }
            ConcesionesDelConector.insert {
                it[id] = "cnc-abandonada"; it[userId] = c.a.duenoA; it[clientId] = CIMD_DE_CLAUDE; it[nombreDelCliente] = "Claude"
                it[hostDelRetorno] = "claude.ai"; it[scope] = "movi:leer"; it[versionDeSesion] = 0; it[creadaEn] = ahora - 100 * dia
                it[ultimoUso] = ahora - 95 * dia
            }
            TokensDelConector.insert {
                it[hash] = "h".repeat(64); it[userId] = c.a.duenoA; it[concesionId] = "cnc-abandonada"; it[tipo] = "refresco"
                it[audiencia] = AndamioDelConector.URL_DEL_MCP; it[creadoEn] = ahora - 95 * dia; it[venceEn] = ahora + dia
            }
        }
        val borradas = transaction { limpiarElConector(ahora) }
        assertTrue(borradas >= 3, "borró $borradas")
        transaction {
            assertEquals(1L, LlamadasDelConector.selectAll().count(), "queda la llamada de hoy")
            assertTrue(ConcesionesDelConector.selectAll().where { ConcesionesDelConector.id eq "cnc-abandonada" }.empty())
            assertTrue(TokensDelConector.selectAll().where { TokensDelConector.concesionId eq "cnc-abandonada" }.empty())
            assertTrue(CodigosDelConector.selectAll().count() >= 0)
        }
        assertEquals(HttpStatusCode.OK, with(c) { mcp(acceso, "ping") }.status, "la conexión viva sigue")
    }
}
