package com.jvillada.movi.fallos

import android.content.Context
import android.os.Build
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
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException

/**
 * # Los fallos de Android (vuelta 100)
 *
 * El teléfono del papá (Samsung, APK 1.87) mostró «Movi continúa fallando» con la app cerrada, y no
 * había forma de saber por qué. Ahora:
 *
 * 1. **Al caerse** ([instalar], desde `MoviAplicacion`): un `Thread.setDefaultUncaughtExceptionHandler`
 *    escribe el fallo en disco —sincrónico, en `filesDir/fallos/<id>.json`— y **deja seguir al handler
 *    que había** (el del sistema, que muestra el aviso y mata el proceso). Nada de red en ese momento:
 *    el proceso se está muriendo.
 * 2. **Al próximo arranque** (o apenas haya red): `SubirFallosWorker` sube cada archivo a
 *    `POST /api/fallos` con [subirLosFallosGuardados] y lo borra. El id del archivo es el del reporte,
 *    así que subirlo dos veces se guarda una (el server es idempotente).
 * 3. **Lo que un Worker atrapa y sigue** ([reportarAtrapado]): el mismo reporte con `atrapado = true`
 *    y el componente que lo atrapó, sin cambiar lo que hace el Worker. Un error de red (`IOException`)
 *    no se reporta: sin señal es lo normal, no un defecto.
 *
 * Lo que se guarda: tipo, mensaje y stack recortados y limpios (`falloDe` en `:core`: sin números de
 * 5+ cifras, correos ni lo citado), hilo, versión, modelo, Android, hora y componente. Se manda
 * **siempre**, aunque el interruptor de uso esté apagado: no lleva datos del usuario.
 */
object CapturaDeFallos {

    /** Como mucho estos archivos esperando: una racha de caídas no llena el teléfono. */
    internal const val MAX_GUARDADOS = 20

    private val json = Json { ignoreUnknownKeys = true }

    /** La carpeta de los fallos por subir. */
    fun carpeta(context: Context): File = File(context.filesDir, "fallos")

    /** Llena [InfoDeLaApp] con lo de este aparato. */
    fun conocerElAparato(context: Context) {
        InfoDeLaApp.version = runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull().orEmpty()
        InfoDeLaApp.modelo = "${Build.MANUFACTURER} ${Build.MODEL}".trim()
        InfoDeLaApp.sistema = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"
    }

    /**
     * Instala el handler. [previo] es el que había (el del sistema); se llama siempre después de
     * guardar, pase lo que pase al guardar.
     */
    fun instalar(dir: File, previo: Thread.UncaughtExceptionHandler? = Thread.getDefaultUncaughtExceptionHandler()) {
        Thread.setDefaultUncaughtExceptionHandler { hilo, error ->
            try {
                guardar(dir, armar(error, hilo.name, atrapado = false))
            } catch (_: Throwable) {
                // Guardar no puede impedir que el sistema haga lo suyo.
            } finally {
                previo?.uncaughtException(hilo, error)
            }
        }
    }

    /** El reporte de [error], limpio. */
    fun armar(error: Throwable, hilo: String, atrapado: Boolean, componente: String = ""): FalloDeLaApp = falloDe(
        error = error,
        id = newId("fallo"),
        plataforma = "android",
        version = InfoDeLaApp.version,
        hilo = hilo,
        modelo = InfoDeLaApp.modelo,
        sistema = InfoDeLaApp.sistema,
        ahora = System.currentTimeMillis(),
        atrapado = atrapado,
        componente = componente,
    )

    /** Lo escribe en disco (sincrónico). Si ya hay [MAX_GUARDADOS], se va el más viejo. */
    fun guardar(dir: File, fallo: FalloDeLaApp) {
        dir.mkdirs()
        val viejos = guardados(dir)
        if (viejos.size >= MAX_GUARDADOS) viejos.take(viejos.size - MAX_GUARDADOS + 1).forEach { it.delete() }
        File(dir, "${fallo.id}.json").writeText(json.encodeToString(FalloDeLaApp.serializer(), fallo))
    }

    /** Los archivos por subir, el más viejo primero. */
    fun guardados(dir: File): List<File> =
        dir.listFiles { f -> f.isFile && f.name.endsWith(".json") }?.sortedBy { it.lastModified() }.orEmpty()

    /** ¿Este error atrapado va al reporte? Un error de red no: sin señal es lo normal. */
    fun vaAlReporte(error: Throwable): Boolean = error !is IOException && error !is CancellationException

    /**
     * Un Worker atrapó [error] y siguió: queda en disco para la próxima subida, con [componente] (el
     * nombre del Worker). No cambia nada de lo que el Worker hace después. Nunca lanza.
     */
    fun reportarAtrapado(context: Context, error: Throwable, componente: String) {
        if (!vaAlReporte(error)) return
        runCatching {
            guardar(carpeta(context), armar(error, Thread.currentThread().name, atrapado = true, componente = componente))
            alGuardarAtrapado?.invoke(context)
        }
    }

    /** Lo engancha `MoviAplicacion`: encolar la subida apenas un Worker reporta algo. */
    var alGuardarAtrapado: ((Context) -> Unit)? = null

    /** Lo que pasó al intentar subir un reporte. */
    enum class Envio { SUBIDO, RECHAZADO, REINTENTAR }

    /**
     * **Sube y borra lo guardado.** Lo [Envio.SUBIDO] y lo [Envio.RECHAZADO] (un 4xx que no es de
     * límite: reintentar no lo arregla) se borran; lo demás queda para la próxima. Un archivo que no
     * se puede leer se borra. Devuelve cuántos quedaron por subir.
     */
    suspend fun subirLosFallosGuardados(dir: File, enviar: suspend (FalloDeLaApp) -> Envio): Int {
        var quedan = 0
        for (archivo in guardados(dir)) {
            val fallo = runCatching { json.decodeFromString(FalloDeLaApp.serializer(), archivo.readText()) }.getOrNull()
            if (fallo == null) { archivo.delete(); continue }
            when (runCatching { enviar(fallo) }.getOrDefault(Envio.REINTENTAR)) {
                Envio.SUBIDO, Envio.RECHAZADO -> archivo.delete()
                Envio.REINTENTAR -> quedan++
            }
        }
        return quedan
    }

    /** El envío de verdad: `POST /api/fallos`, con el token si hay sesión (solo para atribuirlo). */
    suspend fun enviarAMovi(fallo: FalloDeLaApp): Envio {
        val cliente = createHttpClient()
        return try {
            val respuesta = cliente.post("$apiBaseUrl/api/fallos") {
                contentType(ContentType.Application.Json)
                setBody(fallo)
            }
            val codigo = respuesta.status.value
            when {
                codigo in 200..299 -> Envio.SUBIDO
                // Un 404 es un server que todavía no tiene la ruta (APK nuevo, server viejo): espera.
                codigo == 404 || codigo == 429 || codigo >= 500 -> Envio.REINTENTAR
                else -> Envio.RECHAZADO
            }
        } finally {
            cliente.close()
        }
    }
}
