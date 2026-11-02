package com.jvillada.movi.ui.admin

import com.jvillada.movi.data.apiBaseUrl
import com.jvillada.movi.data.createHttpClient
import com.jvillada.movi.shared.model.CostoDeLaIa
import com.jvillada.movi.shared.model.GuardarTopeDeLaIa
import com.jvillada.movi.shared.model.TopesDeLaIa
import com.jvillada.movi.shared.repository.ApiException
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.encodeURLPathPart
import io.ktor.http.isSuccess

/**
 * **Movi AI para quien administra** (vuelta 106): lo que cuesta en pesos y el tope de preguntas libres
 * por mes. Los `…/api/admin/ia/…` solo contestan a un admin. Aparte del repositorio de la app, como
 * [ObservabilidadParaAdmin]: no es de la persona, no tiene caché ni espejo local.
 */
interface MoviAiParaAdmin {
    suspend fun costo(): CostoDeLaIa
    suspend fun topes(): TopesDeLaIa
    suspend fun guardarGlobal(tope: Int): TopesDeLaIa
    suspend fun guardarExcepcion(usuarioId: String, tope: Int): TopesDeLaIa
    suspend fun quitarExcepcion(usuarioId: String): TopesDeLaIa

    companion object {
        /** La costura de las pruebas, como `Repositories.sustitutoDePrueba`. */
        internal var sustitutoDePrueba: MoviAiParaAdmin? = null
        private val real: MoviAiParaAdmin by lazy { MoviAiParaAdminApi(createHttpClient(), apiBaseUrl) }
        val actual: MoviAiParaAdmin get() = sustitutoDePrueba ?: real
    }
}

class MoviAiParaAdminApi(private val client: HttpClient, private val baseUrl: String) : MoviAiParaAdmin {
    private suspend inline fun <reified T> exigir(respuesta: HttpResponse): T {
        if (!respuesta.status.isSuccess()) throw ApiException(respuesta.status.value, respuesta.bodyAsText())
        return respuesta.body()
    }

    override suspend fun costo(): CostoDeLaIa = exigir(client.get("$baseUrl/api/admin/ia/costo"))
    override suspend fun topes(): TopesDeLaIa = exigir(client.get("$baseUrl/api/admin/ia/topes"))
    override suspend fun guardarGlobal(tope: Int): TopesDeLaIa = exigir(
        client.put("$baseUrl/api/admin/ia/topes/global") {
            contentType(ContentType.Application.Json)
            setBody(GuardarTopeDeLaIa(tope))
        },
    )
    override suspend fun guardarExcepcion(usuarioId: String, tope: Int): TopesDeLaIa = exigir(
        client.put("$baseUrl/api/admin/ia/topes/usuarios/${usuarioId.encodeURLPathPart()}") {
            contentType(ContentType.Application.Json)
            setBody(GuardarTopeDeLaIa(tope))
        },
    )
    override suspend fun quitarExcepcion(usuarioId: String): TopesDeLaIa =
        exigir(client.delete("$baseUrl/api/admin/ia/topes/usuarios/${usuarioId.encodeURLPathPart()}"))
}
