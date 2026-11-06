package com.jvillada.movi.server.conector

import at.favre.lib.crypto.bcrypt.BCrypt
import com.jvillada.movi.server.ai.AndamioDelAsistente
import com.jvillada.movi.server.auth.RateLimiter
import com.jvillada.movi.server.db.TABLAS_DEL_CONECTOR
import com.jvillada.movi.server.db.Users
import com.jvillada.movi.server.routes.conectorDeLaAppRoutes
import com.jvillada.movi.server.routes.conectorMcpRoutes
import com.jvillada.movi.server.routes.conectorOAuthRoutes
import io.ktor.client.HttpClient
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.parameters
import io.ktor.server.routing.Route
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.net.URI
import java.net.URLDecoder
import java.security.MessageDigest
import java.util.Base64
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * # El andamio de las pruebas del conector (vuelta 135)
 *
 * El de Movi AI (H2, dos dueños A y B, el JWT de la app) más las tablas del conector, una contraseña de verdad para
 * cada dueño (BCrypt de costo 4, rápido) y un lector de CIMD que **no toca la red**: sirve el documento de Claude y
 * el de Claude Code desde la memoria. La base pública del conector es `https://movi.test`.
 */
internal class AndamioDelConector(nombreDeLaBase: String, tablasDeMas: Array<Table> = emptyArray()) {
    val a = AndamioDelAsistente(nombreDeLaBase, tablasDeMas = TABLAS_DEL_CONECTOR + tablasDeMas)
    val json = Json { ignoreUnknownKeys = true }

    val contrasenaDeA = "clave-de-a-123"
    val contrasenaDeB = "clave-de-b-456"
    val correoDeA get() = "${a.duenoA}@ola3.test"
    val correoDeB get() = "${a.duenoB}@ola3.test"

    /** Lo que el lector de mentira sirve, por URL. Una prueba puede cambiarlo. */
    val documentos = mutableMapOf(
        CIMD_DE_CLAUDE to """{"client_id":"$CIMD_DE_CLAUDE","client_name":"Claude","redirect_uris":["$RETORNO_DE_CLAUDE"],"token_endpoint_auth_method":"none","grant_types":["authorization_code","refresh_token"]}""",
        CIMD_DE_CLAUDE_CODE to """{"client_id":"$CIMD_DE_CLAUDE_CODE","client_name":"Claude Code","redirect_uris":["http://localhost/callback","http://127.0.0.1/callback"],"token_endpoint_auth_method":"none"}""",
    )

    init {
        ConfigDelConector.baseParaPruebas = BASE
        SolicitudFirmada.claveParaPruebas = "clave-de-prueba-de-las-solicitudes-del-conector".toByteArray()
        lectorDeCimd = LectorDeCimd { url -> documentos[url]?.let { LecturaDelCimd.Documento(it) } ?: LecturaDelCimd.NoExiste }
        olvidarLosCimdEnMemoria()
        RateLimiter.reset()
        limitadorDelConector.olvidar()
        avisosDelConector = AvisoConTope()
        transaction {
            listOf(a.duenoA to contrasenaDeA, a.duenoB to contrasenaDeB).forEach { (uid, clave) ->
                Users.update({ Users.id eq uid }) { it[passwordHash] = BCrypt.withDefaults().hashToString(4, clave.toCharArray()) }
            }
        }
    }

    fun cerrar() {
        ConfigDelConector.baseParaPruebas = null
        SolicitudFirmada.claveParaPruebas = null
        lectorDeCimd = LectorDeCimdPorRed
        cimdDePrueba = emptySet()
        olvidarLosCimdEnMemoria()
        RateLimiter.reset()
        limitadorDelConector.olvidar()
        avisosDelConector = AvisoConTope()
    }

    /** Las rutas del conector (públicas), las de la app que se pidan (con el JWT) y «Movi en Claude». */
    fun ApplicationTestBuilder.montar(deLaApp: Route.() -> Unit = {}) {
        with(a) {
            application {
                modulo { conectorDeLaAppRoutes(); deLaApp() }
                routing { conectorOAuthRoutes(); conectorMcpRoutes() }
            }
        }
    }

    fun ApplicationTestBuilder.sinRedirigir(): HttpClient = createClient { followRedirects = false }

    // ── PKCE ───────────────────────────────────────────────────────────────────────────────────

    val verificador = "verificador-de-prueba-con-mas-de-cuarenta-y-tres-caracteres-123"
    fun desafioDe(v: String): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256").digest(v.toByteArray()))

    // ── El flujo ───────────────────────────────────────────────────────────────────────────────

    fun urlDeAutorizar(
        clientId: String = CIMD_DE_CLAUDE,
        redirectUri: String = RETORNO_DE_CLAUDE,
        state: String? = "estado-123",
        desafio: String = desafioDe(verificador),
        metodo: String = "S256",
        extra: String = "",
    ): String = "/oauth/authorize?response_type=code&client_id=${enc(clientId)}&redirect_uri=${enc(redirectUri)}" +
        "&code_challenge=$desafio&code_challenge_method=$metodo&scope=movi%3Aleer" +
        (state?.let { "&state=${enc(it)}" } ?: "") + extra

