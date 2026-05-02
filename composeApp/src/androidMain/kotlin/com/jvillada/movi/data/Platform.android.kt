package com.jvillada.movi.data

import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

actual fun createHttpClient(): HttpClient = HttpClient(Android) {
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
}

// 10.0.2.2 is the host loopback alias on Android emulator
actual val apiBaseUrl: String = "http://10.0.2.2:8080"
