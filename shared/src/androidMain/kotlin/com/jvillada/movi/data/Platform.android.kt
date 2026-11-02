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
import com.jvillada.movi.observabilidad.anotarElErrorDeRedParaElUso
import com.jvillada.movi.observabilidad.anotarLaRespuestaParaElUso
import io.ktor.client.plugins.api.createClientPlugin
import java.io.IOException

/**
 * # Las pruebas no salen a la red (vuelta 100)
 *
 * En los logs de Railway había ráfagas de `401 GET /api/events/ev-hija/parecidos` y `/api/espacios`:
 * `ev-hija` solo existe en `HojaDelMovimientoTest`. El camino: una prueba Robolectric monta una
 * pantalla sin poner `Repositories.sustitutoDePrueba`, la pantalla lee `Repositories.wallets`, eso
 * construye el repositorio de verdad (`createRepository`) y su cliente sale a [apiBaseUrl] —
 * **producción**— sin token. Las pruebas no fallaban (un 401 se trata como «no hay datos») y
 * producción recibía basura en cada corrida del CI.
 *
 * Con [cerradaPorLasPruebas] en `true` —lo pone `AppDePrueba` antes de cada prueba— todo cliente de
 * [createHttpClient] que no vaya a la máquina misma (`127.0.0.1`) falla antes de salir, con [LaRedEstaCerradaEnLasPruebas] (una `IOException`: la
 * app la trata como «sin conexión», que es lo que esas pantallas ya sabían manejar). Lo que use
 * `HttpURLConnection` directo lo cierra el `ProxySelector` de `AppDePrueba`. La guarda que lo vigila
 * es `LasPruebasNoSalenALaRedTest`.
 */
object RedDeLaApp {
    @Volatile
    var cerradaPorLasPruebas: Boolean = false

    /** Los pedidos que la red cerrada frenó, para la guarda (un `getParecidos` se traga su error). */
    val frenados: java.util.concurrent.CopyOnWriteArrayList<String> = java.util.concurrent.CopyOnWriteArrayList()
}

/** El pedido que una prueba intentó mandar a la red. */
class LaRedEstaCerradaEnLasPruebas(url: String) :
    IOException("Una prueba intentó salir a la red ($url). Pon un sustituto: Repositories.sustitutoDePrueba, Uso.enviadorDePrueba…")

private val HOSTS_LOCALES = setOf("127.0.0.1", "localhost", "::1", "[::1]")

private val RedCerradaEnLasPruebas = createClientPlugin("RedCerradaEnLasPruebas") {
    onRequest { request, _ ->
        // Lo local (un server de prueba en 127.0.0.1) no es «la red»: esas pruebas lo levantan a propósito.
        if (RedDeLaApp.cerradaPorLasPruebas && request.url.host !in HOSTS_LOCALES) {
            val url = request.url.buildString()
            RedDeLaApp.frenados += url
            throw LaRedEstaCerradaEnLasPruebas(url)
        }
    }
}

actual fun createHttpClient(): HttpClient = HttpClient(Android) {
    // Vuelta 100: en las pruebas no sale nada a la red (ver [RedDeLaApp]). Primero de todo, para que
    // ningún otro plugin alcance a armar el pedido.
    install(RedCerradaEnLasPruebas)
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
            // Vuelta 100: una escritura que el server rechazó queda en el uso, con su código.
            anotarLaRespuestaParaElUso(response.status.value, response.call.request.method.value, response.call.request.url)
        }
        handleResponseExceptionWithRequest { cause, request ->
            // Network errors (no connectivity) — keep session alive, work offline
            if (cause is IOException) {
                anotarElErrorDeRedParaElUso(request.method.value, request.url)
                return@handleResponseExceptionWithRequest
            }
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
        // Vuelta 100: en la línea de tiempo del uso, cuándo el teléfono subió lo que tenía guardado.
        com.jvillada.movi.observabilidad.Uso.evento(
            com.jvillada.movi.shared.model.EventoDeUso.SINCRONIZACION,
            mapOf(com.jvillada.movi.shared.model.ClaveDeUso.RESULTADO to "OK"),
        )
        olvidarLoFresco()
        // Vuelta 82: lo que el héroe del Inicio adelantaba puede haber subido: que vuelva a mirar ya.
        com.jvillada.movi.ui.dashboard.MovimientosDelDueno.despertar()
    }).also { it.start() }
    return LocalRepository(db, remote, userId, sincronizador = sincronizador)
}

// No-op: no hay overlay HTML que recuperar acá. Compose navega solo al ver
// SessionManager.loggedIn == false (ver App.kt).
actual fun reloadForLogout() {}
