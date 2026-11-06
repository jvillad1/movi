package com.jvillada.movi.server.conector

import com.jvillada.movi.server.conector.AndamioDelConector.Companion.CIMD_DE_CLAUDE
import com.jvillada.movi.server.conector.AndamioDelConector.Companion.RETORNO_DE_CLAUDE
import com.jvillada.movi.server.routes.LIMITE_DE_TOKEN_POR_CLIENTE
import com.jvillada.movi.server.routes.LIMITE_DE_TOKEN_POR_IP
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.header
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
        val antes = limitadorDelConector.llavesQueEmpiezanCon("token:cliente:")
        repeat(LIMITE_DE_TOKEN_POR_CLIENTE + 1) { i ->
            val res = canjeDesde("10.${i / 65_536}.${(i / 256) % 256}.${i % 256}", CIMD_DE_CLAUDE, "mvc_c_" + "a".repeat(42) + (i % 10))
            assertEquals(HttpStatusCode.BadRequest, res.status, "pedido $i")
        }
        assertEquals(antes, limitadorDelConector.llavesQueEmpiezanCon("token:cliente:"), "lo basura no creó baldes de cliente")
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
        assertEquals(0, limitadorDelConector.llavesQueEmpiezanCon("token:cliente:"))
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
}
