package com.jvillada.movi.data

import com.jvillada.movi.shared.SyncEngine
import com.jvillada.movi.shared.db.createDatabase
import com.jvillada.movi.shared.repository.LocalRepository
import com.jvillada.movi.shared.repository.WalletRepository
import com.jvillada.movi.shared.repository.WalletRepositoryImpl
import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import io.ktor.client.plugins.HttpCallValidator
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

actual fun createHttpClient(): HttpClient = HttpClient(Darwin) {
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
            if (response.status == HttpStatusCode.Unauthorized) {
                SessionManager.onUnauthorized()
            } else if (response.status.value in 200..299) {
                SessionManager.onAuthSuccess()
            }
        }
        handleResponseExceptionWithRequest { cause, _ ->
            // Network errors — keep session alive, work offline
            if (cause.message?.contains("NSURLErrorDomain") == true ||
                cause.message?.contains("cancelled") == true) return@handleResponseExceptionWithRequest
            throw cause
        }
    }
}

actual val apiBaseUrl: String = "https://movi-project-production.up.railway.app"

actual val isAndroid: Boolean = false

actual fun createRepository(): WalletRepository {
    val remote = WalletRepositoryImpl(createHttpClient(), apiBaseUrl)
    val db = createDatabase("movi.db")
    val userId = { SessionManager.userId ?: "" }
    SyncEngine(db, remote, userId).start()
    return LocalRepository(db, remote, userId)
}

// No-op: no hay overlay HTML que recuperar acá. Compose navega solo al ver
// SessionManager.loggedIn == false (ver App.kt).
actual fun reloadForLogout() {}
