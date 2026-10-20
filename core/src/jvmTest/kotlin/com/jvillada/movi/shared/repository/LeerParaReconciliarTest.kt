package com.jvillada.movi.shared.repository

import com.jvillada.movi.shared.model.AvisoSinLeer
import com.jvillada.movi.shared.model.DudaDelAviso
import com.jvillada.movi.shared.model.PropuestaDelAviso
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.comoLectura
import com.jvillada.movi.shared.model.dudasDelAviso
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * **«Anotarlo igual» llega a Reconciliar.** La app pide `/parse?anotarloIgual=1`; un aviso que no se
 * pudo leer contesta 422 con el motivo y lo que trae en JSON. Un server viejo contesta el texto de
 * siempre, y eso sigue siendo el error de siempre ([ElMotivoDeNoPoderLeerUnSmsTest]).
 */
class LeerParaReconciliarTest {

    private val motivo = "Este mensaje no trae un movimiento para anotar. Puedes ignorarlo."
    private var pedido: String? = null

    private fun repo(status: HttpStatusCode, cuerpo: String, tipo: String): WalletRepositoryImpl {
        val engine = MockEngine { request ->
            pedido = request.url.toString()
            respond(content = cuerpo, status = status, headers = headersOf(HttpHeaders.ContentType, tipo))
        }
        return WalletRepositoryImpl(HttpClient(engine) { install(ContentNegotiation) { json() } }, "https://movi.test")
    }

    @Test
    fun `un 422 en JSON trae el motivo y lo que trae el aviso`() = runBlocking {
        val cuerpo = """{"motivo":"$motivo","sinLeer":{"monto":1000.0,"moneda":"COP","tipo":"EXPENSE","momento":1792345678000}}"""
        val lectura = repo(HttpStatusCode.UnprocessableEntity, cuerpo, "application/json").leerParaReconciliar("notif_1")

        assertTrue(pedido!!.endsWith("/api/sms/notif_1/parse?anotarloIgual=1"), "pidió $pedido")
        assertNull(lectura.leido)
        assertEquals(motivo, lectura.noSePudoLeer)
        assertEquals(1_000.0, lectura.sinLeer?.monto)
        assertEquals(TransactionType.EXPENSE, lectura.sinLeer?.tipo)
    }

    @Test
    fun `un 200 es la lectura de siempre`() = runBlocking {
        val cuerpo = """{"amount":15100.0,"merchant":"TOSTAO","type":"EXPENSE","category":"Comida"}"""
        val lectura = repo(HttpStatusCode.OK, cuerpo, "application/json").leerParaReconciliar("sms_1")

        assertEquals("TOSTAO", lectura.leido?.merchant)
        assertNull(lectura.sinLeer)
    }

    @Test
    fun `un server viejo contesta el texto, y sigue siendo el error de siempre`() = runBlocking {
        val error = assertFailsWith<ApiException> {
            repo(HttpStatusCode.UnprocessableEntity, motivo, "text/plain; charset=UTF-8").leerParaReconciliar("notif_1")
        }
        assertEquals(422, error.status)
        assertEquals(motivo, error.serverMessage)
    }

    @Test
    fun `la lectura con que se anota lleva el monto, la moneda y lo que elige el duenno`() {
        val sinLeer = AvisoSinLeer(monto = 1_000.0, moneda = "COP", tipo = TransactionType.EXPENSE, momento = 1L)

        val comoGasto = sinLeer.comoLectura()
        assertEquals(1_000.0, comoGasto.amount)
        assertEquals("Movimiento", comoGasto.merchant, "sin nombre, el de siempre de parseSms")
        assertEquals("", comoGasto.category, "sin propuesta, la categoría se elige")

        val comoIngreso = sinLeer.copy(comercio = "Carolina R.", categoria = "Familia").comoLectura(monto = 1_200.0, tipo = TransactionType.INCOME)
        assertEquals(1_200.0, comoIngreso.amount)
        assertEquals(TransactionType.INCOME, comoIngreso.type)
        assertEquals("Carolina R.", comoIngreso.merchant)
        assertEquals("Familia", comoIngreso.category)
    }

    /** La fila de «Por revisar» no lo confirma: sin lectura, la duda es «no se leyó» y va a Revisar. */
    @Test
    fun `en la bandeja un aviso sin leer sigue yendo a Revisar`() {
        val propuesta = PropuestaDelAviso(avisoId = "notif_1", noSePudoLeer = motivo, sinLeer = AvisoSinLeer(1_000.0, momento = 1L))
        val dudas = dudasDelAviso(
            leido = propuesta.leido,
            coincidencias = 0,
            destinoSinNombre = false,
            cuentaSegura = true,
            nombraOtraCuentaSuya = false,
            categoriasDelDueno = listOf("Familia"),
        )
        assertEquals(setOf(DudaDelAviso.NO_SE_LEYO), dudas)
    }
}
