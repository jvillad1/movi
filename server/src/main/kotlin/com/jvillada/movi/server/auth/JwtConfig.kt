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
}
