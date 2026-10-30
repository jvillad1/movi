package com.jvillada.movi.data

import com.jvillada.movi.shared.SyncEngine
import com.jvillada.movi.shared.db.createDatabase
import com.jvillada.movi.shared.repository.LocalRepository
import com.jvillada.movi.shared.repository.WalletRepository
import com.jvillada.movi.shared.repository.WalletRepositoryImpl
import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.HttpCallValidator
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import java.io.IOException

actual fun createHttpClient(): HttpClient = HttpClient(Android) {
    install(ContentNegotiation) {
        json(Json { ignoreUnknownKeys = true })
    }
    install(HttpTimeout) {
        requestTimeoutMillis = 120_000
        socketTimeoutMillis  = 120_000
        connectTimeoutMillis = 30_000
    }
    defaultRequest {
        SessionManager.token?.let { headers.append("Authorization", "Bearer $it") }
    }
    install(HttpCallValidator) {
        validateResponse { response ->
            // Un 401 de Movi con el token guardado cierra la sesión; el del login, el de otro
            // host o el de un pedido con el token de antes, no. Ver `laSesionTras`.
            val tokenDelPedido = tokenDelEncabezado(response.call.request.headers[HttpHeaders.Authorization])
            if (laSesionTras(response.status.value, response.call.request.url, tokenDelPedido, SessionManager.token) == LaSesionTras.SE_CERRO) {
                SessionManager.seRechazoElToken(tokenDelPedido)
            }
        }
        handleResponseExceptionWithRequest { cause, _ ->
            // Network errors (no connectivity) — keep session alive, work offline
            if (cause is IOException) return@handleResponseExceptionWithRequest
            throw cause
        }
    }
}

actual val apiBaseUrl: String = "https://movi-project-production.up.railway.app"

actual val isAndroid: Boolean = true

actual fun createRepository(): WalletRepository {
    val remote = WalletRepositoryImpl(createHttpClient(), apiBaseUrl)
    val db = createDatabase("movi.db")
    val userId = { SessionManager.userId ?: "" }
    // El mismo que corre cada 30 s es el que «Repartir» le pide subir ya (vuelta de diseño 24):
    // comparten el candado y no empujan la misma fila a la vez.
    // Vuelta 78: lo que sube cambia lo que el server contesta: lo leído hace menos de 30 s ya no es fresco.
    val sincronizador = SyncEngine(db, remote, userId, alSubirAlgo = {
        olvidarLoFresco()
        // Vuelta 82: lo que el héroe del Inicio adelantaba puede haber subido: que vuelva a mirar ya.
        com.jvillada.movi.ui.dashboard.MovimientosDelDueno.despertar()
    }).also { it.start() }
    return LocalRepository(db, remote, userId, sincronizador = sincronizador)
}

// No-op: no hay overlay HTML que recuperar acá. Compose navega solo al ver
// SessionManager.loggedIn == false (ver App.kt).
actual fun reloadForLogout() {}
