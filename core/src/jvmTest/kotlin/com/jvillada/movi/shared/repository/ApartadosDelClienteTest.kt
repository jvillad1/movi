package com.jvillada.movi.shared.repository

import com.jvillada.movi.shared.model.Apartado
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** El cliente de apartados: un server viejo no rompe la lectura, y el alta lleva un id propio. */
class ApartadosDelClienteTest {

    private val json = headersOf(HttpHeaders.ContentType, "application/json")

    private fun repo(engine: MockEngine) =
        WalletRepositoryImpl(HttpClient(engine) { install(ContentNegotiation) { json() } }, "https://movi.test")

    @Test
    fun `un 404 de un server viejo es una lista vacia`() = runBlocking {
        val repo = repo(MockEngine { respond("", HttpStatusCode.NotFound) })
        assertEquals(emptyList(), repo.getApartados())
    }

    @Test
    fun `otro error de lectura sale como ApiException`() {
        val repo = repo(MockEngine { respond("boom", HttpStatusCode.InternalServerError) })
        val e = assertFailsWith<ApiException> { runBlocking { repo.getApartados() } }
        assertEquals(500, e.status)
    }

    @Test
    fun `guardar sin id manda un POST con id apt_ generado`() = runBlocking {
        var cuerpo = ""
        var metodo: HttpMethod? = null
        val repo = repo(MockEngine { req ->
            metodo = req.method
            cuerpo = req.body.toByteArray().decodeToString()
            respond("""{"id":"apt_x","accountId":"a1","proposito":"SOAT","monto":100,"restante":100}""", HttpStatusCode.Created, json)
        })
        val guardado = repo.guardarApartado(Apartado(accountId = "a1", proposito = "SOAT", monto = 100))
        assertEquals(HttpMethod.Post, metodo)
        assertTrue(Regex(""""id":"apt_[^"]+"""").containsMatchIn(cuerpo), cuerpo)
        assertEquals("apt_x", guardado.id)
    }

    @Test
    fun `quitar un apartado que el server rechaza sale como ApiException`() {
        val repo = repo(MockEngine { respond("no", HttpStatusCode.NotFound) })
        val e = assertFailsWith<ApiException> { runBlocking { repo.quitarApartado("apt_1") } }
        assertEquals(404, e.status)
    }
}
