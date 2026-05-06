package com.jvillada.movi.data

import com.jvillada.movi.shared.repository.WalletRepository
import com.jvillada.movi.shared.repository.WalletRepositoryImpl
import io.ktor.client.HttpClient
import io.ktor.client.engine.js.Js
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.HttpCallValidator
import io.ktor.client.plugins.defaultRequest
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

actual fun createHttpClient(): HttpClient = HttpClient(Js) {
    install(ContentNegotiation) {
        json(Json { ignoreUnknownKeys = true })
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

actual val apiBaseUrl: String = "https://movi-api-production.up.railway.app"

actual fun createRepository(): WalletRepository = WalletRepositoryImpl(createHttpClient(), apiBaseUrl)
