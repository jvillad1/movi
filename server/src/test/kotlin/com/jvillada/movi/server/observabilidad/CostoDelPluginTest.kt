package com.jvillada.movi.server.observabilidad

import com.jvillada.movi.server.plugins.MetricasPorRuta
import io.ktor.client.request.get
import io.ktor.server.application.install
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * # Cuánto le cuesta el plugin a un pedido (vuelta 102)
 *
 * Dos medidas, que se imprimen en la salida de la prueba:
 * 1. **Anotar un pedido** (`Metricas.registrarPedido`, lo que corre en cada `ResponseSent`), solo:
 *    microsegundos por anotación. Es lo único que se le exige: menos de 50 µs en promedio, un techo
 *    holgado para que una máquina lenta del CI no la vuelva intermitente.
 * 2. **El pedido entero**, con y sin el plugin, en el motor de pruebas de Ktor: la diferencia es el
 *    costo de punta a punta (la envoltura de la corrutina y los tres ganchos). Solo se informa: el
 *    motor de pruebas tiene más ruido que lo que se mide.
 */
class CostoDelPluginTest {

    @BeforeTest fun limpiar() = Metricas.reiniciar()
    @AfterTest fun soltar() = Metricas.reiniciar()

    @Test
    fun `anotar un pedido cuesta microsegundos`() {
        val rutas = (0 until 40).map { "/api/ruta-$it/{id}" }
        repeat(20_000) { Metricas.registrarPedido("GET", rutas[it % rutas.size], 200, (it % 300).toLong(), usuario = "usr_${it % 3}") }
        val n = 200_000
        val t0 = System.nanoTime()
        for (i in 0 until n) Metricas.registrarPedido("GET", rutas[i % rutas.size], if (i % 50 == 0) 500 else 200, (i % 300).toLong(), usuario = "usr_${i % 3}")
        val nsPorPedido = (System.nanoTime() - t0) / n
        println("costo del plugin · anotar un pedido: $nsPorPedido ns")
        assertTrue(nsPorPedido < 50_000, "anotar un pedido tardó $nsPorPedido ns en promedio")
    }

    @Test
    fun `el pedido entero con y sin el plugin`() {
        fun medir(conPlugin: Boolean): Long {
            var nsPorPedido = 0L
            testApplication {
                application {
                    if (conPlugin) install(MetricasPorRuta)
                    routing { get("/api/events/{id}") { call.respondText("ok") } }
                }
                repeat(500) { client.get("/api/events/ev-$it") }
                val n = 3_000
                val t0 = System.nanoTime()
                repeat(n) { client.get("/api/events/ev-$it") }
                nsPorPedido = (System.nanoTime() - t0) / n
            }
            return nsPorPedido
        }
        val sin = medir(conPlugin = false)
        val con = medir(conPlugin = true)
        val sin2 = medir(conPlugin = false)
        val con2 = medir(conPlugin = true)
        println("costo del plugin · pedido entero: sin ${sin / 1_000}/${sin2 / 1_000} µs, con ${con / 1_000}/${con2 / 1_000} µs")
    }
}
