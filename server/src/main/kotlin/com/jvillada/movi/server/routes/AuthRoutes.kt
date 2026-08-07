package com.jvillada.movi.server.routes

import at.favre.lib.crypto.bcrypt.BCrypt
import com.jvillada.movi.server.auth.JwtConfig
import com.jvillada.movi.server.auth.PasswordReset
import com.jvillada.movi.server.auth.PasswordResetConfig
import com.jvillada.movi.server.auth.PasswordResetMailer
import com.jvillada.movi.server.auth.RateLimiter
import com.jvillada.movi.server.db.PasswordResetTokens
import com.jvillada.movi.server.db.Users
import com.jvillada.movi.server.db.dbQuery
import com.jvillada.movi.shared.model.AuthResponse
import com.jvillada.movi.shared.model.LoginRequest
import com.jvillada.movi.shared.model.PasswordPolicy
import com.jvillada.movi.shared.model.PasswordResetConfirmRequest
import com.jvillada.movi.shared.model.PasswordResetRequest
import com.jvillada.movi.shared.model.RegisterRequest
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.log
import io.ktor.server.plugins.origin
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update

/** Respuesta única del pedido de reset. Idéntica exista o no el correo — es el punto. */
private const val RESET_REQUEST_ACK =
    "Si el correo está registrado, te enviamos un enlace para restablecer la contraseña. " +
        "Revisá tu bandeja; el enlace vence en 1 hora."

/** Respuesta única de un confirm que no prospera: no distingue inexistente de usado de vencido. */
private const val RESET_TOKEN_REJECTED =
    "El enlace no es válido, ya se usó o venció. Pedí uno nuevo desde \"¿Olvidaste tu contraseña?\"."

