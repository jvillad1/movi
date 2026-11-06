package com.jvillada.movi.server.conector

import com.jvillada.movi.server.conector.AndamioDelConector.Companion.BASE
import com.jvillada.movi.server.conector.AndamioDelConector.Companion.CIMD_DE_CLAUDE
import com.jvillada.movi.server.conector.AndamioDelConector.Companion.CIMD_DE_CLAUDE_CODE
import com.jvillada.movi.server.conector.AndamioDelConector.Companion.RETORNO_DE_CLAUDE
import com.jvillada.movi.server.conector.AndamioDelConector.Companion.URL_DEL_MCP
import com.jvillada.movi.server.db.ConcesionesDelConector
import com.jvillada.movi.server.db.TokensDelConector
import com.jvillada.movi.server.db.Users
import com.jvillada.movi.server.routes.accountRoutes
import com.jvillada.movi.shared.model.ConectorDeClaude
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * # Vuelta 135 · El OAuth del conector de Movi para Claude
 *
 * Por las rutas de verdad, sin red (el CIMD se sirve de memoria). Cada prueba falla sin el cambio: sin las rutas,
 * `/mcp` y `/oauth/…` no existen (404) y los `.well-known` tampoco.
 */
class ConectorOAuthTest {

    private lateinit var c: AndamioDelConector

    @BeforeTest
    fun setUp() {
        c = AndamioDelConector("conector_oauth")
    }

    @AfterTest
    fun tearDown() = c.cerrar()

