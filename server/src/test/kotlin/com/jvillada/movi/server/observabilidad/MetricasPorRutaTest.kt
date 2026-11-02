package com.jvillada.movi.server.observabilidad

import com.jvillada.movi.server.plugins.MetricasPorRuta
import com.jvillada.movi.server.plugins.SIN_RUTA
import com.jvillada.movi.server.plugins.configureStatusPages
import io.ktor.client.request.get
import io.ktor.client.request.request
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.application.install
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * # El plugin de métricas por ruta (vuelta 102)
 *
 * Anota la **plantilla** (nunca la URL con ids ni el query), cuenta los 4xx y los 5xx —incluido el
 * 400 que pone `StatusPages`— y los percentiles salen bien con duraciones conocidas.
 */
class MetricasPorRutaTest {

    @BeforeTest fun limpiar() = Metricas.reiniciar()
    @AfterTest fun soltar() = Metricas.reiniciar()

    private fun rutasAnotadas() = Metricas.cerrado(Long.MAX_VALUE).rutas

    @Test
    fun `anota la plantilla de la ruta, nunca la url con ids ni el query`() = testApplication {
        application {
            install(MetricasPorRuta)
            routing {
                route("/api/events") {
                    get("/{id}") { call.respondText("ok") }
                    get("/{id}/parecidos") { call.respondText("ok") }
                }
            }
        }
        client.get("/api/events/ev-123?correo=caro@movi.test")
        client.get("/api/events/ev-456")
        client.get("/api/events/ev-123/parecidos")

        val anotadas = rutasAnotadas()
        assertEquals(setOf("/api/events/{id}", "/api/events/{id}/parecidos"), anotadas.keys.map { it.ruta }.toSet())
        assertEquals(2L, anotadas.entries.single { it.key.ruta == "/api/events/{id}" }.value.total)
        assertTrue(anotadas.keys.all { it.metodo == "GET" })
        assertTrue(anotadas.keys.none { "ev-" in it.ruta || "caro" in it.ruta || "?" in it.ruta })
    }

    @Test
    fun `cuenta los 4xx y los 5xx, con el status final que puso StatusPages`() = testApplication {
        application {
            configureStatusPages()
            install(MetricasPorRuta)
            routing {
                get("/api/bien") { call.respondText("ok") }
                get("/api/no-esta") { call.respond(HttpStatusCode.NotFound, "no") }
                get("/api/roto") { call.respond(HttpStatusCode.InternalServerError, "roto") }
                // El que lanza: StatusPages lo vuelve un 400, y así tiene que contarse.
                get("/api/pedido-malo") { throw BadRequestException("cuerpo ilegible") }
            }
        }
        client.get("/api/bien")
        client.get("/api/no-esta")
        client.get("/api/roto")
        client.get("/api/roto")
        assertEquals(HttpStatusCode.BadRequest, client.get("/api/pedido-malo").status)
        // Y una ruta que no existe: se anota sin plantilla, como 404.
        client.get("/api/nadie/la/registro")

        val porRuta = rutasAnotadas().mapKeys { it.key.ruta }
        assertEquals(0L, porRuta.getValue("/api/bien").errores4xx + porRuta.getValue("/api/bien").errores5xx)
        assertEquals(1L, porRuta.getValue("/api/no-esta").errores4xx)
        assertEquals(2L, porRuta.getValue("/api/roto").errores5xx)
        assertEquals(1L, porRuta.getValue("/api/pedido-malo").errores4xx)
        assertEquals(0L, porRuta.getValue("/api/pedido-malo").errores5xx)
        assertEquals(1L, porRuta.getValue(SIN_RUTA).errores4xx)
    }

    @Test
    fun `el metodo va de una lista cerrada, el que invente el cliente es OTRO`() = testApplication {
        application {
            install(MetricasPorRuta)
            routing { get("/api/bien") { call.respondText("ok") } }
        }
        client.request("/api/bien") { method = HttpMethod("PROPFIND-con-basura") }
        client.request("/api/bien") { method = HttpMethod("patch") }
        client.get("/api/bien")
        assertEquals(setOf("OTRO", "PATCH", "GET"), rutasAnotadas().keys.map { it.metodo }.toSet())
        assertEquals("OTRO", metodoNormalizado("x".repeat(500)))
        assertEquals("OPTIONS", metodoNormalizado("options"))
    }

    @Test
    fun `los percentiles salen bien con duraciones conocidas`() {
        Metricas.reloj = { 1_000_000L * Metricas.UN_MINUTO }
        // 90 pedidos de 10 ms, 9 de 200 ms y uno de 1 s.
        repeat(90) { Metricas.registrarPedido("GET", "/api/x", 200, 10, usuario = null) }
        repeat(9) { Metricas.registrarPedido("GET", "/api/x", 200, 200, usuario = null) }
        Metricas.registrarPedido("GET", "/api/x", 200, 1_000, usuario = null)

        val a = rutasAnotadas().values.single()
        assertEquals(100L, a.total)
        assertEquals(10L, Cubetas.percentil(a.cubetas, 0.50, a.maximoMs))
        assertEquals(200L, Cubetas.percentil(a.cubetas, 0.95, a.maximoMs))
        assertEquals(200L, Cubetas.percentil(a.cubetas, 0.99, a.maximoMs))
        assertEquals(1_000L, Cubetas.percentil(a.cubetas, 1.0, a.maximoMs))
        assertEquals(1_000L, a.maximoMs)
    }

    @Test
    fun `un percentil nunca dice mas que el maximo, y vacio no dice nada`() {
        val c = LongArray(Cubetas.CUANTAS)
        assertNull(Cubetas.percentil(c, 0.95, 0))
        // 12 ms cae en la cubeta de 15, pero el más lento fue 12: el p95 dice 12.
        c[Cubetas.indiceDe(12)] = 1
        assertEquals(12L, Cubetas.percentil(c, 0.95, 12))
        // Más de 60 s: la última cubeta, con el máximo real.
        val d = LongArray(Cubetas.CUANTAS)
        d[Cubetas.indiceDe(90_000)] = 1
        assertEquals(Cubetas.CUANTAS - 1, Cubetas.indiceDe(90_000))
        assertEquals(90_000L, Cubetas.percentil(d, 0.5, 90_000))
        // Los bordes son inclusivos: 10 ms cae en la de 10.
        assertEquals(10L, Cubetas.BORDES[Cubetas.indiceDe(10)])
        assertEquals(15L, Cubetas.BORDES[Cubetas.indiceDe(11)])
    }

    @Test
    fun `los usuarios distintos se cuentan y no se guardan`() {
        Metricas.reloj = { 7 * Metricas.UN_MINUTO }
        Metricas.registrarPedido("GET", "/api/a", 200, 1, usuario = "usr_1")
        Metricas.registrarPedido("GET", "/api/b", 200, 1, usuario = "usr_1")
        Metricas.registrarPedido("GET", "/api/a", 200, 1, usuario = "usr_2")
        Metricas.registrarPedido("GET", "/health", 200, 1, usuario = null)
        val actividad = Metricas.cerrado(Long.MAX_VALUE).actividad.values.single()
        assertEquals(2, actividad.usuarios)
        assertTrue("usr_" !in actividad.toString())
    }
}
