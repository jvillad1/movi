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
            // Ver `cuentaComoSesionVencida`: el 401 del propio login no es una sesión que
            // venció — es una contraseña equivocada, y contarlo hacía que al tercer intento
            // la app se cerrara sola encima de la persona.
            if (response.status == HttpStatusCode.Unauthorized) {
                if (cuentaComoSesionVencida(response.call.request.url.encodedPath)) {
                    SessionManager.onUnauthorized()
                }
            } else if (response.status.value in 200..299) {
                SessionManager.onAuthSuccess()
            }
        }
        // Un fetch caído llega acá como `kotlin.Error("Fail to fetch", JsError)`, que no es una
        // Exception: se lanza en su lugar una IOException, igual que en Android, para que los
        // `catch (e: Exception)` de las pantallas la atrapen y no congele la app. Ver
        // [comoFalloDeRed]. Este hook (RequestError/ReceiveError de HttpCallValidator) envuelve
        // el pipeline de pedido —donde corre el motor— y el de recepción del cuerpo con un
        // `catch (Throwable)`; lo que se lanza desde acá reemplaza a la causa original.
        // La sesión sigue viva: un error de red no es un 401.
        handleResponseExceptionWithRequest { cause, _ ->
            comoFalloDeRed(cause)?.let { throw it }
        }
    }
}

actual val apiBaseUrl: String = window.location.origin

actual val isAndroid: Boolean = false

actual fun createRepository(): WalletRepository = WalletRepositoryImpl(createHttpClient(), apiBaseUrl)

// Recarga la página: es lo único que hace que index.html vuelva a evaluar su overlay HTML
// nativo (el único login con el que el gestor de contraseñas del navegador sabe hablar) en
// vez de dejar a Compose mostrando su propio LoginScreen sobre el canvas. Ver el comentario
// en Platform.kt para el porqué completo.
actual fun reloadForLogout() { window.location.reload() }
