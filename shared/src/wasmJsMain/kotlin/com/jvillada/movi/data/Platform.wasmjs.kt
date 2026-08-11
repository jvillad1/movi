package com.jvillada.movi.data

import com.jvillada.movi.shared.repository.WalletRepository
import com.jvillada.movi.shared.repository.WalletRepositoryImpl
import io.ktor.client.HttpClient
import io.ktor.client.engine.js.Js
import io.ktor.client.plugins.HttpCallValidator
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import kotlinx.browser.window
import kotlinx.serialization.json.Json

actual fun createHttpClient(): HttpClient = HttpClient(Js) {
    install(ContentNegotiation) {
        json(Json { ignoreUnknownKeys = true })
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
        // JS fetch errors are network-level — session stays alive
    }
}

actual val apiBaseUrl: String = window.location.origin

actual fun createRepository(): WalletRepository = WalletRepositoryImpl(createHttpClient(), apiBaseUrl)

// Recarga la página: es lo único que hace que index.html vuelva a evaluar su overlay HTML
// nativo (el único login con el que el gestor de contraseñas del navegador sabe hablar) en
// vez de dejar a Compose mostrando su propio LoginScreen sobre el canvas. Ver el comentario
// en Platform.kt para el porqué completo.
actual fun reloadForLogout() { window.location.reload() }
