package com.jvillada.movi.server.observabilidad

import com.jvillada.movi.server.ai.FallasDeLaApiDePrueba
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * # El envoltorio de los servicios de afuera (vuelta 102)
 *
 * Un éxito con fichas, un error sin crédito y un 429 se cuentan cada uno en su lugar, **sin tocar la
 * red**: las «llamadas» son lambdas que devuelven o lanzan lo que lanzaría el SDK.
 */
class LlamadasDeAfueraTest {

    @BeforeTest fun limpiar() { Metricas.reiniciar(); Metricas.reloj = { 42 * Metricas.UN_MINUTO } }
    @AfterTest fun soltar() = Metricas.reiniciar()

    private fun anotado(servicio: String, operacion: String) =
        Metricas.cerrado(Long.MAX_VALUE).servicios.entries.single { it.key.servicio == servicio && it.key.operacion == operacion }.value

    @Test
    fun `un exito, un sin credito y un 429 de Anthropic se cuentan bien`() {
        val modelo = "claude-haiku-de-prueba"
        val respuesta = medirLlamada(Servicio.ANTHROPIC, modelo, { _: String -> Desenlace(fichas = Fichas(1_500, 300, 1_000)) }) { "hola" }
        assertEquals("hola", respuesta, "medir no cambia lo que devuelve la llamada")

        val sinCredito = FallasDeLaApiDePrueba.sinCredito()
        val lanzada = assertFailsWith<Exception> { medirLlamada<String>(Servicio.ANTHROPIC, modelo) { throw sinCredito } }
        assertTrue(lanzada === sinCredito, "y vuelve a lanzar la MISMA excepción")
        assertFailsWith<Exception> { medirLlamada<String>(Servicio.ANTHROPIC, modelo) { throw FallasDeLaApiDePrueba.limiteDeUso() } }
        assertFailsWith<Exception> { medirLlamada<String>(Servicio.ANTHROPIC, modelo) { throw FallasDeLaApiDePrueba.errorInterno() } }
        assertFailsWith<Exception> { medirLlamada<String>(Servicio.ANTHROPIC, modelo) { throw java.net.http.HttpTimeoutException("lento") } }

        val a = anotado(Servicio.ANTHROPIC, modelo)
        assertEquals(5L, a.llamadas)
        assertEquals(1L, a.sinCredito)
        assertEquals(1L, a.limite)
        assertEquals(1L, a.servidor)
        assertEquals(1L, a.otros, "el timeout es un error de red, que va a «otros»")
        assertEquals(1_500L, a.fichasEntrada)
        assertEquals(300L, a.fichasSalida)
        assertEquals(1_000L, a.fichasCache)
    }

    @Test
    fun `una respuesta http se clasifica por su status`() {
        listOf(200, 202, 404, 429, 503).forEach { status ->
            medirLlamada(Servicio.RESEND, "lectura", { s: Int -> desenlaceHttp(s) }) { status }
        }
        val r = anotado(Servicio.RESEND, "lectura")
        assertEquals(5L, r.llamadas)
        assertEquals(1L, r.limite)
        assertEquals(1L, r.servidor)
        assertEquals(1L, r.otros)
        assertEquals(0L, r.sinCredito)
    }

    @Test
    fun `una llamada a la IA dentro de un pedido marca que el pedido la uso`() = runBlocking {
        val pedido = ElPedidoEnCurso("POST /api/ai/chat")
        withContext(ElPedidoEnCurso.comoContexto(pedido)) {
            withContext(kotlinx.coroutines.Dispatchers.IO) {
                medirLlamada(Servicio.ANTHROPIC, "claude-x") { "ok" }
            }
        }
        assertTrue(pedido.usoLaIa, "el hilo de IO también ve el pedido en curso")
        // Fuera de un pedido no hay a quién marcar, y no falla.
        medirLlamada(Servicio.ANTHROPIC, "claude-x") { "ok" }
        assertEquals(2L, anotado(Servicio.ANTHROPIC, "claude-x").llamadas)
    }

    @Test
    fun `el error de una corrida se guarda sin numeros largos ni correos`() {
        val e = NumberFormatException("For input string \"1.843.014\" de caro@movi.test en la cuenta 50009876")
        val recortado = errorRecortado(e)
        assertTrue(recortado.startsWith("NumberFormatException: "))
        assertTrue("1.843.014" !in recortado && "50009876" !in recortado && "caro@" !in recortado, recortado)
        assertTrue(errorRecortado(IllegalStateException("x".repeat(1_000))).length <= 200)
    }
}
