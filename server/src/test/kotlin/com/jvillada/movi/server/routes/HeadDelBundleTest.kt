package com.jvillada.movi.server.routes

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.jvillada.movi.server.plugins.configureAutoHead
import com.jvillada.movi.server.plugins.configureConditionalHeaders
import com.jvillada.movi.server.plugins.configureRouting
import com.jvillada.movi.server.plugins.configureSerialization
import io.ktor.client.request.get
import io.ktor.client.request.head
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.auth.authentication
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * # Un HEAD sobre un archivo que existe no puede contestar 404
 *
 * Medido contra producción: `curl -I /composeApp.js` devolvía **404** mientras el `GET` del mismo
 * archivo contestaba 200 sano, con su `Cache-Control: no-cache` y su `Last-Modified`. Ktor no
 * atiende `HEAD` por su cuenta —una ruta `get` responde solo a GET— así que el pedido caía hasta el
 * final del router y salía por el 404.
 *
 * No es un detalle de protocolo: HEAD es lo que usan los proxys, los verificadores de enlaces y
 * algunos precargadores para preguntar «¿esto existe, y cambió?» sin bajarse los megas del bundle.
 * Contestarles 404 es mentirles sobre un archivo que está ahí.
 *
 * Lo arregla `configureAutoHead()`. Estos tests fijan las tres cosas que tiene que cumplir la
 * respuesta: **200**, **las mismas cabeceras que el GET** (tipo, largo, caché y fecha — si no, el
 * HEAD no serviría para nada de lo que se usa) y **el cuerpo vacío**, que es lo único que lo
 * distingue de un GET.
 */
class HeadDelBundleTest {

    private val testSecret = "test-secret-for-head-response-tests-min-32-chars"

    private fun Application.testModule() {
        configureSerialization()
        configureConditionalHeaders()
        configureAutoHead()
        val algorithm = Algorithm.HMAC256(testSecret)
        val verifier = JWT.require(algorithm).withIssuer("movi").withAudience("movi-client").build()
        authentication {
            jwt("jwt") {
                this.verifier(verifier)
                validate { credential ->
                    if (credential.payload.getClaim("userId").asString() != null) JWTPrincipal(credential.payload) else null
                }
            }
        }
        configureRouting()
    }

    private fun ApplicationTestBuilder.wireApp() {
        application { testModule() }
    }

    /** El index y el bundle: los dos archivos que alguien pide con HEAD en la vida real. */
    private val estaticos = listOf("/index.html", "/composeApp.js")

    @Test
    fun `HEAD sobre el index y sobre el bundle contesta 200`() = testApplication {
        wireApp()

        for (ruta in estaticos) {
            assertEquals(
                HttpStatusCode.OK,
                client.head(ruta).status,
                "$ruta existe: contestarle 404 a un HEAD es mentir sobre un archivo que está ahí",
            )
        }
    }

    @Test
    fun `y con las mismas cabeceras que el GET`() = testApplication {
        wireApp()

        for (ruta in estaticos) {
            val get = client.get(ruta)
            val head = client.head(ruta)

            for (cabecera in listOf(
                HttpHeaders.ContentType,
                HttpHeaders.ContentLength,
                HttpHeaders.CacheControl,
                HttpHeaders.LastModified,
            )) {
                assertNotNull(get.headers[cabecera], "$ruta: el GET tendría que traer $cabecera")
                assertEquals(
                    get.headers[cabecera],
                    head.headers[cabecera],
                    "$ruta: el HEAD tiene que decir lo mismo que el GET en $cabecera",
                )
            }
        }
    }

    /** Lo único que lo distingue de un GET: el cuerpo no viaja. */
    @Test
    fun `y sin cuerpo`() = testApplication {
        wireApp()

        for (ruta in estaticos) {
            assertEquals("", client.head(ruta).bodyAsText(), "$ruta: un HEAD no manda cuerpo")
        }
    }

    /**
     * La SPA en una ruta del router del cliente —que es como el dueño entra a la app— también
     * responde, porque `default("index.html")` la resuelve igual para HEAD.
     */
    @Test
    fun `la SPA en cualquier ruta tambien contesta al HEAD`() = testApplication {
        wireApp()

        for (ruta in listOf("/", "/movimientos")) {
            assertEquals(HttpStatusCode.OK, client.head(ruta).status, ruta)
        }
    }

    /**
     * La revalidación condicional sigue siendo barata también por esta puerta: con la fecha que el
     * propio server dio, el HEAD contesta 304. Es justamente para lo que un proxy usa el HEAD.
     */
    @Test
    fun `el HEAD condicional contesta 304`() = testApplication {
        wireApp()

        val lastModified = client.get("/composeApp.js").headers[HttpHeaders.LastModified]
        assertNotNull(lastModified)

        val condicional = client.head("/composeApp.js") { header(HttpHeaders.IfModifiedSince, lastModified) }
        assertEquals(HttpStatusCode.NotModified, condicional.status)
    }

    /**
     * **Lo que no se puede haber roto por el camino.** `AutoHeadResponse` está instalado a nivel
     * aplicación, así que vale para todo el router: hay que ver que la API siga contestando lo
     * mismo que antes.
     *
     * - Una ruta `/api/…` que no existe sigue cayendo en el 404 JSON de `apiNotFound` (ese `handle`
     *   atiende cualquier método, HEAD incluido).
     * - Una ruta autenticada sin token sigue contestando 401, no 200 mudo.
     * - El contenido de un documento sigue exigiendo su token de descarga.
     */
    @Test
    fun `la API contesta al HEAD lo mismo que contestaba al GET`() = testApplication {
        wireApp()

        assertEquals(HttpStatusCode.NotFound, client.head("/api/no-existe").status)
        assertEquals(HttpStatusCode.Unauthorized, client.head("/api/accounts").status)
        assertEquals(HttpStatusCode.Unauthorized, client.head("/api/documents/doc-1/content").status)
        // Y el GET de esas mismas rutas no cambió.
        assertEquals(HttpStatusCode.NotFound, client.get("/api/no-existe").status)
        assertEquals(HttpStatusCode.Unauthorized, client.get("/api/accounts").status)
    }

    /** `/health` es lo que mira el proveedor: un HEAD ahí también tiene que decir que está vivo. */
    @Test
    fun `health contesta al HEAD`() = testApplication {
        wireApp()

        val res = client.head("/health")
        assertEquals(HttpStatusCode.OK, res.status)
        assertTrue(res.bodyAsText().isEmpty())
    }
}
