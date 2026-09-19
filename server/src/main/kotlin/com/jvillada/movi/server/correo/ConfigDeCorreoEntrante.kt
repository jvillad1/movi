package com.jvillada.movi.server.correo

import java.io.File
import java.security.MessageDigest
import java.util.Base64

/**
 * La configuración del correo entrante. Mismo orden de resolución que `VapidConfig` y
 * `ReminderConfig`: system property (para las pruebas), env var, `server/.env`, `.env`.
 *
 * **Sin `INBOUND_EMAIL_SECRET` la puerta no existe.** No hay default, no hay «modo abierto para
 * probar» y no hay secreto compilado: un webhook público que escriba en la bandeja de plata de
 * alguien no puede depender de que nadie se haya olvidado de configurar nada.
 */
object ConfigDeCorreoEntrante {

    /** El secreto compartido con el proveedor. `null` = el correo entrante está apagado. */
    fun secreto(): String? = resolver("movi.correo.secreto", "INBOUND_EMAIL_SECRET")

    /**
     * La dirección que dio el proveedor (`9f3c…@inbound.postmarkapp.com`, `alertas@midominio.com`).
     * Solo se usa para poder **decirle al dueño** a qué dirección reenviar; la ruta no la mira.
     */
    fun direccionBase(): String? = resolver("movi.correo.direccion", "INBOUND_EMAIL_ADDRESS")

    fun estaConfigurado(): Boolean = !secreto().isNullOrBlank()

    /**
     * **El balde del rate limit, que no puede ser el secreto.** `RateLimiter` guarda la clave en un
     * mapa en memoria hasta una hora; meter ahí el secreto lo deja disponible en un volcado de
     * memoria y en cualquier log futuro que imprima las claves. El hash lo identifica igual de bien
     * y no lo revela.
     */
    fun baldeDelSecreto(secreto: String): String = sha256Hex(secreto).take(16)

    private fun resolver(prop: String, envKey: String): String? {
        System.getProperty(prop)?.takeIf { it.isNotBlank() }?.let { return it }
        System.getenv(envKey)?.takeIf { it.isNotBlank() }?.let { return it }
        val archivos = listOf(
            File(System.getProperty("user.dir"), "server/.env"),
            File(System.getProperty("user.dir"), ".env"),
        )
        return archivos.firstNotNullOfOrNull { f ->
            if (!f.exists()) null
            else f.readLines().firstOrNull { it.startsWith("$envKey=") }
                ?.substringAfter("=")?.trim()?.takeIf { it.isNotBlank() }
        }
    }
}

/**
 * **El secreto que trae la petición**, buscado en los dos lugares donde un proveedor de correo lo
 * puede poner sin que nadie escriba código:
 *
 * 1. `Authorization: Basic …` — el usuario da igual, lo que cuenta es la contraseña. Es lo que
 *    permite configurar el webhook como una sola URL (`https://movi:SECRETO@…/api/correo-entrante`)
 *    en Postmark, en Mailgun y en cualquier otro.
 * 2. `X-Movi-Correo-Secreto: …`, para un proveedor que sí deje poner cabeceras.
 *
 * **Y no en la URL**, que sería lo más cómodo: `CallLogging` está instalado a nivel INFO
 * (`Monitoring.kt`) y escribe la ruta de cada petición, así que un secreto en el path terminaría
 * impreso en los logs de Railway en el primer correo que llegue. Las credenciales de `Authorization`
 * no las imprime nadie.
 */
fun secretoPresentado(authorization: String?, cabeceraPropia: String?): String? {
    cabeceraPropia?.trim()?.takeIf { it.isNotBlank() }?.let { return it }
    val basico = authorization?.trim()?.takeIf { it.startsWith("Basic ", ignoreCase = true) }
        ?.substringAfter(' ')?.trim() ?: return null
    val decodificado = runCatching { String(Base64.getDecoder().decode(basico)) }.getOrNull() ?: return null
    return decodificado.substringAfter(':', missingDelimiterValue = "").takeIf { it.isNotBlank() }
}

/**
 * Comparación de tiempo constante. Con un `==` normal, el tiempo que tarda en fallar dice cuántos
 * caracteres del secreto acertaste; el rate limit lo hace muy difícil de explotar, pero esto no
 * cuesta nada y no hay que volver a pensarlo.
 */
fun esElMismoSecreto(presentado: String, esperado: String): Boolean =
    MessageDigest.isEqual(presentado.toByteArray(), esperado.toByteArray())
