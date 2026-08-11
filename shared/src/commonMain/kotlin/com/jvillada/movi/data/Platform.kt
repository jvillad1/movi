package com.jvillada.movi.data

import com.jvillada.movi.shared.repository.WalletRepository
import io.ktor.client.HttpClient

expect fun createHttpClient(): HttpClient
expect val apiBaseUrl: String
expect fun createRepository(): WalletRepository

/**
 * Se llama justo después de que una sesión termina (logout explícito o el logout forzado de
 * [SessionManager.onUnauthorized] tras 401s repetidos).
 *
 * En Android/iOS es un no-op: Compose ya reacciona a `SessionManager.loggedIn` y navega sola a
 * [com.jvillada.movi.ui.Screen.Login].
 *
 * En la web (wasmJs) recarga la página. No es un capricho: el overlay HTML nativo de
 * `index.html` (el único login con el que el gestor de contraseñas del navegador sabe hablar)
 * se evalúa una sola vez, al cargar la página. Si no se recarga acá, cerrar sesión deja a
 * Compose mostrando SU propio `LoginScreen` sobre el canvas — exactamente el segundo login que
 * este cambio existe para eliminar, reaparecido por la puerta de atrás del logout. Recargar es
 * lo que hace que el overlay vuelva a tomar el control desde cero.
 */
expect fun reloadForLogout()
