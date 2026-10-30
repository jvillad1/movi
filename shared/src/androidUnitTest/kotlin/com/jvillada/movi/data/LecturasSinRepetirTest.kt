package com.jvillada.movi.data

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Test
import java.io.IOException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * # El motor de [LecturasSinRepetir]: unir lo que viaja, recordar lo fresco (vuelta 78)
 *
 * Con su reloj, su generación y su usuario a mano: ninguna prueba depende del reloj de la máquina.
 */
class LecturasSinRepetirTest {

    private var ahora = 1_000_000L
    private var generacion = 0
    private var usuario: String? = "u1"
    private val lecturas = LecturasCompartidas(reloj = { ahora }, generacion = { generacion }, usuario = { usuario })

    private var pedidos = 0
    private suspend fun cuentas(): List<String> {
        pedidos++
        return listOf("Nu", "Bancolombia")
    }

    @Test
    fun `dos pedidos iguales a la vez salen una sola vez`() = runBlocking {
        val puerta = CompletableDeferred<Unit>()
        val resultados = coroutineScope {
            val a = async { lecturas.leer("cuentas") { puerta.await(); cuentas() } }
            val b = async { lecturas.leer("cuentas") { puerta.await(); cuentas() } }
            val c = async { lecturas.leer("cuentas") { puerta.await(); cuentas() } }
            yield()
            puerta.complete(Unit)
            listOf(a, b, c).awaitAll()
        }
        assertEquals(1, pedidos)
        assertEquals(3, resultados.count { it == listOf("Nu", "Bancolombia") })
    }

    @Test
    fun `lo fresco no vuelve a salir, lo de mas de 30 s si`() = runBlocking {
        lecturas.leer("cuentas") { cuentas() }
        ahora += 29_000
        lecturas.leer("cuentas") { cuentas() }
        assertEquals(1, pedidos, "a los 29 s es la misma")
        ahora += 2_000
        lecturas.leer("cuentas") { cuentas() }
        assertEquals(2, pedidos, "a los 31 s se pide otra vez")
    }

    @Test
    fun `cada clave va por su lado`() = runBlocking {
        lecturas.leer("cuentas") { cuentas() }
        lecturas.leer("ultimos:3") { cuentas() }
        lecturas.leer("ultimos:5") { cuentas() }
        lecturas.leer("ultimos:3") { cuentas() }
        assertEquals(3, pedidos)
    }

    @Test
    fun `una escritura la vuelve vieja`() = runBlocking {
        lecturas.leer("cuentas") { cuentas() }
        generacion++ // lo que hace InvalidaElInicioAlEscribir tras escribir
        lecturas.leer("cuentas") { cuentas() }
        assertEquals(2, pedidos)
    }

    @Test
    fun `lo que se leyo mientras alguien escribia no se recuerda`() = runBlocking {
        lecturas.leer("cuentas") { generacion++; cuentas() }
        lecturas.leer("cuentas") { cuentas() }
        assertEquals(2, pedidos)
    }

    @Test
    fun `otra sesion no ve lo de la anterior, y sin sesion no se recuerda nada`() = runBlocking {
        lecturas.leer("cuentas") { cuentas() }
        usuario = "u2"
        lecturas.leer("cuentas") { cuentas() }
        assertEquals(2, pedidos)
        usuario = null
        lecturas.leer("cuentas") { cuentas() }
        lecturas.leer("cuentas") { cuentas() }
        assertEquals(4, pedidos)
    }

    @Test
    fun `una falla no se recuerda, y quien esperaba recibe la misma falla`() = runBlocking {
        val puerta = CompletableDeferred<Unit>()
        coroutineScope {
            val a = async { runCatching { lecturas.leer<List<String>>("cuentas") { puerta.await(); pedidos++; throw IOException("sin señal") } } }
            val b = async { runCatching { lecturas.leer<List<String>>("cuentas") { pedidos++; listOf("no debería salir") } } }
            yield()
            puerta.complete(Unit)
            assertEquals("sin señal", a.await().exceptionOrNull()?.message)
            assertEquals("sin señal", b.await().exceptionOrNull()?.message)
        }
        assertEquals(1, pedidos)
        // Reintentar sale de verdad.
        assertEquals(listOf("Nu", "Bancolombia"), lecturas.leer("cuentas") { cuentas() })
        assertEquals(2, pedidos)
    }

    @Test
    fun `si quien la pidio primero se va, la pide quien la esperaba`() = runBlocking {
        val nunca = CompletableDeferred<Unit>()
        var resultado: List<String>? = null
        coroutineScope {
            val primero = launch { lecturas.leer("cuentas") { nunca.await(); cuentas() } }
            yield()
            val segundo = async { lecturas.leer("cuentas") { cuentas() } }
            yield()
            primero.cancel()
            resultado = segundo.await()
        }
        assertEquals(listOf("Nu", "Bancolombia"), resultado)
        assertEquals(1, pedidos)
        assertEquals(1, lecturas.cuantasFrescas)
    }

    @Test
    fun `una lectura nula tambien se recuerda`() = runBlocking {
        var veces = 0
        repeat(3) { lecturas.leer<String?>("cierre") { veces++; null } }
        assertEquals(1, veces)
    }

    @Test
    fun `un reloj que va para atras no da por fresca nada`() = runBlocking {
        lecturas.leer("cuentas") { cuentas() }
        ahora -= 5_000
        lecturas.leer("cuentas") { cuentas() }
        assertEquals(2, pedidos)
    }

    @Test
    fun `la cancelacion de quien pidio llega a quien pidio`() {
        assertFailsWith<kotlinx.coroutines.CancellationException> {
            runBlocking {
                val trabajo = async { lecturas.leer("cuentas") { CompletableDeferred<Unit>().await(); cuentas() } }
                yield()
                trabajo.cancel()
                trabajo.await()
            }
        }
    }
}