    fun enc(s: String): String = java.net.URLEncoder.encode(s, Charsets.UTF_8)

    /** El identificador de un solo uso que trae la pantalla de consentimiento. */
    fun solicitudDe(html: String): String =
        assertNotNull(Regex("name=\"solicitud\" value=\"([^\"]+)\"").find(html)?.groupValues?.get(1), "la pantalla trae la solicitud:\n$html")

    suspend fun ApplicationTestBuilder.permitir(
        solicitud: String,
        correo: String = correoDeA,
        contrasena: String = contrasenaDeA,
        decision: String = "permitir",
    ): HttpResponse = sinRedirigir().submitForm(
        "/oauth/authorize",
        formParameters = parameters {
            append("solicitud", solicitud)
            append("correo", correo)
            append("contrasena", contrasena)
            append("decision", decision)
        },
    )

    fun parametrosDe(location: String): Map<String, String> =
        URI(location).rawQuery.orEmpty().split('&').filter { '=' in it }.associate {
            it.substringBefore('=') to URLDecoder.decode(it.substringAfter('='), Charsets.UTF_8)
        }

    /** Autoriza con el cliente de Claude y devuelve el código. */
    suspend fun ApplicationTestBuilder.codigo(correo: String = correoDeA, contrasena: String = contrasenaDeA, clientId: String = CIMD_DE_CLAUDE, redirectUri: String = RETORNO_DE_CLAUDE): String {
        val pantalla = client.get(urlDeAutorizar(clientId = clientId, redirectUri = redirectUri))
        assertEquals(HttpStatusCode.OK, pantalla.status, pantalla.bodyAsText())
        val res = permitir(solicitudDe(pantalla.bodyAsText()), correo, contrasena)
        assertEquals(HttpStatusCode.Found, res.status, res.bodyAsText())
        return assertNotNull(parametrosDe(res.headers[HttpHeaders.Location]!!)["code"])
    }

    suspend fun ApplicationTestBuilder.token(form: Map<String, String>): HttpResponse =
        client.submitForm("/oauth/token", formParameters = parameters { form.forEach { (k, v) -> append(k, v) } })

    suspend fun ApplicationTestBuilder.canjear(
        codigo: String,
        verificador: String = this@AndamioDelConector.verificador,
        clientId: String = CIMD_DE_CLAUDE,
        redirectUri: String = RETORNO_DE_CLAUDE,
    ): HttpResponse = token(
        mapOf("grant_type" to "authorization_code", "code" to codigo, "code_verifier" to verificador, "client_id" to clientId, "redirect_uri" to redirectUri),
    )

    fun tokensDe(cuerpo: String): Pair<String, String> {
        val o = json.parseToJsonElement(cuerpo).jsonObject
        return o["access_token"]!!.jsonPrimitive.content to o["refresh_token"]!!.jsonPrimitive.content
    }

    /** El flujo entero para [correo]: (acceso, refresco). */
    suspend fun ApplicationTestBuilder.conectar(correo: String = correoDeA, contrasena: String = contrasenaDeA): Pair<String, String> {
        val res = canjear(codigo(correo, contrasena))
        assertEquals(HttpStatusCode.OK, res.status, res.bodyAsText())
        return tokensDe(res.bodyAsText())
    }

    // ── MCP ────────────────────────────────────────────────────────────────────────────────────

    private var siguienteId = 1

    suspend fun ApplicationTestBuilder.mcp(token: String?, metodo: String, params: String = "{}"): HttpResponse =
        client.post("/mcp") {
            token?.let { header(HttpHeaders.Authorization, "Bearer $it") }
            contentType(ContentType.Application.Json)
            setBody("""{"jsonrpc":"2.0","id":${siguienteId++},"method":"$metodo","params":$params}""")
        }

    /** `tools/call` y su `structuredContent`; falla si el pedido no salió 200. */
    suspend fun ApplicationTestBuilder.herramienta(token: String, nombre: String, argumentos: String = "{}"): JsonObject {
        val res = mcp(token, "tools/call", """{"name":"$nombre","arguments":$argumentos}""")
        assertEquals(HttpStatusCode.OK, res.status, res.bodyAsText())
        val r = json.parseToJsonElement(res.bodyAsText()).jsonObject["result"]?.jsonObject
        assertNotNull(r, res.bodyAsText())
        assertTrue(r["isError"]?.jsonPrimitive?.content == "false", "la herramienta $nombre falló: ${res.bodyAsText()}")
        return r["structuredContent"]!!.jsonObject
    }

    companion object {
        const val BASE = "https://movi.test"
        const val URL_DEL_MCP = "$BASE/mcp"
        const val CIMD_DE_CLAUDE = "https://claude.ai/oauth/mcp-oauth-client-metadata"
        const val CIMD_DE_CLAUDE_CODE = "https://claude.ai/oauth/claude-code-client-metadata"
        const val RETORNO_DE_CLAUDE = "https://claude.ai/api/mcp/auth_callback"
    }
}
