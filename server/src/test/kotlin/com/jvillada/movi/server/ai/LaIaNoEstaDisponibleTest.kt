package com.jvillada.movi.server.ai

import com.anthropic.models.messages.MessageParam
import com.jvillada.movi.server.ai.FallasDeLaApiDePrueba.claveRechazada
import com.jvillada.movi.server.ai.FallasDeLaApiDePrueba.errorInterno
import com.jvillada.movi.server.ai.FallasDeLaApiDePrueba.facturacion
import com.jvillada.movi.server.ai.FallasDeLaApiDePrueba.limiteDeUso
import com.jvillada.movi.server.ai.FallasDeLaApiDePrueba.modeloQueNoExiste
import com.jvillada.movi.server.ai.FallasDeLaApiDePrueba.pedidoMalArmado
import com.jvillada.movi.server.ai.FallasDeLaApiDePrueba.saturada
import com.jvillada.movi.server.ai.FallasDeLaApiDePrueba.sinCredito
import com.jvillada.movi.server.ai.FallasDeLaApiDePrueba.sinPermiso
import com.jvillada.movi.shared.model.IA_NO_DISPONIBLE
import com.jvillada.movi.shared.model.IA_SIN_CREDITO
import com.jvillada.movi.shared.model.esCodigoDeIaNoDisponible
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** # Qué falla de la API es «la IA no está disponible» y cuál sigue siendo un error de verdad */
class LaIaNoEstaDisponibleTest {

    @Test
    fun `sin credito, por el mensaje del 400 o por el 402`() {
        assertEquals(FallaDeLaIa.SIN_CREDITO, fallaDeLaIa(sinCredito()))
        assertEquals(FallaDeLaIa.SIN_CREDITO, fallaDeLaIa(facturacion()))
        assertEquals(IA_SIN_CREDITO, FallaDeLaIa.SIN_CREDITO.codigo)
    }

    @Test
    fun `la clave y la carga son no disponible`() {
        assertEquals(FallaDeLaIa.SIN_PERMISO, fallaDeLaIa(claveRechazada()))
        assertEquals(FallaDeLaIa.SIN_PERMISO, fallaDeLaIa(sinPermiso()))
        assertEquals(FallaDeLaIa.SATURADA, fallaDeLaIa(limiteDeUso()))
        assertEquals(FallaDeLaIa.SATURADA, fallaDeLaIa(saturada()))
        listOf(FallaDeLaIa.SIN_PERMISO, FallaDeLaIa.SATURADA).forEach { assertEquals(IA_NO_DISPONIBLE, it.codigo) }
    }

    @Test
    fun `un error de verdad no se esconde detras de no disponible`() {
        assertNull(fallaDeLaIa(pedidoMalArmado()))
        assertNull(fallaDeLaIa(errorInterno()))
        assertNull(fallaDeLaIa(modeloQueNoExiste()))
        assertNull(fallaDeLaIa(IllegalStateException("credit balance is too low")), "solo cuenta si viene de la API")
    }

    @Test
    fun `se reconoce aunque venga envuelta`() {
        assertEquals(FallaDeLaIa.SIN_CREDITO, fallaDeLaIa(RuntimeException("envuelta", sinCredito())))
    }

    @Test
    fun `los codigos son los que la app reconoce`() {
        FallaDeLaIa.entries.forEach { assertTrue(esCodigoDeIaNoDisponible(it.codigo)) }
        assertTrue(esCodigoDeIaNoDisponible("IA_SIN_CREDITO\n"))
        assertFalse(esCodigoDeIaNoDisponible("<html>502 Bad Gateway</html>"))
        assertFalse(esCodigoDeIaNoDisponible(null))
    }

    private class Lanzada(val falla: FallaDeLaIa) : Exception()

    @Test
    fun `conLaIa traduce solo lo que es de la cuenta`() {
        val traducida = assertFailsWith<Lanzada> { conLaIa("prueba", { throw Lanzada(it) }) { throw sinCredito() } }
        assertEquals(FallaDeLaIa.SIN_CREDITO, traducida.falla)
        val otra = pedidoMalArmado()
        assertSame(otra, assertFailsWith<Exception> { conLaIa("prueba", { throw Lanzada(it) }) { throw otra } })
        assertEquals(3, conLaIa("prueba", { throw Lanzada(it) }) { 3 })
    }

    // ── El respaldo del chat ────────────────────────────────────────────────────

    private fun modeloQueFallaCon(falla: Exception, pedidos: MutableList<String>) = ElModeloDeAnthropic(
        modelo = MODELO_DE_TODOS_LOS_DIAS,
        persona = "p",
        contexto = "c",
        mensajesDelDueno = listOf(MessageParam.builder().role(MessageParam.Role.USER).content("hola").build()),
        modeloDeRespaldo = MODELO_DE_RESPALDO,
        llamar = { params ->
            pedidos += params.model().asString()
            throw falla
        },
    )

    @Test
    fun `sin credito el chat no reintenta con el respaldo, que es la misma cuenta`() = runBlocking {
        val pedidos = mutableListOf<String>()
        val falla = sinCredito()
        assertSame(falla, assertFailsWith<Exception> { modeloQueFallaCon(falla, pedidos).siguienteVuelta(true) })
        assertEquals(listOf(MODELO_DE_TODOS_LOS_DIAS), pedidos)

        pedidos.clear()
        assertFailsWith<Exception> { modeloQueFallaCon(claveRechazada(), pedidos).siguienteVuelta(true) }
        assertEquals(1, pedidos.size, "con la clave rechazada tampoco")
    }

    @Test
    fun `saturada si prueba con el respaldo`() = runBlocking {
        val pedidos = mutableListOf<String>()
        assertFailsWith<Exception> { modeloQueFallaCon(saturada(), pedidos).siguienteVuelta(true) }
        assertEquals(listOf(MODELO_DE_TODOS_LOS_DIAS, MODELO_DE_RESPALDO), pedidos)
    }
}
