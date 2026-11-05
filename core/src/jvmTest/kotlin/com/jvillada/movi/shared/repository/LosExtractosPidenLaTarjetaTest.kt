package com.jvillada.movi.shared.repository

import com.jvillada.movi.shared.model.ClaveDelPdf
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
import kotlin.test.assertTrue

/**
 * Vuelta 129 · **todo camino que lee un papel pide `conTarjeta=1`**. Sin el parámetro el server no
 * dice que el extracto es de una tarjeta, y la revisión proponía la de la tarjeta Nu en «Nu Ahorros».
 * Faltaba en `abrirPapelConClave`: el PDF de la tarjeta con clave (Importar → clave, Compartir → Abrir).
 */
class LosExtractosPidenLaTarjetaTest {

    private val pedidos = mutableListOf<String>()

    private fun repo(cuerpo: String): WalletRepositoryImpl {
        val engine = MockEngine { request ->
            pedidos += request.url.toString()
            respond(content = cuerpo, status = HttpStatusCode.OK, headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }
        return WalletRepositoryImpl(HttpClient(engine) { install(ContentNegotiation) { json() } }, "https://movi.test")
    }

    private val lectura = """{"documentoId":"doc_1","que":"EXTRACTO"}"""
    private val extracto = """{"statementId":"s","bankName":"Nu","period":"","newTransactions":[],"matches":[]}"""

    @Test
    fun `abrir un PDF con clave pide la tarjeta`() = runBlocking {
        repo(lectura).abrirPapelConClave("doc_1", ClaveDelPdf("1234"))
        assertTrue("conTarjeta=1" in pedidos.single(), "pidió ${pedidos.single()}")
    }

    @Test
    fun `leer un papel y leer el extracto de un documento piden la tarjeta`() = runBlocking {
        repo(lectura).leerPapel("doc_1", anotarAunqueEsteAnotado = false)
        repo(extracto).readStatementFromDocument("doc_1")
        assertTrue(pedidos.all { "conTarjeta=1" in it }, "pidió $pedidos")
    }
}
