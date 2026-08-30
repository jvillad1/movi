package com.jvillada.movi.server.routes

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.jvillada.movi.server.plugins.configureRouting
import com.jvillada.movi.server.plugins.configureSerialization
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.application.Application
import io.ktor.server.auth.authentication
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * El `staticResources("/", "static") { default("index.html") }` que sirve la PWA es un catch-all:
 * hasta este test, CUALQUIER ruta no registrada —`/api/` incluidas— devolvía 200 con el HTML de la
 * app. Verificado contra producción el 2026-08-30: `GET /api/documents` (ruta inexistente) → 200.
 *
 * El daño no es el código de estado sino el diagnóstico: un cliente nuevo contra un server viejo
 * (o al revés) recibe `<!doctype html>…` con 200 y Ktor client explota con un error de parseo que
 * no dice «esa ruta no existe». Ya costó horas una vez, con el campo `usedCategories`.
 *
 * Estos tests fijan las dos mitades del contrato: `/api/…` inexistente → 404 JSON; cualquier otra
 * ruta → el index.html de la SPA, que es lo que necesitan `/cuentas`, `/movimientos`, etc.
 */
class ApiNotFoundTest {

    private val testSecret = "test-secret-for-api-not-found-tests-min-32-chars"
    private val issuer   = "movi"
    private val audience = "movi-client"

    private fun Application.testModule() {
        configureSerialization()

        val algorithm = Algorithm.HMAC256(testSecret)
        val verifier  = JWT.require(algorithm)
            .withIssuer(issuer)
            .withAudience(audience)
            .build()

        authentication {
            jwt("jwt") {
                this.verifier(verifier)
                validate { credential ->
                    if (credential.payload.getClaim("userId").asString() != null)
                        JWTPrincipal(credential.payload)
                    else null
                }
            }
        }

        configureRouting()
    }

    private fun ApplicationTestBuilder.wireApp() {
        application { testModule() }
    }

    // ── /api/** inexistente → 404 JSON ────────────────────────────────────────

    @Test
    fun `una ruta api inexistente da 404 con cuerpo JSON, no el HTML de la SPA`() = testApplication {
        wireApp()

        val res = client.get("/api/no-existe")

        assertEquals(HttpStatusCode.NotFound, res.status)
        assertEquals(
            ContentType.Application.Json.contentType,
            res.contentType()?.contentType,
            "el cuerpo tiene que ser JSON parseable, no HTML",
        )
        val body = res.bodyAsText()
        assertTrue("<!doctype html" !in body.lowercase(), "no debe devolver el index de la SPA: $body")
        val obj = Json.parseToJsonElement(body).jsonObject
        assertEquals("not_found", obj["error"]!!.jsonPrimitive.content)
        assertEquals("/api/no-existe", obj["path"]!!.jsonPrimitive.content)
    }

    @Test
    fun `el 404 de api tambien aplica a rutas anidadas y a otros metodos`() = testApplication {
        wireApp()

        val anidada = client.get("/api/documents/42/lineas")
        assertEquals(HttpStatusCode.NotFound, anidada.status)
        assertEquals("/api/documents/42/lineas", Json.parseToJsonElement(anidada.bodyAsText()).jsonObject["path"]!!.jsonPrimitive.content)

        val post = client.post("/api/documents")
        assertEquals(HttpStatusCode.NotFound, post.status)
        assertEquals(ContentType.Application.Json.contentType, post.contentType()?.contentType)
    }

    // ── las rutas reales siguen intactas ──────────────────────────────────────

    @Test
    fun `el comodin no se come las rutas publicas ni el health`() = testApplication {
        wireApp()

        assertEquals(HttpStatusCode.OK, client.get("/health").status)

        // Pública, sin token: la sirve smsFilterConfigRoutes, no el comodín.
        val filtro = client.get("/api/sms/filter-config")
        assertEquals(HttpStatusCode.OK, filtro.status)
        assertTrue("senderCodes" in filtro.bodyAsText())
    }

    @Test
    fun `una ruta autenticada sin token sigue dando 401, no 404`() = testApplication {
        wireApp()

        // Si el comodín ganara la resolución, esto sería 404 y perderíamos la señal de «te falta token».
        assertEquals(HttpStatusCode.Unauthorized, client.get("/api/accounts").status)
    }

    // ── la SPA se sigue sirviendo ─────────────────────────────────────────────

    @Test
    fun `una ruta cualquiera de la SPA sigue devolviendo el index`() = testApplication {
        wireApp()

        for (ruta in listOf("/cuentas", "/movimientos", "/presupuestos/2026-08", "/")) {
            val res = client.get(ruta)
            assertEquals(HttpStatusCode.OK, res.status, "la SPA debe responder en $ruta")
            assertTrue(
                "movi-spa-fixture" in res.bodyAsText(),
                "$ruta debería servir el index.html de la SPA",
            )
        }
    }
}
