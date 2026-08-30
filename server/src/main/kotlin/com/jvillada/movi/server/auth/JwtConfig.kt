package com.jvillada.movi.server.auth

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import java.util.Date

object JwtConfig {
    /** Pure, testable secret resolution: env var wins, then .env file, else fail fast. */
    fun resolveSecret(env: String?, fromFile: String?): String {
        val candidate = env?.takeIf { it.isNotBlank() }
            ?: fromFile?.takeIf { it.isNotBlank() }
        return candidate
            ?: error("JWT_SECRET not set — refusing to start with an insecure default. Set the JWT_SECRET env var.")
    }

    private val secret: String by lazy {
        // ESCOTILLA SOLO PARA TESTS. La system property va primero para que los tests puedan
        // fijar un secreto sin depender de que exista un server/.env en el checkout (mismo
        // idioma que VapidConfig). No cambia nada del token en sí: algoritmo, claims y validez
        // siguen igual.
        //
        // Que vaya ANTES de la variable de entorno es deliberado pero conviene entender qué
        // implica: quien pueda agregar un `-Dmovi.jwt.secret=…` a la línea de arranque le gana
        // a `JWT_SECRET` y firma tokens válidos para cualquier usuario. Eso NO es una escalada
        // real —quien controla los argumentos del proceso ya controla el proceso entero— y en
        // producción no se pasa ningún `-D`: Railway arranca el fat JAR sin propiedades y el
        // secreto sale de la variable de entorno. Si algún día el arranque pasa a componerse
        // desde una plantilla o un script con argumentos de terceros, invertir este orden.
        System.getProperty("movi.jwt.secret")?.takeIf { it.isNotBlank() }
            ?: resolveSecret(System.getenv("JWT_SECRET"), readFromEnvFile("JWT_SECRET"))
    }

    private fun readFromEnvFile(key: String): String? {
        val files = listOf(
            java.io.File(System.getProperty("user.dir"), "server/.env"),
            java.io.File(System.getProperty("user.dir"), ".env"),
        )
        return files.firstNotNullOfOrNull { f ->
            if (!f.exists()) null
            else f.readLines().firstOrNull { it.startsWith("$key=") }?.substringAfter("=")?.trim()
        }
    }

    private val algorithm: Algorithm by lazy { Algorithm.HMAC256(secret) }
    private const val ISSUER = "movi"
    private const val AUDIENCE = "movi-client"
    private const val VALIDITY_MS = 30L * 24 * 60 * 60 * 1000 // 30 days

    fun makeToken(userId: String, email: String): String = JWT.create()
        .withIssuer(ISSUER)
        .withAudience(AUDIENCE)
        .withClaim("userId", userId)
        .withClaim("email", email)
        .withExpiresAt(Date(System.currentTimeMillis() + VALIDITY_MS))
        .sign(algorithm)

    fun verifier() = JWT.require(algorithm)
        .withIssuer(ISSUER)
        .withAudience(AUDIENCE)
        .build()!!

    // ── Descarga de un documento ───────────────────────────────────────────────────

    /**
     * Audiencia distinta a propósito: un token de descarga **no** sirve para llamar a la API.
     * Si compartieran audiencia, el que se filtra por una URL abriría la cuenta entera.
     */
    private const val DOWNLOAD_AUDIENCE = "movi-download"

    /**
     * Cinco minutos. Es una URL que va a quedar en el historial del navegador, en los logs del
     * proxy y en cualquier `Referer`: tiene que servir para abrir el archivo una vez y dejar de
     * servir enseguida.
     */
    const val DOWNLOAD_VALIDITY_MS = 5L * 60 * 1000

    /**
     * Un permiso para descargar **un** documento, del **dueño** que lo pidió, por poco tiempo.
     *
     * Existe porque abrir un archivo desde el navegador es una navegación del navegador —una
     * pestaña nueva, el visor de PDF del sistema— y ahí no hay dónde poner `Authorization`. La
     * alternativa conocida es mandar el token de sesión en la URL, y ese dura **30 días**.
     */
    fun makeDownloadToken(userId: String, documentId: String): String = JWT.create()
        .withIssuer(ISSUER)
        .withAudience(DOWNLOAD_AUDIENCE)
        .withClaim("userId", userId)
        .withClaim("documentId", documentId)
        .withExpiresAt(Date(System.currentTimeMillis() + DOWNLOAD_VALIDITY_MS))
        .sign(algorithm)

    /**
     * Devuelve el `userId` si el token es válido **para este documento**, o `null`.
     *
     * Comprueba las dos cosas por separado, y las dos importan: la audiencia (que no sea un token
     * de sesión reusado como enlace) y que el `documentId` del token sea el que se está pidiendo
     * (que un enlace a la nómina de julio no abra la escritura del apartamento).
     */
    fun verifyDownloadToken(token: String, documentId: String): String? = try {
        val payload = JWT.require(algorithm)
            .withIssuer(ISSUER)
            .withAudience(DOWNLOAD_AUDIENCE)
            .withClaim("documentId", documentId)
            .build()
            .verify(token)
        payload.getClaim("userId").asString()
    } catch (e: Exception) {
        null
    }
}
