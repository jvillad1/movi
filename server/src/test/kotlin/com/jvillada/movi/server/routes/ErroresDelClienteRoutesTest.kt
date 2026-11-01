package com.jvillada.movi.server.routes

import com.jvillada.movi.server.auth.RateLimiter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Vuelta 96 · el «Algo falló» de la web deja su error en el log del server. Ver
 * [erroresDelClienteRoutes].
 */
class ErroresDelClienteRoutesTest {

    @BeforeTest fun setUp() = RateLimiter.reset()
    @AfterTest fun tearDown() = RateLimiter.reset()

    private val cuerpo = """{"mensaje":"TypeError: null is not an object","navegador":"Mozilla/5.0 (iPhone)","usuario":"usr_1"}"""

    @Test
    fun `un reporte sin sesion se acepta`() = testApplication {
        application { routing { erroresDelClienteRoutes() } }
        // Sin Content-Type JSON a propósito: `sendBeacon` puede mandarlo como quiera.
        val res = client.post("/api/errores-del-cliente") { setBody(cuerpo) }
        assertEquals(HttpStatusCode.NoContent, res.status)
    }

    @Test
    fun `un cuerpo que no es el reporte es un 400`() = testApplication {
        application { routing { erroresDelClienteRoutes() } }
        val res = client.post("/api/errores-del-cliente") { contentType(ContentType.Application.Json); setBody("no es json") }
        assertEquals(HttpStatusCode.BadRequest, res.status)
    }

    @Test
    fun `el limitador frena una rafaga`() = testApplication {
        application { routing { erroresDelClienteRoutes() } }
        val estados = (1..25).map { client.post("/api/errores-del-cliente") { setBody(cuerpo) }.status }
        assertEquals(20, estados.count { it == HttpStatusCode.NoContent })
        assertEquals(HttpStatusCode.TooManyRequests, estados.last())
    }

    @Test
    fun `la linea del log es un solo renglon y con tope`() {
        val linea = lineaDelErrorDelCliente(
            ErrorDelCliente(mensaje = "uno\ndos\r\nINFO falso" + "x".repeat(5_000), navegador = "Safari\tiPhone", usuario = null),
        )
        assertFalse('\n' in linea || '\r' in linea || '\t' in linea, linea.take(200))
        assertTrue("usuario=-" in linea)
        assertTrue("uno dos INFO falso" in linea)
        assertTrue(linea.length < MAX_MENSAJE + 400, "la línea no tiene tope: ${linea.length}")
    }
}
