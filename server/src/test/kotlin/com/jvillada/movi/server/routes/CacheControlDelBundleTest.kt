package com.jvillada.movi.server.routes

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.jvillada.movi.server.plugins.configureConditionalHeaders
import com.jvillada.movi.server.plugins.configureRouting
import com.jvillada.movi.server.plugins.configureSerialization
import io.ktor.client.request.get
import io.ktor.client.request.header
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
 * # El navegador pregunta antes de reusar el bundle
 *
 * `staticResources("/", "static")` se servía **sin ninguna instrucción de caché**: ni
 * `Cache-Control`, ni `Last-Modified`, ni `ETag` (lo segundo sorprende, pero está verificado: Ktor
 * calcula la versión del archivo y no la manda hasta que `ConditionalHeaders` está instalado, y no
 * lo estaba). Sin nada que decida, el navegador queda libre de aplicar su heurística (RFC 9111
 * §4.2.2) y reusar `composeApp.js` o el `.wasm` de su disco después de un despliegue. Los nombres
 * no llevan hash —se mantienen estables a propósito, el Dockerfile los copia por nombre— ni
 * parámetro de versión, así que nada rompe el empate. El peor de los diagnósticos: el dueño
 * mirando la app de antes mientras `/version` jura que el commit nuevo está arriba. `push-sw.js`
 * ya se niega a cachear por esto mismo.
 *
 * Estos tests fijan las dos mitades del arreglo: la cabecera está en TODO lo estático —index,
 * bundle y fuentes— y obliga a revalidar sin prohibir el guardado (`no-cache`, no `no-store`); y
 * esa revalidación es barata, porque ahora hay `Last-Modified` y la petición condicional
 * contesta 304 sin cuerpo.
 */
class CacheControlDelBundleTest {

    private val testSecret = "test-secret-for-cache-control-tests-min-32-chars"

    private fun Application.testModule() {
        configureSerialization()
        configureConditionalHeaders()
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

    /** El index, el bundle y una fuente: los tres archivos del fixture de `test/resources/static`. */
    private val estaticos = listOf(
        "/index.html",
        "/composeApp.js",
        "/composeResources/com.jvillada.movi.resources/font/space_grotesk_regular.ttf",
    )

    @Test
    fun `todo lo estatico se sirve con Cache-Control que obliga a revalidar`() = testApplication {
        wireApp()

        for (ruta in estaticos) {
            val res = client.get(ruta)
            assertEquals(HttpStatusCode.OK, res.status, "$ruta debería servirse")
            val cacheControl = res.headers[HttpHeaders.CacheControl]
            assertNotNull(cacheControl, "$ruta se sirve sin Cache-Control: el navegador puede quedarse con el build viejo")
            assertTrue(
                "no-cache" in cacheControl || "max-age=0" in cacheControl,
                "$ruta debe obligar a revalidar, y dice «$cacheControl»",
            )
        }
    }

    /**
     * La cabecera vale también para la SPA servida por `default("index.html")` en una ruta del
     * router del cliente — que es como el dueño entra a la app, no por `/index.html`.
     */
    @Test
    fun `la SPA en cualquier ruta tambien lleva la cabecera`() = testApplication {
        wireApp()

        for (ruta in listOf("/", "/movimientos", "/presupuestos/2026-09")) {
            val res = client.get(ruta)
            assertEquals(HttpStatusCode.OK, res.status)
            assertNotNull(res.headers[HttpHeaders.CacheControl], "$ruta se sirve sin Cache-Control")
        }
    }

    /**
     * `no-cache` no es `no-store`: guardar está bien, reusar sin preguntar no. Si esto se volviera
     * `no-store` el bundle entero viajaría en cada visita, que es el otro extremo del error.
     */
    @Test
    fun `revalidar y no prohibir el guardado`() = testApplication {
        wireApp()

        val cacheControl = client.get("/composeApp.js").headers[HttpHeaders.CacheControl]
        assertNotNull(cacheControl)
        assertTrue("no-store" !in cacheControl, "no-store tiraría el bundle en cada visita: «$cacheControl»")
    }

    /**
     * Y la revalidación tiene que seguir siendo barata: con la fecha que el propio server dio,
     * la segunda petición contesta 304 sin cuerpo. Sin esto, «obligar a revalidar» podría
     * significar «mandar el bundle entero cada vez».
     */
    @Test
    fun `la revalidacion condicional sigue contestando 304`() = testApplication {
        wireApp()

        val primera = client.get("/composeApp.js")
        val lastModified = primera.headers[HttpHeaders.LastModified]
        assertNotNull(lastModified, "sin Last-Modified no hay revalidación barata posible")

        val segunda = client.get("/composeApp.js") { header(HttpHeaders.IfModifiedSince, lastModified) }
        assertEquals(HttpStatusCode.NotModified, segunda.status, "la revalidación debería ahorrar el cuerpo")
    }
}
