package com.jvillada.movi.data

import com.jvillada.movi.shared.SyncEngine
import com.jvillada.movi.shared.db.createDatabase
import com.jvillada.movi.shared.repository.LocalRepository
import com.jvillada.movi.shared.repository.WalletRepository
import com.jvillada.movi.shared.repository.WalletRepositoryImpl
import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.HttpCallValidator
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
        socketTimeoutMillis = 120_000
        connectTimeoutMillis = 30_000
    }
    defaultRequest {
        SessionManager.token?.let { token ->
            headers.append("Authorization", "Bearer $token")
        }
    }
    install(HttpCallValidator) {
        validateResponse { response ->
            if (response.status == HttpStatusCode.Unauthorized) SessionManager.clear()
        }
    }
}

actual val apiBaseUrl: String = "http://localhost:8080"

actual fun createRepository(): WalletRepository {
    val remote = WalletRepositoryImpl(createHttpClient(), apiBaseUrl)
    val db = createDatabase("movi.db")
    val userId = { SessionManager.userId ?: "" }
    SyncEngine(db, remote, userId).start()
    return LocalRepository(db, remote, userId)
}
