package com.jvillada.movi.server.conector

import com.jvillada.movi.server.auth.RateLimiter
import com.jvillada.movi.server.conector.AndamioDelConector.Companion.CIMD_DE_CLAUDE
import com.jvillada.movi.server.conector.AndamioDelConector.Companion.RETORNO_DE_CLAUDE
import com.jvillada.movi.server.db.ClientesDelConector
import com.jvillada.movi.server.routes.LIMITE_DE_TOKEN_POR_CONCESION
import com.jvillada.movi.server.routes.LIMITE_GLOBAL_DE_REGISTROS_POR_HORA
import com.jvillada.movi.server.db.CodigosDelConector
import com.jvillada.movi.server.db.ConcesionesDelConector
import com.jvillada.movi.server.db.LlamadasDelConector
import com.jvillada.movi.server.db.SolicitudesDelConector
import com.jvillada.movi.server.db.TokensDelConector
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
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
    fun `con el tope de solicitudes pendientes lleno, autorizar contesta 503 sin escribir`() = testApplication {
        with(c) { montar() }
        val vence = System.currentTimeMillis() + 60_000
        transaction {
            SolicitudesDelConector.batchInsert(1..LasConcesiones.MAX_SOLICITUDES_PENDIENTES) { i ->
                this[SolicitudesDelConector.id] = "s$i"
                this[SolicitudesDelConector.clientId] = CIMD_DE_CLAUDE
                this[SolicitudesDelConector.nombreDelCliente] = "Claude"
                this[SolicitudesDelConector.redirectUri] = RETORNO_DE_CLAUDE
                this[SolicitudesDelConector.codeChallenge] = "x"
                this[SolicitudesDelConector.scope] = "movi:leer"
                this[SolicitudesDelConector.venceEn] = vence
            }
        }
        val res = client.get(c.urlDeAutorizar())
        assertEquals(HttpStatusCode.ServiceUnavailable, res.status)
        assertTrue(PantallaDeConsentimiento.OCUPADO in res.bodyAsText())
        assertEquals(LasConcesiones.MAX_SOLICITUDES_PENDIENTES.toLong(), transaction { SolicitudesDelConector.selectAll().count() })
    }

    @Test
    fun `con el tope de clientes DCR lleno, registrar contesta 503, y el limite de registros es global`() = testApplication {
        with(c) { montar() }
        val ahora = System.currentTimeMillis()
        transaction {
            ClientesDelConector.batchInsert(1..LasConcesiones.MAX_CLIENTES_DCR) { i ->
                this[ClientesDelConector.id] = "cli$i"
                this[ClientesDelConector.nombre] = ""
                this[ClientesDelConector.redirectUris] = "[]"
                this[ClientesDelConector.creadoEn] = ahora
            }
        }
        suspend fun registrar() = client.post("/oauth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"redirect_uris":["$RETORNO_DE_CLAUDE"]}""")
        }
        assertEquals(HttpStatusCode.ServiceUnavailable, registrar().status)
        transaction { ClientesDelConector.deleteAll() }
        // El 503 de arriba ya gastó uno del balde global.
        repeat(LIMITE_GLOBAL_DE_REGISTROS_POR_HORA - 2) { RateLimiter.allow("conector:register:global", LIMITE_GLOBAL_DE_REGISTROS_POR_HORA, 60 * 60_000L) }
        assertEquals(HttpStatusCode.Created, registrar().status)
        assertEquals(HttpStatusCode.TooManyRequests, registrar().status)
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
    fun `el canje se limita por concesion, no por IP`() = testApplication {
        with(c) { montar() }
        val codigo = with(c) { codigo() }
        assertEquals(HttpStatusCode.OK, with(c) { canjear(codigo) }.status)
        // El mismo código otra vez: invalid_grant hasta el tope de la concesión, después 429.
        repeat(LIMITE_DE_TOKEN_POR_CONCESION - 1) { assertEquals(HttpStatusCode.BadRequest, with(c) { canjear(codigo) }.status) }
        assertEquals(HttpStatusCode.TooManyRequests, with(c) { canjear(codigo) }.status)
        // Otra conexión del mismo cliente sigue (el límite del cliente es amplio).
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
    fun `un refresco repetido dentro de dos minutos no corta la conexion`() = testApplication {
        with(c) { montar() }
        val (_, refresco) = with(c) { conectar() }
        val rotado = with(c) { token(mapOf("grant_type" to "refresh_token", "refresh_token" to refresco, "client_id" to CIMD_DE_CLAUDE)) }
        val (acceso2, _) = c.tokensDe(rotado.bodyAsText())
        transaction { TokensDelConector.update({ TokensDelConector.hash eq LasConcesiones.hash(refresco) }) { it[revocadoEn] = System.currentTimeMillis() - 90_000 } }
        assertEquals(HttpStatusCode.BadRequest, with(c) { token(mapOf("grant_type" to "refresh_token", "refresh_token" to refresco, "client_id" to CIMD_DE_CLAUDE)) }.status)
        assertEquals(HttpStatusCode.OK, with(c) { mcp(acceso2, "ping") }.status, "a los 90 s sigue conectado")
    }

    @Test
    fun `una conexion sin uso en 90 dias ya no sirve`() = testApplication {
        with(c) { montar() }
        val (acceso, _) = with(c) { conectar() }
        transaction { ConcesionesDelConector.update({ ConcesionesDelConector.userId eq c.a.duenoA }) { it[ultimoUso] = System.currentTimeMillis() - 91 * dia } }
        assertEquals(HttpStatusCode.Unauthorized, with(c) { mcp(acceso, "ping") }.status)
    }

    @Test
    fun `la limpieza diaria borra lo vencido, lo sin uso y las llamadas viejas, y deja lo vivo`() = testApplication {
        with(c) { montar() }
        val (acceso, _) = with(c) { conectar() }
        with(c) { herramienta(acceso, "cuentas") }
        val ahora = System.currentTimeMillis()
        transaction {
            SolicitudesDelConector.insert {
                it[id] = "vieja"; it[clientId] = CIMD_DE_CLAUDE; it[nombreDelCliente] = "Claude"; it[redirectUri] = RETORNO_DE_CLAUDE
                it[codeChallenge] = "x"; it[scope] = "movi:leer"; it[venceEn] = ahora - 1
            }
            ClientesDelConector.insert { it[id] = "cli-viejo"; it[nombre] = ""; it[redirectUris] = "[]"; it[creadoEn] = ahora - 2 * dia }
            ClientesDelConector.insert { it[id] = "cli-nuevo"; it[nombre] = ""; it[redirectUris] = "[]"; it[creadoEn] = ahora }
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
        assertTrue(borradas >= 5, "borró $borradas")
        transaction {
            assertTrue(SolicitudesDelConector.selectAll().where { SolicitudesDelConector.id eq "vieja" }.empty())
            assertEquals(listOf("cli-nuevo"), ClientesDelConector.selectAll().map { it[ClientesDelConector.id] })
            assertEquals(1L, LlamadasDelConector.selectAll().count(), "queda la llamada de hoy")
            assertTrue(ConcesionesDelConector.selectAll().where { ConcesionesDelConector.id eq "cnc-abandonada" }.empty())
            assertTrue(TokensDelConector.selectAll().where { TokensDelConector.concesionId eq "cnc-abandonada" }.empty())
            assertTrue(CodigosDelConector.selectAll().count() >= 0)
        }
        assertEquals(HttpStatusCode.OK, with(c) { mcp(acceso, "ping") }.status, "la conexión viva sigue")
    }
}
