package com.jvillada.movi.server.routes

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * El contrato de `/version`, que es la única forma que tiene alguien de afuera —una persona o el
 * workflow de despliegue— de saber qué commit está sirviendo producción.
 *
 * Las dos mitades importan igual:
 *  - con el SHA conocido → 200 y el SHA;
 *  - sin el SHA → 503 y `null` EXPLÍCITO. Que «no lo sé» sea indistinguible de «ya desplegó» es
 *    exactamente el silencio que este endpoint viene a romper.
 *
 * El tercer test es el que evita inventar: un valor basura en la variable (una rama, `unknown`,
 * un texto cualquiera) no puede salir por la respuesta como si fuera un commit.
 */
class VersionRouteTest {

    @AfterTest
    fun limpiar() {
        System.clearProperty("movi.commit.sha")
    }

    private fun leerCommit(cuerpo: String) =
        Json.parseToJsonElement(cuerpo).jsonObject["commit"]!!

    @Test
    fun `con el commit conocido responde 200 y el sha`() = testApplication {
        System.setProperty("movi.commit.sha", "ffce2359bd0f4b3c2a1e8d7c6b5a4938271605ab")
        application { routing { versionRoutes() } }

        val res = client.get("/version")
        assertEquals(HttpStatusCode.OK, res.status)
        assertEquals(
            "ffce2359bd0f4b3c2a1e8d7c6b5a4938271605ab",
            leerCommit(res.bodyAsText()).jsonPrimitive.content,
        )
    }

    @Test
    fun `sin el commit responde 503 y un null explicito`() = testApplication {
        System.clearProperty("movi.commit.sha")
        application { routing { versionRoutes() } }

        val res = client.get("/version")
        // 503, no 200: un script no puede confundir «no lo sé» con «ya desplegó».
        assertEquals(HttpStatusCode.ServiceUnavailable, res.status)
        assertEquals(JsonNull, leerCommit(res.bodyAsText()))
        assertTrue(res.bodyAsText().contains("\"commit\""), "el campo tiene que estar presente, no omitido")
    }

    @Test
    fun `un valor que no parece un sha se trata como desconocido`() = testApplication {
        System.setProperty("movi.commit.sha", "master")
        application { routing { versionRoutes() } }

        val res = client.get("/version")
        assertEquals(HttpStatusCode.ServiceUnavailable, res.status)
        assertEquals(JsonNull, leerCommit(res.bodyAsText()))
    }
}
