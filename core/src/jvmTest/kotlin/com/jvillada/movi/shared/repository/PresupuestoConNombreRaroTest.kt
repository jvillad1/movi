package com.jvillada.movi.shared.repository

import com.jvillada.movi.shared.model.Budget
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * El nombre de un presupuesto NO va en la ruta.
 *
 * «Luz/Agua» se creaba bien —el POST lo manda en el cuerpo— y después no se podía ni editar ni
 * borrar: `/api/budgets/Luz/Agua` son dos segmentos y el server hace coincidir uno solo, así que
 * era 404 para siempre y la hoja solo decía que no lo encontró. Con «%» el path ni siquiera
 * decodifica. Acá se mira lo único que hace falta para que eso no vuelva: qué ruta y qué cuerpo
 * sale del cliente.
 */
class PresupuestoConNombreRaroTest {

    private val pedidos = mutableListOf<Pair<HttpRequestData, String>>()

    private fun repo(): WalletRepositoryImpl {
        pedidos.clear()
        val engine = MockEngine { request ->
            pedidos += request to ((request.body as? TextContent)?.text ?: "")
            respond(
                content = """{"category":"Luz/Agua","monthlyLimit":500000}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val client = HttpClient(engine) { install(ContentNegotiation) { json() } }
        return WalletRepositoryImpl(client, "https://movi.test")
    }

    /** Lo que el cliente mandó: ruta completa (codificada, tal cual viaja) y cuerpo. */
    private val ruta get() = pedidos.single().first.url.encodedPath
    private val metodo get() = pedidos.single().first.method
    private val cuerpo get() = Json.parseToJsonElement(pedidos.single().second).jsonObject

    private fun campo(nombre: String) = cuerpo[nombre]!!.jsonPrimitive.content

    @Test
    fun `editar un presupuesto con barra en el nombre no lo mete en la ruta`() = runBlocking {
        repo().updateBudget("Luz/Agua", Budget("Luz/Agua", 500_000))

        assertEquals("/api/budgets", ruta, "el nombre no puede partir la ruta en dos")
        assertEquals(HttpMethod.Put, metodo)
        assertEquals("Luz/Agua", campo("category"))
        assertEquals("500000", campo("monthlyLimit"))
    }

    @Test
    fun `borrar un presupuesto con porcentaje en el nombre no lo mete en la ruta`() = runBlocking {
        repo().deleteBudget("Ahorro 50%")

        assertEquals("/api/budgets/delete", ruta)
        assertEquals(HttpMethod.Post, metodo)
        assertEquals("Ahorro 50%", campo("category"))
    }

    @Test
    fun `renombrar manda los dos nombres en el cuerpo`() = runBlocking {
        repo().renameBudget("Luz/Agua", "Servicios 100%")

        assertEquals("/api/budgets/rename", ruta)
        assertEquals(HttpMethod.Post, metodo)
        assertEquals("Luz/Agua", campo("category"))
        assertEquals("Servicios 100%", campo("newCategory"))
    }

    @Test
    fun `ninguna de las tres rutas lleva el nombre pegado`() = runBlocking {
        val nombres = listOf("Luz/Agua", "Ahorro 50%", "Casa#1")
        for (nombre in nombres) {
            repo().updateBudget(nombre, Budget(nombre, 1_000))
            assertEquals("/api/budgets", ruta, "editar «$nombre»")

            repo().deleteBudget(nombre)
            assertEquals("/api/budgets/delete", ruta, "borrar «$nombre»")

            repo().renameBudget(nombre, "Otro")
            assertEquals("/api/budgets/rename", ruta, "renombrar «$nombre»")
        }
        // Y ni el nombre codificado ni el crudo se filtran a la URL.
        repo().deleteBudget("Luz/Agua")
        val url = pedidos.single().first.url.toString()
        assertTrue("Luz" !in url && "%2F" !in url, "el nombre no debe aparecer en la URL: $url")
    }
}
