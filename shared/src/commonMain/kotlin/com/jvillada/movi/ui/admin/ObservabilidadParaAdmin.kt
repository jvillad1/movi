package com.jvillada.movi.ui.admin

import com.jvillada.movi.data.apiBaseUrl
import com.jvillada.movi.data.createHttpClient
import com.jvillada.movi.shared.model.EventoDelRecorrido
import com.jvillada.movi.shared.model.ResumenDeFallos
import com.jvillada.movi.shared.model.ResumenDeUso
import com.jvillada.movi.shared.model.SesionDeUso
import com.jvillada.movi.shared.model.UsuarioConUso
import com.jvillada.movi.shared.repository.ApiException
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.encodeURLPathPart
import io.ktor.http.isSuccess

/**
 * **Lo que lee la pantalla de admin «Fallos y uso»** (vuelta 100): los `GET /api/admin/…`, que solo
 * contestan a un admin. Aparte del repositorio de la app a propósito: no tiene espejo local ni caché,
 * no es de la persona sino de quien administra, y así la vuelta del servidor puede sumar lo suyo sin
 * tocar `WalletRepository`.
 */
interface ObservabilidadParaAdmin {
    suspend fun fallos(): ResumenDeFallos
    suspend fun uso(): ResumenDeUso
    suspend fun usuarios(): List<UsuarioConUso>
    suspend fun sesiones(usuarioId: String): List<SesionDeUso>
    suspend fun eventos(sesionId: String): List<EventoDelRecorrido>

    companion object {
        /** La costura de las pruebas, como `Repositories.sustitutoDePrueba`. */
        internal var sustitutoDePrueba: ObservabilidadParaAdmin? = null
        private val real: ObservabilidadParaAdmin by lazy { ObservabilidadParaAdminApi(createHttpClient(), apiBaseUrl) }
        val actual: ObservabilidadParaAdmin get() = sustitutoDePrueba ?: real
    }
}

class ObservabilidadParaAdminApi(private val client: HttpClient, private val baseUrl: String) : ObservabilidadParaAdmin {
    private suspend inline fun <reified T> leer(ruta: String): T {
        val respuesta: HttpResponse = client.get("$baseUrl$ruta")
        if (!respuesta.status.isSuccess()) throw ApiException(respuesta.status.value, respuesta.bodyAsText())
        return respuesta.body()
    }

    override suspend fun fallos(): ResumenDeFallos = leer("/api/admin/fallos")
    override suspend fun uso(): ResumenDeUso = leer("/api/admin/uso")
    override suspend fun usuarios(): List<UsuarioConUso> = leer("/api/admin/uso/usuarios")
    override suspend fun sesiones(usuarioId: String): List<SesionDeUso> =
        leer("/api/admin/uso/usuarios/${usuarioId.encodeURLPathPart()}/sesiones")
    override suspend fun eventos(sesionId: String): List<EventoDelRecorrido> =
        leer("/api/admin/uso/sesiones/${sesionId.encodeURLPathPart()}/eventos")
}
