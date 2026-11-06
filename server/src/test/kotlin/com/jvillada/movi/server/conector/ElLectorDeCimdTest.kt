package com.jvillada.movi.server.conector

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.net.ServerSocket
import java.net.URI
import java.net.http.HttpClient
import java.time.Duration
import kotlin.concurrent.thread
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * # Vuelta 135 (C1 de la revisión) · Un documento que gotea no cuelga nada
 *
 * Un server local que manda las cabeceras y después un byte por segundo. Con el lector viejo
 * (`send` + `ofInputStream` + `readNBytes`), `HttpRequest.timeout` solo cubría las cabeceras y la lectura se
 * quedaba bloqueada en un hilo de `Dispatchers.IO` —el de la base— hasta que el otro lado cerraba. Ahora
 * [bajarConTope] corta la operación entera al tope, y setenta lecturas así no le quitan ni un hilo a la base.
 */
class ElLectorDeCimdTest {

    private lateinit var servidor: ServerSocket
    @Volatile private var abierto = true

    @BeforeTest
    fun arrancar() {
        servidor = ServerSocket(0)
        thread(isDaemon = true) {
            while (abierto) {
                val s = runCatching { servidor.accept() }.getOrNull() ?: break
                thread(isDaemon = true) {
                    runCatching {
                        s.use {
                            val entrada = it.getInputStream().bufferedReader()
                            var linea = entrada.readLine()
                            val camino = linea?.split(' ')?.getOrNull(1).orEmpty()
                            while (!linea.isNullOrEmpty()) linea = entrada.readLine()
                            val salida = it.getOutputStream()
                            if (camino == "/grande") {
                                val cuerpo = ByteArray(MAX_BYTES_DEL_CIMD + 10) { 'a'.code.toByte() }
                                salida.write("HTTP/1.1 200 OK\r\nContent-Length: ${cuerpo.size}\r\n\r\n".toByteArray())
                                salida.write(cuerpo)
                                salida.flush()
                            } else {
                                salida.write("HTTP/1.1 200 OK\r\nContent-Length: 100000\r\n\r\n".toByteArray())
                                salida.flush()
                                repeat(60) { _ -> salida.write('x'.code); salida.flush(); Thread.sleep(1_000) }
                            }
                        }
                    }
                }
            }
        }
    }

    @AfterTest
    fun parar() {
        abierto = false
        servidor.close()
    }

    private val cliente = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build()
    private fun url(camino: String) = URI("http://127.0.0.1:${servidor.localPort}$camino")

    @Test
    fun `un cuerpo que gotea se corta al tope`() = runBlocking {
        val antes = System.currentTimeMillis()
        assertNull(bajarConTope(cliente, url("/gotea"), topeMs = 1_000, maxBytes = MAX_BYTES_DEL_CIMD))
        val tardo = System.currentTimeMillis() - antes
        assertTrue(tardo < 2_500, "tardó $tardo ms (el tope era 1 s)")
    }

    @Test
    fun `un cuerpo de mas de 16 KB no se acepta`() = runBlocking {
        assertNull(bajarConTope(cliente, url("/grande"), topeMs = 3_000, maxBytes = MAX_BYTES_DEL_CIMD))
    }

    @Test
    fun `setenta lecturas lentas no le quitan hilos a la base`() = runBlocking {
        coroutineScope {
            val lentas = (1..70).map { async { bajarConTope(cliente, url("/gotea/$it"), topeMs = 2_500, maxBytes = MAX_BYTES_DEL_CIMD) } }
            // Mientras las setenta esperan, un trabajo en Dispatchers.IO (de donde vive `dbQuery`) entra enseguida.
            Thread.sleep(300)
            val antes = System.currentTimeMillis()
            val listo = withContext(Dispatchers.IO) { "listo" }
            assertEquals("listo", listo)
            assertTrue(System.currentTimeMillis() - antes < 500, "Dispatchers.IO esperó")
            assertTrue(lentas.awaitAll().all { it == null })
        }
    }
}
