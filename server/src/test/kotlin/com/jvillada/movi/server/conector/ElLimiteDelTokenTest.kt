package com.jvillada.movi.server.conector

import com.jvillada.movi.server.conector.AndamioDelConector.Companion.CIMD_DE_CLAUDE
import com.jvillada.movi.server.conector.AndamioDelConector.Companion.RETORNO_DE_CLAUDE
import com.jvillada.movi.server.routes.LIMITE_DE_TOKEN_POR_IP
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.http.parameters
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * # Vuelta 135 (N1 de la re-revisión) · Nadie llena el balde de Claude ni la memoria
 *
 * Sin el arreglo, el único freno de `/oauth/token` era un balde por `client_id` que elegía el atacante: mil pedidos
 * basura con el `client_id` de Claude lo llenaban para todos los usuarios, y `client_id` inventados creaban una
 * clave nueva por pedido en el limitador de la app (una hora cada una, sin tope).
 */
class ElLimiteDelTokenTest {

    private lateinit var c: AndamioDelConector

    @BeforeTest
    fun setUp() {
        c = AndamioDelConector("conector_limite_token")
    }

    @AfterTest
    fun tearDown() = c.cerrar()

    private suspend fun ApplicationTestBuilder.canjeDesde(ip: String, clientId: String, codigo: String) =
        client.submitForm(
            "/oauth/token",
            formParameters = parameters {
                append("grant_type", "authorization_code"); append("code", codigo); append("code_verifier", c.verificador)
                append("client_id", clientId); append("redirect_uri", RETORNO_DE_CLAUDE)
            },
        ) { header("X-Forwarded-For", ip) }

    @Test
    fun `mil canjes basura con el client_id de Claude no le cierran la puerta a nadie`() = testApplication {
        with(c) { montar() }
        val (_, refresco) = with(c) { conectar(c.correoDeB, c.contrasenaDeB) }
        val antes = limitadorDelConector.llavesQueEmpiezanCon("token:concesion:")
        repeat(1_001) { i ->
            val res = canjeDesde("10.${i / 65_536}.${(i / 256) % 256}.${i % 256}", CIMD_DE_CLAUDE, "mvc_c_" + "a".repeat(42) + (i % 10))
            assertEquals(HttpStatusCode.BadRequest, res.status, "pedido $i")
        }
        assertEquals(antes, limitadorDelConector.llavesQueEmpiezanCon("token:concesion:"), "lo basura no creó baldes")
        val res = with(c) { token(mapOf("grant_type" to "refresh_token", "refresh_token" to refresco, "client_id" to CIMD_DE_CLAUDE)) }
        assertEquals(HttpStatusCode.OK, res.status, res.bodyAsText())
    }

    @Test
    fun `un client_id inventado se rechaza sin tocar ningun balde de cliente`() = testApplication {
        with(c) { montar() }
        repeat(500) { i ->
            val res = canjeDesde("192.0.2.${i % 200}", "https://claude.ai/oauth/inventado-$i", "mvc_c_" + "b".repeat(43))
            assertEquals(HttpStatusCode.Unauthorized, res.status)
            assertEquals("invalid_client", c.json.parseToJsonElement(res.bodyAsText()).jsonObject["error"]!!.jsonPrimitive.content)
        }
        assertEquals(0, limitadorDelConector.llavesQueEmpiezanCon("token:concesion:"))
        assertTrue(limitadorDelConector.llaves() <= 200, "solo los baldes por IP: ${limitadorDelConector.llaves()}")
    }

    @Test
    fun `una sola IP tiene su tope, y un 64 de IPv6 cuenta como una`() = testApplication {
        with(c) { montar() }
        repeat(LIMITE_DE_TOKEN_POR_IP) { i ->
            val res = canjeDesde("2001:db8:aa:bb:${i.toString(16)}::1", CIMD_DE_CLAUDE, "mvc_c_" + "c".repeat(43))
            assertEquals(HttpStatusCode.BadRequest, res.status)
        }
        assertEquals(HttpStatusCode.TooManyRequests, canjeDesde("2001:db8:aa:bb:ffff::9", CIMD_DE_CLAUDE, "mvc_c_" + "c".repeat(43)).status)
        assertEquals(HttpStatusCode.BadRequest, canjeDesde("2001:db8:aa:cc::1", CIMD_DE_CLAUDE, "mvc_c_" + "c".repeat(43)).status, "otro /64")
    }