fun Route.authRoutes() {
    route("/api/auth") {

        post("/register") {
            val ip = call.request.origin.remoteHost
            if (!RateLimiter.allow(ip, maxAttempts = 10, windowMs = 5 * 60_000L)) {
                return@post call.respond(HttpStatusCode.TooManyRequests, "Demasiados intentos, esperá unos minutos")
            }
            val req = call.receive<RegisterRequest>()
            if (req.email.isBlank() || req.name.isBlank()) {
                return@post call.respond(HttpStatusCode.BadRequest, "Nombre y correo requeridos")
            }
            // La política vive en :core (PasswordPolicy) para que servidor y UI no puedan
            // divergir. Acá está la validación AUTORITATIVA: la del cliente es cortesía.
            PasswordPolicy.problemWith(req.password)?.let { problem ->
                return@post call.respond(HttpStatusCode.BadRequest, PasswordPolicy.messageFor(problem))
            }

            val emailTaken = dbQuery {
                Users.selectAll().where { Users.email eq req.email.lowercase().trim() }.count() > 0
            }
            if (emailTaken) return@post call.respond(HttpStatusCode.Conflict, "Email already registered")

            val userId  = "usr_${java.util.UUID.randomUUID()}"
            val email   = req.email.lowercase().trim()
            val name    = req.name.trim()
            val hash    = BCrypt.withDefaults().hashToString(12, req.password.toCharArray())

            dbQuery {
                Users.insert {
                    it[id]           = userId
                    it[Users.email]  = email
                    it[Users.name]   = name
                    it[passwordHash] = hash
                }
            }

            val token = JwtConfig.makeToken(userId, email)
            call.respond(HttpStatusCode.Created, AuthResponse(token, userId, name, email))
        }

        post("/login") {
            val ip = call.request.origin.remoteHost
            if (!RateLimiter.allow(ip, maxAttempts = 10, windowMs = 5 * 60_000L)) {
                return@post call.respond(HttpStatusCode.TooManyRequests, "Demasiados intentos, esperá unos minutos")
            }
            val req = call.receive<LoginRequest>()

            // OJO: acá NO se aplica PasswordPolicy, y es a propósito. El piso de longitud solo
            // puede exigirse cuando tenemos la contraseña en claro para guardarla: registro y
            // cambio de contraseña. De las cuentas existentes solo tenemos el hash BCrypt, que
            // no se puede re-evaluar contra el piso nuevo. Validar acá dejaría afuera de sus
            // propias finanzas a quien tenga una contraseña anterior al cambio — un apagón
            // autoinfligido. Quien quiera subir de nivel lo hace por el flujo de recuperación.
            val row = dbQuery {
                Users.selectAll()
                    .where { Users.email eq req.email.lowercase().trim() }
                    .firstOrNull()
            } ?: return@post call.respond(HttpStatusCode.Unauthorized, "Invalid credentials")

            val verified = BCrypt.verifyer()
                .verify(req.password.toCharArray(), row[Users.passwordHash]).verified
            if (!verified) return@post call.respond(HttpStatusCode.Unauthorized, "Invalid credentials")

            val token = JwtConfig.makeToken(row[Users.id], row[Users.email])
            call.respond(AuthResponse(token, row[Users.id], row[Users.name], row[Users.email]))
        }

        // ── Recuperación de contraseña ────────────────────────────────────────
        //
        // LIMITACIÓN CONOCIDA Y NO RESUELTA — leer antes de asumir que un reset "echa" a nadie:
        // las sesiones de movi son JWT sin estado, de 30 días y SIN revocación. No hay lista de
        // tokens vivos ni versión de credencial en el token, así que **restablecer la contraseña
        // NO invalida los JWT que un atacante ya tenga**: si alguien se llevó un token, sigue
        // entrando hasta que ese JWT venza por su cuenta (hasta 30 días después). El reset cierra
        // la puerta de la contraseña, no las sesiones ya abiertas. Arreglarlo de verdad exige
        // revocación (tabla de sesiones, o un `passwordChangedAt` en el usuario chequeado en cada
        // request contra un claim `iat`), que es un cambio mucho más grande que este y que además
        // toca el JWT. Está fuera de alcance a propósito, no olvidado.

        post("/password-reset/request") {
            val startedAt = System.currentTimeMillis()

            val ip = call.request.origin.remoteHost
            if (!RateLimiter.allow("pwreset-req:$ip", maxAttempts = 5, windowMs = 15 * 60_000L)) {
                return@post call.respond(HttpStatusCode.TooManyRequests, "Demasiados pedidos, esperá unos minutos")
            }
            val req = call.receive<PasswordResetRequest>()
            val email = req.email.lowercase().trim()

            // Sin clave de Resend no hay forma de que llegue ningún correo. Se decide ANTES de
            // mirar la base para que la respuesta no dependa en nada del correo pedido: es una
            // condición del servidor, idéntica para cualquier dirección, así que no enumera.
            // Y se contesta con la verdad en vez del 202 genérico: mandar a alguien a esperar
            // un correo que no existe, en el único camino que tiene para recuperar el acceso,
            // es peor que decirle que el servidor no puede ayudarlo ahora.
            val apiKey = PasswordResetConfig.resendApiKey()
            if (apiKey.isNullOrBlank()) {
                call.application.log.error(
                    "password-reset: RESEND_API_KEY no está configurada — la recuperación por " +
                        "correo está APAGADA y se está respondiendo 503 a quien la pida.",
                )
                return@post call.respond(
                    HttpStatusCode.ServiceUnavailable,
                    "El envío de correo no está configurado en este servidor, así que no se puede " +
                        "enviar el enlace de recuperación. Contactá a quien administra movi.",
                )
            }

            val user = dbQuery {
                Users.selectAll().where { Users.email eq email }
                    .firstOrNull()
                    ?.let { it[Users.id] to it[Users.email] }
            }

            // El token se genera SIEMPRE, exista o no la cuenta: el trabajo criptográfico no
            // puede depender de la respuesta que queremos ocultar.
            val token = PasswordReset.generateToken()
            val tokenHash = PasswordReset.hashToken(token)
            val now = System.currentTimeMillis()

            if (user != null) {
                val (userId, userEmail) = user
                dbQuery {
                    // Pedir uno nuevo invalida los anteriores: como mucho hay un enlace vivo.
                    PasswordResetTokens.update({
                        (PasswordResetTokens.userId eq userId) and (PasswordResetTokens.usedAt.isNull())
                    }) { it[usedAt] = now }

                    PasswordResetTokens.insert {
                        it[id]        = "prt_${java.util.UUID.randomUUID()}"
                        it[PasswordResetTokens.userId] = userId
                        it[PasswordResetTokens.tokenHash] = tokenHash
                        it[createdAt] = now
                        it[expiresAt] = PasswordReset.expiryFor(now)
                    }
                }

                val link = "${PasswordResetConfig.appBaseUrl()}/?reset=$token"
                val from = PasswordResetConfig.from()
                // El envío sale del camino de la petición a propósito: esperar el round-trip a
                // Resend haría que un correo registrado tarde cientos de ms más que uno
                // desconocido, y esa diferencia ES el oráculo de enumeración.
                call.application.launch {
                    val ok = runCatching { PasswordResetMailer.sendResetLink(userEmail, link, apiKey, from) }
                        .getOrElse { e ->
                            call.application.log.error("password-reset: excepción enviando el correo: ${e.message}", e)
                            false
                        }
                    // Si el envío falla igual respondimos 202: el token ya existe y no se puede
                    // desdecir sin abrir el mismo canal que estamos cerrando (un 502 solo para
                    // correos registrados sería un oráculo perfecto). Queda en los logs.
                    if (!ok) call.application.log.error("password-reset: Resend rechazó el envío para un usuario")
                }
            }

            // Piso de tiempo: iguala el camino "registrado" (que escribe en la base) con el
            // camino "desconocido" (que no escribe nada). Ver PasswordReset.REQUEST_FLOOR_MS.
            PasswordReset.remainingFloorMs(System.currentTimeMillis() - startedAt)
                .takeIf { it > 0 }?.let { delay(it) }

            call.respond(HttpStatusCode.Accepted, RESET_REQUEST_ACK)
        }

        post("/password-reset/confirm") {
            val ip = call.request.origin.remoteHost
            if (!RateLimiter.allow("pwreset-confirm:$ip", maxAttempts = 10, windowMs = 15 * 60_000L)) {
                return@post call.respond(HttpStatusCode.TooManyRequests, "Demasiados intentos, esperá unos minutos")
            }
            val req = call.receive<PasswordResetConfirmRequest>()

            // La política se valida ANTES que el token, a propósito: al revés, un "contraseña
            // muy corta" contra un token inventado confirmaría que ese token era válido.
            // Además así una contraseña rechazada no consume el token.
            PasswordPolicy.problemWith(req.newPassword)?.let { problem ->
                return@post call.respond(HttpStatusCode.BadRequest, PasswordPolicy.messageFor(problem))
            }

            val now = System.currentTimeMillis()
            // Se busca por hash: nunca se compara el secreto byte a byte, así que no hay
            // oráculo de temporización en la verificación del token.
            val hash = PasswordReset.hashToken(req.token)
            val row = dbQuery {
                PasswordResetTokens.selectAll()
                    .where { PasswordResetTokens.tokenHash eq hash }
                    .firstOrNull()
            }

            // Falla cerrado: inexistente, ya usado o vencido dan exactamente la misma respuesta.
            if (row == null ||
                row[PasswordResetTokens.usedAt] != null ||
                PasswordReset.isExpired(row[PasswordResetTokens.expiresAt], now)
            ) {
                return@post call.respond(HttpStatusCode.BadRequest, RESET_TOKEN_REJECTED)
            }

            val userId = row[PasswordResetTokens.userId]
            val newHash = BCrypt.withDefaults().hashToString(12, req.newPassword.toCharArray())

            dbQuery {
                Users.update({ Users.id eq userId }) { it[passwordHash] = newHash }
                // Consume el token usado y, de paso, cualquier otro pendiente del mismo usuario:
                // si pidió tres enlaces, canjear uno mata los otros dos.
                PasswordResetTokens.update({
                    (PasswordResetTokens.userId eq userId) and (PasswordResetTokens.usedAt.isNull())
                }) { it[usedAt] = now }
            }

            call.application.log.info("password-reset: contraseña restablecida para $userId")
            call.respond(HttpStatusCode.OK, "Listo, tu contraseña quedó actualizada. Ya podés entrar con la nueva.")
        }
    }
}