    // ── Descubrimiento ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `mcp sin token contesta 401 con el WWW-Authenticate que lleva a la metadata del recurso`() = testApplication {
        with(c) { montar() }
        val res = with(c) { mcp(null, "initialize") }
        assertEquals(HttpStatusCode.Unauthorized, res.status)
        val desafio = assertNotNull(res.headers[HttpHeaders.WWWAuthenticate])
        assertTrue(desafio.startsWith("Bearer "), desafio)
        assertTrue("resource_metadata=\"$BASE/.well-known/oauth-protected-resource\"" in desafio, desafio)
        assertTrue("scope=\"movi:leer\"" in desafio, desafio)
        // Con un token que no sirve, además `invalid_token`.
        val conBasura = with(c) { mcp("mvc_a_" + "x".repeat(43), "initialize") }
        assertEquals(HttpStatusCode.Unauthorized, conBasura.status)
        assertTrue("error=\"invalid_token\"" in conBasura.headers[HttpHeaders.WWWAuthenticate]!!)
    }

    @Test
    fun `los dos documentos well-known dicen los campos exactos`() = testApplication {
        with(c) { montar() }
        for (ruta in listOf("/.well-known/oauth-protected-resource", "/.well-known/oauth-protected-resource/mcp")) {
            val r = c.json.parseToJsonElement(client.get(ruta).bodyAsText()).jsonObject
            assertEquals(URL_DEL_MCP, r["resource"]!!.jsonPrimitive.content, "el recurso es exactamente la URL del MCP")
            assertEquals(listOf(BASE), r["authorization_servers"]!!.jsonArray.map { it.jsonPrimitive.content })
            assertEquals(listOf("movi:leer"), r["scopes_supported"]!!.jsonArray.map { it.jsonPrimitive.content })
        }
        val s = c.json.parseToJsonElement(client.get("/.well-known/oauth-authorization-server").bodyAsText()).jsonObject
        assertEquals(BASE, s["issuer"]!!.jsonPrimitive.content)
        assertEquals("$BASE/oauth/authorize", s["authorization_endpoint"]!!.jsonPrimitive.content)
        assertEquals("$BASE/oauth/token", s["token_endpoint"]!!.jsonPrimitive.content)
        assertEquals("$BASE/oauth/register", s["registration_endpoint"]!!.jsonPrimitive.content)
        assertEquals(listOf("S256"), s["code_challenge_methods_supported"]!!.jsonArray.map { it.jsonPrimitive.content })
        assertEquals(listOf("none"), s["token_endpoint_auth_methods_supported"]!!.jsonArray.map { it.jsonPrimitive.content })
        assertEquals("true", s["client_id_metadata_document_supported"]!!.jsonPrimitive.content)
        assertEquals(setOf("authorization_code", "refresh_token"), s["grant_types_supported"]!!.jsonArray.map { it.jsonPrimitive.content }.toSet())
        assertEquals("true", s["authorization_response_iss_parameter_supported"]!!.jsonPrimitive.content)
    }

    // ── El flujo ───────────────────────────────────────────────────────────────────────────────

    @Test
    fun `la pantalla de consentimiento dice quien pide, a que host vuelve y no se deja meter en un iframe`() = testApplication {
        with(c) { montar() }
        val res = client.get(c.urlDeAutorizar())
        assertEquals(HttpStatusCode.OK, res.status)
        val html = res.bodyAsText()
        assertTrue("Permitir que Claude lea tus datos de Movi" in html, html)
        assertTrue("<span class=\"host\">claude.ai</span>" in html, html)
        assertTrue(PantallaDeConsentimiento.QUE_PODRA_HACER in html)
        assertTrue("frame-ancestors 'none'" in res.headers["Content-Security-Policy"]!!)
        assertEquals("DENY", res.headers["X-Frame-Options"])
        assertEquals("no-store", res.headers[HttpHeaders.CacheControl])
    }

    @Test
    fun `el flujo completo con PKCE da tokens, el state y el iss vuelven, y el refresco rota`() = testApplication {
        with(c) { montar() }
        val pantalla = client.get(c.urlDeAutorizar(state = "mi estado & más"))
        val res = with(c) { permitir(c.solicitudDe(pantalla.bodyAsText())) }
        assertEquals(HttpStatusCode.Found, res.status)
        val location = res.headers[HttpHeaders.Location]!!
        assertTrue(location.startsWith("$RETORNO_DE_CLAUDE?"), location)
        val p = c.parametrosDe(location)
        assertEquals("mi estado & más", p["state"], "el state vuelve tal cual")
        assertEquals(BASE, p["iss"], "RFC 9207")
        val codigo = p["code"]!!

        val canje = with(c) { canjear(codigo) }
        assertEquals(HttpStatusCode.OK, canje.status, canje.bodyAsText())
        assertEquals("no-store", canje.headers[HttpHeaders.CacheControl])
        val cuerpo = c.json.parseToJsonElement(canje.bodyAsText()).jsonObject
        assertEquals("Bearer", cuerpo["token_type"]!!.jsonPrimitive.content)
        assertEquals("3600", cuerpo["expires_in"]!!.jsonPrimitive.content)
        assertEquals("movi:leer", cuerpo["scope"]!!.jsonPrimitive.content)
        val (acceso, refresco) = c.tokensDe(canje.bodyAsText())
        assertEquals(HttpStatusCode.OK, with(c) { mcp(acceso, "ping") }.status)
        // En la base solo están los hashes.
        transaction {
            val guardados = TokensDelConector.selectAll().map { it[TokensDelConector.hash] }
            assertFalse(acceso in guardados || refresco in guardados, "nunca el token en claro")
            assertTrue(guardados.all { it.length == 64 })
        }

        val rotado = with(c) { token(mapOf("grant_type" to "refresh_token", "refresh_token" to refresco, "client_id" to CIMD_DE_CLAUDE)) }
        assertEquals(HttpStatusCode.OK, rotado.status, rotado.bodyAsText())
        val (acceso2, refresco2) = c.tokensDe(rotado.bodyAsText())
        assertNotEquals(refresco, refresco2)
        assertEquals(HttpStatusCode.OK, with(c) { mcp(acceso2, "ping") }.status)

        // El refresco viejo ya no sirve…
        val viejo = with(c) { token(mapOf("grant_type" to "refresh_token", "refresh_token" to refresco, "client_id" to CIMD_DE_CLAUDE)) }
        assertEquals(HttpStatusCode.BadRequest, viejo.status)
        assertEquals("invalid_grant", c.json.parseToJsonElement(viejo.bodyAsText()).jsonObject["error"]!!.jsonPrimitive.content)
        // …pero dentro de la gracia (una carrera inocente) no corta la conexión.
        assertEquals(HttpStatusCode.OK, with(c) { mcp(acceso2, "ping") }.status)
    }

    @Test
    fun `un refresco viejo reusado pasada la gracia corta la conexion entera`() = testApplication {
        with(c) { montar() }
        val (_, refresco) = with(c) { conectar() }
        val rotado = with(c) { token(mapOf("grant_type" to "refresh_token", "refresh_token" to refresco, "client_id" to CIMD_DE_CLAUDE)) }
        val (acceso2, refresco2) = c.tokensDe(rotado.bodyAsText())
        // El viejo se cambió hace un minuto: ya no es una carrera.
        transaction {
            TokensDelConector.update({ TokensDelConector.hash eq LasConcesiones.hash(refresco) }) {
                it[revocadoEn] = System.currentTimeMillis() - 60_000
            }
        }
        val reuso = with(c) { token(mapOf("grant_type" to "refresh_token", "refresh_token" to refresco, "client_id" to CIMD_DE_CLAUDE)) }
        assertEquals(HttpStatusCode.BadRequest, reuso.status)
        assertEquals(HttpStatusCode.Unauthorized, with(c) { mcp(acceso2, "ping") }.status, "el acceso nuevo quedó revocado")
        val nuevo = with(c) { token(mapOf("grant_type" to "refresh_token", "refresh_token" to refresco2, "client_id" to CIMD_DE_CLAUDE)) }
        assertEquals(HttpStatusCode.BadRequest, nuevo.status, "y el refresco nuevo también")
    }

    @Test
    fun `un code_verifier malo da invalid_grant y el codigo ya no sirve`() = testApplication {
        with(c) { montar() }
        val codigo = with(c) { codigo() }
        val malo = with(c) { canjear(codigo, verificador = "otro-verificador-que-no-es-el-de-la-solicitud-1234567890") }
        assertEquals(HttpStatusCode.BadRequest, malo.status)
        assertEquals("invalid_grant", c.json.parseToJsonElement(malo.bodyAsText()).jsonObject["error"]!!.jsonPrimitive.content)
        assertEquals(HttpStatusCode.BadRequest, with(c) { canjear(codigo) }.status, "gastado")
    }

    @Test
    fun `el codigo es de un solo uso y reusarlo corta lo que salio de el`() = testApplication {
        with(c) { montar() }
        val codigo = with(c) { codigo() }
        val primera = with(c) { canjear(codigo) }
        assertEquals(HttpStatusCode.OK, primera.status)
        val (acceso, _) = c.tokensDe(primera.bodyAsText())
        val segunda = with(c) { canjear(codigo) }
        assertEquals(HttpStatusCode.BadRequest, segunda.status)
        assertEquals(HttpStatusCode.Unauthorized, with(c) { mcp(acceso, "ping") }.status)
    }

    @Test
    fun `un redirect_uri que el cliente no declaro se rechaza sin redirigir, y otro en el canje no sirve`() = testApplication {
        with(c) { montar() }
        val res = c.run { sinRedirigir() }.get(c.urlDeAutorizar(redirectUri = "https://atacante.example/callback"))
        assertEquals(HttpStatusCode.BadRequest, res.status)
        assertNull(res.headers[HttpHeaders.Location], "nunca un redirector abierto")
        // Un prefijo tampoco.
        assertEquals(HttpStatusCode.BadRequest, client.get(c.urlDeAutorizar(redirectUri = "$RETORNO_DE_CLAUDE/otro")).status)

        val codigo = with(c) { codigo() }
        val canje = with(c) { canjear(codigo, redirectUri = "https://claude.ai/otro") }
        assertEquals(HttpStatusCode.BadRequest, canje.status)
    }

    @Test
    fun `sin PKCE S256 vuelve al cliente con invalid_request`() = testApplication {
        with(c) { montar() }
        val res = c.run { sinRedirigir() }.get(c.urlDeAutorizar(metodo = "plain"))
        assertEquals(HttpStatusCode.Found, res.status)
        val p = c.parametrosDe(res.headers[HttpHeaders.Location]!!)
        assertEquals("invalid_request", p["error"])
        assertEquals("estado-123", p["state"])
    }

    @Test
    fun `la solicitud es de un solo uso y cancelar devuelve access_denied`() = testApplication {
        with(c) { montar() }
        val s = c.solicitudDe(client.get(c.urlDeAutorizar()).bodyAsText())
        val cancelar = with(c) { permitir(s, decision = "cancelar") }
        assertEquals(HttpStatusCode.Found, cancelar.status)
        assertEquals("access_denied", c.parametrosDe(cancelar.headers[HttpHeaders.Location]!!)["error"])
        assertEquals(HttpStatusCode.BadRequest, with(c) { permitir(s) }.status, "ya se usó")
    }

    @Test
    fun `con la contrasena mala no hay codigo y la pantalla lo dice`() = testApplication {
        with(c) { montar() }
        val s = c.solicitudDe(client.get(c.urlDeAutorizar()).bodyAsText())
        val res = with(c) { permitir(s, contrasena = "no-es") }
        assertEquals(HttpStatusCode.Unauthorized, res.status)
        assertTrue(PantallaDeConsentimiento.CREDENCIALES_MALAS in res.bodyAsText())
        assertNull(res.headers[HttpHeaders.Location])
        // La misma solicitud sigue sirviendo con la buena.
        assertEquals(HttpStatusCode.Found, with(c) { permitir(s) }.status)
    }

    @Test
    fun `un token vencido da 401`() = testApplication {
        with(c) { montar() }
        val (acceso, _) = with(c) { conectar() }
        transaction {
            TokensDelConector.update({ TokensDelConector.hash eq LasConcesiones.hash(acceso) }) { it[venceEn] = System.currentTimeMillis() - 1 }
        }
        assertEquals(HttpStatusCode.Unauthorized, with(c) { mcp(acceso, "ping") }.status)
    }

    @Test
    fun `un token de otra audiencia no entra`() = testApplication {
        with(c) { montar() }
        val (acceso, _) = with(c) { conectar() }
        transaction { TokensDelConector.update({ TokensDelConector.hash eq LasConcesiones.hash(acceso) }) { it[audiencia] = "https://otro.test/mcp" } }
        assertEquals(HttpStatusCode.Unauthorized, with(c) { mcp(acceso, "ping") }.status)
    }

    // ── CIMD y DCR ─────────────────────────────────────────────────────────────────────────────

    @Test
    fun `un CIMD que dice otro client_id, o un retorno fuera de su lista, se rechaza`() = testApplication {
        with(c) { montar() }
        val falso = "https://claude.ai.example/oauth/metadata"
        c.documentos[falso] = """{"client_id":"https://otro.example/x","redirect_uris":["https://claude.ai.example/cb"]}"""
        assertEquals(HttpStatusCode.BadRequest, client.get(c.urlDeAutorizar(clientId = falso, redirectUri = "https://claude.ai.example/cb")).status)
        val conSecreto = "https://cliente.example/con-secreto"
        c.documentos[conSecreto] = """{"client_id":"$conSecreto","redirect_uris":["https://cliente.example/cb"],"token_endpoint_auth_method":"client_secret_basic"}"""
        assertEquals(HttpStatusCode.BadRequest, client.get(c.urlDeAutorizar(clientId = conSecreto, redirectUri = "https://cliente.example/cb")).status)
        val raro = "https://cliente.example/raro"
        c.documentos[raro] = """{"client_id":"$raro","redirect_uris":["movi://cb"]}"""
        assertEquals(HttpStatusCode.BadRequest, client.get(c.urlDeAutorizar(clientId = raro, redirectUri = "movi://cb")).status)
        // Uno que no existe (el lector no lo encuentra).
        assertEquals(HttpStatusCode.BadRequest, client.get(c.urlDeAutorizar(clientId = "https://nadie.example/cimd")).status)
        // Un http no es CIMD.
        assertEquals(HttpStatusCode.BadRequest, client.get(c.urlDeAutorizar(clientId = "http://claude.ai/oauth/x")).status)
    }

    @Test
    fun `Claude Code vuelve a un loopback con cualquier puerto, y no a otro camino`() = testApplication {
        with(c) { montar() }
        val retorno = "http://localhost:53682/callback"
        val pantalla = client.get(c.urlDeAutorizar(clientId = CIMD_DE_CLAUDE_CODE, redirectUri = retorno))
        assertEquals(HttpStatusCode.OK, pantalla.status, pantalla.bodyAsText())
        assertTrue("Permitir que Claude Code lea tus datos de Movi" in pantalla.bodyAsText())
        val res = with(c) { permitir(c.solicitudDe(pantalla.bodyAsText())) }
        assertTrue(res.headers[HttpHeaders.Location]!!.startsWith("$retorno?code="))
        assertEquals(
            HttpStatusCode.BadRequest,
            client.get(c.urlDeAutorizar(clientId = CIMD_DE_CLAUDE_CODE, redirectUri = "http://localhost:53682/otro")).status,
        )
    }

    @Test
    fun `DCR registra un cliente publico y el flujo funciona con su client_id`() = testApplication {
        with(c) { montar() }
        val reg = client.post("/oauth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"client_name":"Claude","redirect_uris":["$RETORNO_DE_CLAUDE"],"token_endpoint_auth_method":"none","grant_types":["authorization_code","refresh_token"]}""")
        }
        assertEquals(HttpStatusCode.Created, reg.status, reg.bodyAsText())
        val r = c.json.parseToJsonElement(reg.bodyAsText()).jsonObject
        val clientId = r["client_id"]!!.jsonPrimitive.content
        assertEquals("none", r["token_endpoint_auth_method"]!!.jsonPrimitive.content)
        val codigo = with(c) { codigo(clientId = clientId) }
        assertEquals(HttpStatusCode.OK, with(c) { canjear(codigo, clientId = clientId) }.status)

        // Con un client_id que no es el del código, no.
        val otro = with(c) { codigo(clientId = clientId) }
        assertEquals(HttpStatusCode.BadRequest, with(c) { canjear(otro, clientId = CIMD_DE_CLAUDE) }.status)

        // Un retorno http que no es loopback, o un cliente con secreto, no se registra.
        val malo = client.post("/oauth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"redirect_uris":["http://atacante.example/cb"]}""")
        }
        assertEquals(HttpStatusCode.BadRequest, malo.status)
        val conSecreto = client.post("/oauth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"redirect_uris":["$RETORNO_DE_CLAUDE"],"token_endpoint_auth_method":"client_secret_post"}""")
        }
        assertEquals(HttpStatusCode.BadRequest, conSecreto.status)
    }

    // ── Aislamiento ────────────────────────────────────────────────────────────────────────────

    @Test
    fun `el JWT de la app no entra a mcp y el token del conector no entra a la app`() = testApplication {
        with(c) { montar { accountRoutes() } }
        val jwt = c.a.token(c.a.duenoA)
        assertEquals(HttpStatusCode.OK, client.get("/api/accounts") { header(HttpHeaders.Authorization, "Bearer $jwt") }.status)
        assertEquals(HttpStatusCode.Unauthorized, with(c) { mcp(jwt, "ping") }.status, "el JWT de la app no abre /mcp")
        val (acceso, refresco) = with(c) { conectar() }
        assertEquals(HttpStatusCode.Unauthorized, client.get("/api/accounts") { header(HttpHeaders.Authorization, "Bearer $acceso") }.status)
        assertEquals(HttpStatusCode.Unauthorized, client.get("/api/conector") { header(HttpHeaders.Authorization, "Bearer $acceso") }.status)
        // Un token de refresco tampoco abre /mcp.
        assertEquals(HttpStatusCode.Unauthorized, with(c) { mcp(refresco, "ping") }.status)
    }

    @Test
    fun `Desconectar desde la app corta al instante, y solo la conexion propia`() = testApplication {
        with(c) { montar() }
        val (acceso, refresco) = with(c) { conectar() }
        val (accesoDeB, _) = with(c) { conectar(c.correoDeB, c.contrasenaDeB) }
        val lista = c.json.decodeFromString(
            ConectorDeClaude.serializer(),
            client.get("/api/conector") { with(c.a) { como(c.a.duenoA) } }.bodyAsText(),
        )
        assertEquals(URL_DEL_MCP, lista.url)
        val conexion = lista.conexiones.single()
        assertEquals("Claude", conexion.cliente)
        assertEquals("claude.ai", conexion.host)

        // B no puede desconectar la de A.
        assertEquals(HttpStatusCode.NotFound, client.delete("/api/conector/conexiones/${conexion.id}") { with(c.a) { como(c.a.duenoB) } }.status)
        assertEquals(HttpStatusCode.OK, with(c) { mcp(acceso, "ping") }.status)

        assertEquals(HttpStatusCode.NoContent, client.delete("/api/conector/conexiones/${conexion.id}") { with(c.a) { como(c.a.duenoA) } }.status)
        assertEquals(HttpStatusCode.Unauthorized, with(c) { mcp(acceso, "ping") }.status)
        assertEquals(HttpStatusCode.BadRequest, with(c) { token(mapOf("grant_type" to "refresh_token", "refresh_token" to refresco, "client_id" to CIMD_DE_CLAUDE)) }.status)
        assertTrue(c.json.decodeFromString(ConectorDeClaude.serializer(), client.get("/api/conector") { with(c.a) { como(c.a.duenoA) } }.bodyAsText()).conexiones.isEmpty())
        // La de B sigue.
        assertEquals(HttpStatusCode.OK, with(c) { mcp(accesoDeB, "ping") }.status)
        transaction { assertNotNull(ConcesionesDelConector.selectAll().where { ConcesionesDelConector.userId eq c.a.duenoA }.single()[ConcesionesDelConector.revocadaEn]) }
    }

    @Test
    fun `cerrar sesion en todos los aparatos tambien desconecta a Claude`() = testApplication {
        with(c) { montar() }
        val (acceso, _) = with(c) { conectar() }
        transaction { Users.update({ Users.id eq c.a.duenoA }) { it[tokenVersion] = 1 } }
        assertEquals(HttpStatusCode.Unauthorized, with(c) { mcp(acceso, "ping") }.status)
    }
}
