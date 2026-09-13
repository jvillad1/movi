package com.jvillada.movi.server.plugins

import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Lo que un pedido roto devuelve, medido con el mismo `configureSerialization` +
 * `configureStatusPages` que arranca `Application.module`. Ver [configureStatusPages].
 */
class PedidoMalFormadoTest {

    @Serializable
    private data class Cosa(val nombre: String, val monto: Long)

    private fun ApplicationTestBuilder.montar() {
        application {
            configureSerialization()
            configureStatusPages()
            routing {
                post("/recibe") { call.respond(call.receive<Cosa>().nombre) }
                post("/crudo") {
                    val crudo = call.receive<JsonObject>()
                    call.respond(jsonDeLaApi.decodeFromJsonElement<Cosa>(crudo).nombre)
                }
                post("/bug") { error("un bug nuestro") }
            }
        }
    }

    private suspend fun ApplicationTestBuilder.mandar(ruta: String, cuerpo: String) = client.post(ruta) {
        contentType(ContentType.Application.Json)
        setBody(cuerpo)
    }

    @Test
    fun `un JSON roto o sin un campo obligatorio es 400 con motivo`() = testApplication {
        montar()
        for (cuerpo in listOf("{no es json", """{"nombre":"x"}""", """{"nombre":"x","monto":"mucho"}""")) {
            val res = mandar("/recibe", cuerpo)
            assertEquals(HttpStatusCode.BadRequest, res.status, cuerpo)
            assertEquals(PEDIDO_ILEGIBLE, res.bodyAsText())
        }
        assertEquals(HttpStatusCode.OK, mandar("/recibe", """{"nombre":"x","monto":1}""").status)
    }

    @Test
    fun `decodificar a mano el JSON crudo tambien da 400`() = testApplication {
        montar()
        val res = mandar("/crudo", """{"nombre":"x"}""")
        assertEquals(HttpStatusCode.BadRequest, res.status)
        assertEquals(PEDIDO_ILEGIBLE, res.bodyAsText())
    }

    /**
     * Un bug nuestro NO se disfraza de 400: el plugin no lo atrapa. (El test host de Ktor re-lanza
     * la excepción que ningún handler tomó en vez de contestar 500, así que eso es lo que se mide.)
     */
    @Test
    fun `un error nuestro no se disfraza de pedido mal formado`() {
        assertFailsWith<IllegalStateException> {
            testApplication {
                montar()
                mandar("/bug", "{}")
            }
        }
    }
}
