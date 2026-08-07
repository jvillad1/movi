package com.jvillada.movi.server.auth

import java.io.File
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/**
 * Lógica pura del reset de contraseña por correo. Sin DB, sin Ktor: todo lo que acá se
 * equivoca falla en silencio (un token predecible sigue "funcionando"), así que vive separado
 * de las rutas para poder testearlo solo.
 *
 * Modelo de amenaza y decisiones:
 *
 * - **Entropía**: 32 bytes de [SecureRandom] = 256 bits. `java.util.Random` está sembrado con
 *   el reloj y es predecible; `UUID.randomUUID()` sí usa un CSPRNG pero solo tiene 122 bits
 *   útiles y su formato invita a asumir que es un identificador, no un secreto. Acá el token
 *   ES el secreto, así que se genera explícitamente con [SecureRandom].
 *
 * - **Solo se guarda el hash**. En la tabla vive `sha256(token)`, nunca el token. Quien se
 *   lleve un dump de la base no puede canjear ningún reset: tendría que invertir SHA-256.
 *
 * - **SHA-256 y no BCrypt** para el token: BCrypt (lento a propósito) sirve contra secretos de
 *   baja entropía elegidos por personas. Un token de 256 bits aleatorios no se adivina por
 *   fuerza bruta ni con un hash rápido, y usar un KDF lento acá solo agregaría una palanca de
 *   DoS en un endpoint público. La lentitud no compra nada cuando el secreto ya es aleatorio.
 *
 * - **Búsqueda por hash, no comparación**: el confirm hashea el token presentado y busca esa
 *   fila por índice único. No hay comparación byte a byte de secretos, así que no hay oráculo
 *   de temporización en la verificación.
 */
object PasswordReset {

    /** 32 bytes = 256 bits. */
    const val TOKEN_BYTES = 32

    /** Una hora. Suficiente para ir al correo y volver; corto para que un enlace filtrado envejezca rápido. */
    const val TTL_MS = 60L * 60 * 1000

    /**
     * Piso de tiempo de respuesta del endpoint de pedido, en ms.
     *
     * El camino "correo registrado" hace trabajo que el camino "correo desconocido" no hace
     * (invalidar tokens viejos + insertar el nuevo). Aunque el envío del correo ya sale del
     * camino de la petición, esa diferencia de DB es medible con suficientes muestras. Igualar
     * ambos caminos a un piso fijo la esconde sin depender de que nadie, en el futuro, note
     * que agregar una consulta acá reabre el canal.
     */
    const val REQUEST_FLOOR_MS = 250L

    private val secureRandom = SecureRandom()
    private val encoder: Base64.Encoder = Base64.getUrlEncoder().withoutPadding()

    /** Token de un solo uso, url-safe (viaja en el query string del enlace del correo). */
    fun generateToken(): String {
        val bytes = ByteArray(TOKEN_BYTES)
        secureRandom.nextBytes(bytes)
        return encoder.encodeToString(bytes)
    }

    /** `sha256(token)` en hex minúscula. Es lo ÚNICO que se persiste. */
    fun hashToken(token: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(token.toByteArray(Charsets.UTF_8))
            .joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }

    fun expiryFor(nowMs: Long): Long = nowMs + TTL_MS

    /** Falla cerrado: en el instante exacto del vencimiento el token YA no sirve. */
    fun isExpired(expiresAt: Long, nowMs: Long): Boolean = nowMs >= expiresAt

    /** Cuánto falta esperar para que la respuesta tarde al menos [floorMs]. Nunca negativo. */
    fun remainingFloorMs(elapsedMs: Long, floorMs: Long = REQUEST_FLOOR_MS): Long =
        (floorMs - elapsedMs).coerceAtLeast(0)
}

/**
 * Configuración del reset. Resolución: system property primero (tests), luego env /
 * server/.env / .env — mismo orden e idéntica forma que [com.jvillada.movi.server.push.VapidConfig].
 * Sí, es la cuarta copia de `readEnv` en el módulo; consolidar las cuatro es deuda
 * pre-existente y sacarla acá haría este cambio más grande de lo que debería ser.
 */
object PasswordResetConfig {

    /** `null` cuando el envío de correo no está configurado — el endpoint responde 503. */
    fun resendApiKey(): String? = resolve("movi.resend.apiKey", "RESEND_API_KEY")

    fun from(): String = resolve("movi.reminder.from", "REMINDER_FROM") ?: "movi <reminders@movi.app>"

    /**
     * Base del enlace que se manda por correo. Es la URL de la PWA (que sirve el mismo deploy
     * que la API), no el `apiBaseUrl` del cliente.
     */
    fun appBaseUrl(): String =
        (resolve("movi.app.baseUrl", "APP_BASE_URL") ?: "https://movi-project-production.up.railway.app")
            .trimEnd('/')

    private fun resolve(prop: String, envKey: String): String? {
        System.getProperty(prop)?.takeIf { it.isNotBlank() }?.let { return it }
        System.getenv(envKey)?.takeIf { it.isNotBlank() }?.let { return it }
        val files = listOf(
            File(System.getProperty("user.dir"), "server/.env"),
            File(System.getProperty("user.dir"), ".env"),
        )
        return files.firstNotNullOfOrNull { f ->
            if (!f.exists()) null
            else f.readLines().firstOrNull { it.startsWith("$envKey=") }
                ?.substringAfter("=")?.trim()?.takeIf { it.isNotBlank() }
        }
    }
}
