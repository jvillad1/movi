package com.jvillada.movi.shared.repository

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

/**
 * **Cuando el server no puede leer un mensaje del banco, dice por qué, y ese porqué llega.**
 *
 * `GET /api/sms/{id}/parse` contesta 422 con el motivo en texto («Este mensaje no trae un movimiento
 * para anotar. Puedes ignorarlo.»). El cliente hacía `.body()` sin mirar el status: fallaba
 * deserializando ese texto como un `ParsedSms`, el motivo se perdía y el dueño leía «Algo salió
 * mal» con la tarjeta de la sugerencia en «Parseando…» para siempre (23-sep, una notificación de
 * Nu). Acá se fija que sale un [ApiException] con el cuerpo, que es lo que `toUserMessage` sabe leer.
 */
class ElMotivoDeNoPoderLeerUnSmsTest {

    private val motivo = "Este mensaje no trae un movimiento para anotar. Puedes ignorarlo."

    private fun repo(): WalletRepositoryImpl {
        val engine = MockEngine {
            respond(
                content = motivo,
                status = HttpStatusCode.UnprocessableEntity,
                headers = headersOf(HttpHeaders.ContentType, "text/plain; charset=UTF-8"),
            )
        }
        val client = HttpClient(engine) { install(ContentNegotiation) { json() } }
        return WalletRepositoryImpl(client, "https://movi.test")
    }

    @Test
    fun `un 422 del parseo llega como ApiException con el motivo`() = runBlocking {
        val error = assertFailsWith<ApiException> { repo().parseSms("notif_1") }
        assertEquals(422, error.status)
        assertEquals(motivo, error.serverMessage)
    }
}
