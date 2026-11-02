package com.jvillada.movi.fallos

import com.jvillada.movi.data.apiBaseUrl
import com.jvillada.movi.data.createHttpClient
import com.jvillada.movi.observabilidad.InfoDeLaApp
import com.jvillada.movi.shared.model.FalloDeLaApp
import com.jvillada.movi.shared.model.falloDe
import com.jvillada.movi.shared.model.newId
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.datetime.Clock
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import platform.Foundation.NSBundle
import platform.Foundation.NSThread
import platform.Foundation.NSUserDefaults
import platform.UIKit.UIDevice
import kotlin.experimental.ExperimentalNativeApi

/**
 * # Los fallos de iOS (vuelta 100)
 *
 * Kotlin/Native tiene un gancho razonable: `setUnhandledExceptionHook`, que corre con la excepción de
 * Kotlin que nadie atrapó **antes** de que el proceso termine. Ahí se guarda el reporte (limpio, como
 * en Android: `falloDe` de `:core`) en `NSUserDefaults` —sincrónico, sin red: el proceso se está
 * muriendo— y se llama al gancho que había. Al próximo arranque, [subirLosFallosGuardados] los manda a
 * `POST /api/fallos` y los borra.
 *
 * **Lo que no alcanza**: un fallo de Swift o de Objective-C (un `EXC_BAD_ACCESS`, una excepción de
 * UIKit) no pasa por acá; eso lo vería solo un reportero nativo de señales, que es justo lo que traen
 * Crashlytics o Sentry, y esta vuelta no usa terceros. La app es Compose: casi todo lo que puede fallar
 * es Kotlin.
 */
private const val CLAVE = "movi_fallos_pendientes"
private const val MAX_GUARDADOS = 10
private val json = Json { ignoreUnknownKeys = true }
private val lista = ListSerializer(FalloDeLaApp.serializer())

@OptIn(ExperimentalNativeApi::class)
fun instalarLaCapturaDeFallos() {
    runCatching {
        InfoDeLaApp.version = (NSBundle.mainBundle.objectForInfoDictionaryKey("CFBundleShortVersionString") as? String).orEmpty()
        InfoDeLaApp.modelo = UIDevice.currentDevice.model
        InfoDeLaApp.sistema = "iOS ${UIDevice.currentDevice.systemVersion}"
    }
    var previo: ((Throwable) -> Unit)? = null
    previo = setUnhandledExceptionHook { error ->
        runCatching {
            val hilo = if (NSThread.isMainThread) "main" else "otro"
            guardar(
                falloDe(
                    error, id = newId("fallo"), plataforma = "ios", version = InfoDeLaApp.version, hilo = hilo,
                    modelo = InfoDeLaApp.modelo, sistema = InfoDeLaApp.sistema, ahora = Clock.System.now().toEpochMilliseconds(),
                ),
            )
        }
        previo?.invoke(error)
    }
}

private fun leer(): List<FalloDeLaApp> = runCatching {
    NSUserDefaults.standardUserDefaults.stringForKey(CLAVE)?.let { json.decodeFromString(lista, it) }
}.getOrNull().orEmpty()

private fun escribir(fallos: List<FalloDeLaApp>) {
    val d = NSUserDefaults.standardUserDefaults
    if (fallos.isEmpty()) d.removeObjectForKey(CLAVE) else d.setObject(json.encodeToString(lista, fallos.takeLast(MAX_GUARDADOS)), CLAVE)
}

private fun guardar(fallo: FalloDeLaApp) = escribir(leer() + fallo)

/** Sube lo guardado; lo que no sube (sin red, un 5xx, el límite) queda para el próximo arranque. */
suspend fun subirLosFallosGuardados() {
    val pendientes = leer()
    if (pendientes.isEmpty()) return
    val cliente = createHttpClient()
    try {
        val quedan = pendientes.filter { fallo ->
            val codigo = runCatching {
                cliente.post("$apiBaseUrl/api/fallos") {
                    contentType(ContentType.Application.Json)
                    setBody(fallo)
                }.status.value
            }.getOrDefault(0)
            codigo == 0 || codigo == 404 || codigo == 429 || codigo >= 500
        }
        escribir(quedan)
    } finally {
        cliente.close()
    }
}