    @Test
    fun `un refresco real ya rotado, repetido desde muchas IP, no le cierra la puerta a otra conexion del mismo cliente`() = testApplication {
        with(c) { montar() }
        // El atacante tiene su propia cuenta (A): conecta, refresca una vez, y se queda con el refresco ya rotado.
        val (_, r0) = with(c) { conectar() }
        val rotado = with(c) { token(mapOf("grant_type" to "refresh_token", "refresh_token" to r0, "client_id" to CIMD_DE_CLAUDE)) }
        assertEquals(HttpStatusCode.OK, rotado.status)
        // La víctima (B), con el mismo client_id de Claude.
        val (_, deB) = with(c) { conectar(c.correoDeB, c.contrasenaDeB) }
        // 1.100 pedidos con R0 desde 10 IP (110 por IP, bajo su tope de 120).
        repeat(1_100) { i ->
            client.submitForm(
                "/oauth/token",
                formParameters = parameters { append("grant_type", "refresh_token"); append("refresh_token", r0); append("client_id", CIMD_DE_CLAUDE) },
            ) { header("X-Forwarded-For", "198.51.100.${i % 10}") }
        }
        val res = with(c) { token(mapOf("grant_type" to "refresh_token", "refresh_token" to deB, "client_id" to CIMD_DE_CLAUDE)) }
        assertEquals(HttpStatusCode.OK, res.status, res.bodyAsText())
        assertEquals(0, limitadorDelConector.llavesQueEmpiezanCon("token:cliente:"), "no hay balde por cliente")
    }

    @Test
    fun `una IP de Anthropic tiene un tope mas alto en token y en register`() = testApplication {
        with(c) { montar() }
        repeat(LIMITE_DE_TOKEN_DE_ANTHROPIC) { i ->
            val res = canjeDesde("160.79.104.20", CIMD_DE_CLAUDE, "mvc_c_" + "d".repeat(43))
            assertEquals(HttpStatusCode.BadRequest, res.status, "pedido $i desde Anthropic")
        }
        assertEquals(HttpStatusCode.TooManyRequests, canjeDesde("160.79.104.20", CIMD_DE_CLAUDE, "mvc_c_" + "d".repeat(43)).status)
        // El registro DCR: 30 por minuto a una IP cualquiera, 300 a una de Anthropic.
        suspend fun registrar(ip: String) = client.post("/oauth/register") {
            header("X-Forwarded-For", ip)
            contentType(ContentType.Application.Json)
            setBody("""{"redirect_uris":["$RETORNO_DE_CLAUDE"]}""")
        }
        repeat(40) { assertEquals(HttpStatusCode.Created, registrar("160.79.105.1").status) }
        repeat(30) { registrar("203.0.113.9") }
        assertEquals(HttpStatusCode.TooManyRequests, registrar("203.0.113.9").status)
    }

    @Test
    fun `mcp corta por IP a quien junta 401, sin tocar el trafico bueno`() = testApplication {
        with(c) { montar() }
        val (acceso, _) = with(c) { conectar() }
        suspend fun mcpDesde(ip: String, token: String?) = client.post("/mcp") {
            header("X-Forwarded-For", ip)
            token?.let { header(io.ktor.http.HttpHeaders.Authorization, "Bearer $it") }
            contentType(ContentType.Application.Json)
            setBody("""{"jsonrpc":"2.0","id":1,"method":"ping"}""")
        }
        // Lo bueno no cuenta: 50 pings válidos desde la misma IP.
        repeat(50) { assertEquals(HttpStatusCode.OK, mcpDesde("203.0.113.50", acceso).status) }
        repeat(com.jvillada.movi.server.routes.MAX_401_POR_MINUTO_POR_IP) {
            assertEquals(HttpStatusCode.Unauthorized, mcpDesde("203.0.113.50", "mvc_a_" + "z".repeat(43)).status)
        }
        // Pasado el tope de 401, esa IP recibe 429 sin ir a la base; otra IP sigue normal.
        assertEquals(HttpStatusCode.TooManyRequests, mcpDesde("203.0.113.50", "mvc_a_" + "z".repeat(43)).status)
        assertEquals(HttpStatusCode.OK, mcpDesde("203.0.113.51", acceso).status)
    }
}
