package com.jvillada.movi.shared.repository

import com.jvillada.movi.shared.model.Budget
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
 * **Un presupuesto que ya existe llega a la pantalla como un 409**, no como un error de lectura.
 *
 * Crear las propuestas de Presupuestos cuenta un 409 como «ya estaba creado»: el POST que se guardó
 * pero cuya respuesta se perdió en una red mala, reintentado. El server contesta ese 409 con texto
 * plano, y `createBudget` intentaba leerlo como un `Budget`: salía una
 * excepción de deserialización, la pantalla decía «No pudimos crear el presupuesto de Comida» y
 * cada reintento repetía lo mismo sobre un presupuesto que sí existía.
 */
class CrearPresupuestoRepetidoTest {

    private fun repo(status: HttpStatusCode, cuerpo: String, tipo: String): WalletRepositoryImpl {
        val engine = MockEngine { respond(cuerpo, status, headersOf(HttpHeaders.ContentType, tipo)) }
        return WalletRepositoryImpl(HttpClient(engine) { install(ContentNegotiation) { json() } }, "https://movi.test")
    }

    @Test
    fun `un 409 en texto plano sale como ApiException 409`() {
        val repo = repo(HttpStatusCode.Conflict, "Ya existe un presupuesto llamado \"Comida\"", "text/plain")

        val error = assertFailsWith<ApiException> { runBlocking { repo.createBudget(Budget("Comida", 500_000)) } }
        assertEquals(409, error.status)
        assertEquals("Ya existe un presupuesto llamado \"Comida\"", error.serverMessage, "la hoja «Nuevo presupuesto» muestra este texto")
    }

    @Test
    fun `el alta que sale bien devuelve el presupuesto`() = runBlocking {
        val repo = repo(HttpStatusCode.Created, """{"category":"Comida","monthlyLimit":500000}""", "application/json")

        assertEquals(Budget("Comida", 500_000), repo.createBudget(Budget("Comida", 500_000)))
    }
}
