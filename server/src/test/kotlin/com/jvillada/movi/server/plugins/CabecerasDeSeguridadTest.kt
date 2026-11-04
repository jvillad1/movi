package com.jvillada.movi.server.plugins

import io.ktor.client.request.get
import io.ktor.server.response.header
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals

class CabecerasDeSeguridadTest {

    @Test
    fun `toda respuesta lleva las cabeceras de seguridad`() = testApplication {
        application {
            configureCabecerasDeSeguridad()
            routing { get("/a") { call.respondText("x") } }
        }
        val r = client.get("/a")
        for ((nombre, valor) in CABECERAS_DE_SEGURIDAD) {
            assertEquals(listOf(valor), r.headers.getAll(nombre), nombre)
        }
    }

    @Test
    fun `lo que una ruta ya dijo gana y no se duplica`() = testApplication {
        application {
            configureCabecerasDeSeguridad()
            routing {
                get("/b") {
                    call.response.header("Content-Security-Policy", "default-src 'none'")
                    call.respondText("y")
                }
            }
        }
        val r = client.get("/b")
        assertEquals(listOf("default-src 'none'"), r.headers.getAll("Content-Security-Policy"))
        assertEquals(listOf("DENY"), r.headers.getAll("X-Frame-Options"))
    }

    @Test
    fun `un 404 tambien las lleva`() = testApplication {
        application { configureCabecerasDeSeguridad(); routing { } }
        assertEquals("nosniff", client.get("/no-existe").headers["X-Content-Type-Options"])
    }
}
