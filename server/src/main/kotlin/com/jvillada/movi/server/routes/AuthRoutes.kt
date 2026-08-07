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

/**
 * Costo de BCrypt para todo hash nuevo. [DUMMY_PASSWORD_HASH] **debe** tener este mismo costo:
 * si alguien sube el costo acá y no regenera el señuelo, el camino "usuario inexistente" vuelve
 * a ser más barato que el camino "usuario existe" y el oráculo de enumeración se reabre.
 * Hay un test que lo fija.
 */
internal const val BCRYPT_COST = 12

/**
 * Hash BCrypt fijo (costo [BCRYPT_COST]) contra el que se verifica cuando el correo NO existe.
 *
 * No es la contraseña de nadie: es un hash generado una vez de una cadena que no se usa en
 * ningún lado. Su única función es que el camino "no existe ese correo" haga **exactamente el
 * mismo trabajo** que el camino "existe": ~300 ms de BCrypt. Antes el desconocido contestaba
 * 401 al instante y el conocido tardaba ~293 ms — un oráculo de enumeración de dos órdenes de
 * magnitud más grande que los 2 ms que el endpoint de reset gasta 250 ms en esconder.
 *
 * Se verifica de verdad (no se duerme): un `delay` fijo es distinguible del trabajo real bajo
 * carga, y además dejaría la diferencia intacta si el costo de BCrypt cambiara.
 */
internal const val DUMMY_PASSWORD_HASH = "\$2a\$12\$C8iu.kZWQHOr4prsbuGhI.mYX3n4VUM/KYh9xkqf9.TDiZG7WwqCK"

/** Respuesta única del login que no prospera. No distingue "no existe" de "contraseña mala". */
private const val INVALID_CREDENTIALS = "Invalid credentials"

// ── Rate limit: qué protege y qué NO ──────────────────────────────────────────────────────
//
// `call.request.origin.remoteHost` es el peer TCP. En `server/src/main` NO está instalado
// `ForwardedHeaders` ni `XForwardedHeaders`, así que detrás del borde de Railway ese peer es
// **una sola dirección para todo internet**. Una clave `…:$ip` NO es "por IP": es un balde
// GLOBAL. Los nombres de las claves de acá abajo lo dicen para que no se vuelva a leer como
// aislamiento por origen.
//
// **Por qué no se instaló XForwardedHeaders.** Ktor toma esa cabecera al pie de la letra: no
// tiene noción de proxies de confianza. Instalarlo haría que la clave del limitador la elija
// quien llama (`X-Forwarded-For: lo-que-sea`), y el limitador se saltearía con una línea de
// curl. Cambiar un DoS por un bypass trivial es peor que el DoS. Railway tampoco publica un
// rango fijo del borde contra el cual anclar la confianza, así que el peer TCP es lo único no
// falsificable disponible hoy. Si algún día hay un rango confiable, esto se revisita.
//
// **Lo que sí cambió acá:**
//  1. login y register dejaron de compartir balde (antes 10 logins fallidos apagaban también
//     el registro, y al revés).
//  2. se agregó un segundo balde por **correo normalizado** en login y en el pedido de reset.
//     Ese es el balde estricto y el que de verdad frena el ataque, que siempre apunta a una
//     cuenta. El global queda como techo de inundación, con un límite más alto para que una
//     sola persona reintentando no apague el servicio para todos.
//
// **Lo que esto NO protege — dicho explícitamente:**
//  · No hay aislamiento por origen, y no lo va a haber mientras la clave sea el peer TCP.
//    Atacante y usuaria legítima comparten el balde global; agotarlo sigue dando 429 a todos
//    hasta que corra la ventana. Lo que se compró es que ese techo esté mucho más arriba y que
//    agotarlo ya no sea el camino barato para bloquear la recuperación de contraseña.
//  · El balde por correo habilita un DoS **dirigido**: quemando intentos contra un correo
//    conocido se le bloquea a esa persona el login (5 min) o el pedido de reset (15 min). Es
//    el intercambio clásico del bloqueo por cuenta y se elige a conciencia — lo que había
//    antes era el mismo DoS pero contra TODAS las cuentas a la vez y con 5 pedidos.
//  · El balde por correo no enumera: la clave es la cadena que mandaron, exista o no la
//    cuenta, así que dos correos cualquiera se comportan igual.
private const val WINDOW_LOGIN_MS = 5 * 60_000L
private const val WINDOW_RESET_MS = 15 * 60_000L

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
            // Balde propio: antes register y login compartían la clave `$ip` y 10 logins
            // fallidos apagaban también el registro. Ver el bloque de arriba.
            val ip = call.request.origin.remoteHost
            if (!RateLimiter.allow("register:global:$ip", maxAttempts = 30, windowMs = WINDOW_LOGIN_MS)) {
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
            // OJO — ACÁ LA ENUMERACIÓN SIGUE ABIERTA, Y NO POR temporización: este 409 es una
            // respuesta DIRECTA ("ese correo ya tiene cuenta"). Cualquiera puede preguntar por
            // una dirección y enterarse. El registro es público por decisión explícita del
            // dueño y cerrar esto es una decisión de producto aparte (registro por invitación,
            // o un 202 genérico que obligue a confirmar por correo), así que se deja como está
            // — pero se deja DICHO. No se puede afirmar que movi no enumera cuentas: el flujo
            // de reset no enumera; este sí.
            if (emailTaken) return@post call.respond(HttpStatusCode.Conflict, "Email already registered")

            val userId  = "usr_${java.util.UUID.randomUUID()}"
            val email   = req.email.lowercase().trim()
            val name    = req.name.trim()
            val hash    = BCrypt.withDefaults().hashToString(BCRYPT_COST, req.password.toCharArray())

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
            if (!RateLimiter.allow("login:global:$ip", maxAttempts = 60, windowMs = WINDOW_LOGIN_MS)) {
                return@post call.respond(HttpStatusCode.TooManyRequests, "Demasiados intentos, esperá unos minutos")
            }
            val req = call.receive<LoginRequest>()
            val email = req.email.lowercase().trim()

            // Balde estricto por correo: el ataque de fuerza bruta apunta SIEMPRE a una cuenta,
            // y este es el único balde que en producción no es global. La clave es la cadena
            // pedida, exista o no la cuenta, así que no distingue registrado de desconocido.
            if (!RateLimiter.allow("login:email:$email", maxAttempts = 10, windowMs = WINDOW_LOGIN_MS)) {
                return@post call.respond(HttpStatusCode.TooManyRequests, "Demasiados intentos, esperá unos minutos")
            }

            // OJO: acá NO se aplica el PISO de PasswordPolicy, y es a propósito. El mínimo solo
            // puede exigirse cuando tenemos la contraseña en claro para guardarla: registro y
            // cambio de contraseña. De las cuentas existentes solo tenemos el hash BCrypt, que
            // no se puede re-evaluar contra el piso nuevo. Validar acá dejaría afuera de sus
            // propias finanzas a quien tenga una contraseña anterior al cambio — un apagón
            // autoinfligido. Quien quiera subir de nivel lo hace por el flujo de recuperación.
            //
            // El TECHO sí se aplica, y es otra cosa: `at.favre.lib:bcrypt` **lanza**
            // IllegalArgumentException por encima de 72 bytes UTF-8 (no trunca). Sin
            // StatusPages instalado eso sería un 500 que cualquiera sin autenticar puede
            // disparar mandando una contraseña larga. Se corta acá con el mismo 401 de siempre.
            // Va ANTES de mirar la base a propósito: depende solo de la contraseña, así que
            // ningún atacante puede usarlo para distinguir un correo registrado de uno que no
            // lo está (una contraseña de >72 bytes nunca podría ser la correcta: al registrar
            // no se pudo haber aceptado).
            if (PasswordPolicy.byteLength(req.password) > PasswordPolicy.MAX_BYTES) {
                return@post call.respond(HttpStatusCode.Unauthorized, INVALID_CREDENTIALS)
            }

            val row = dbQuery {
                Users.selectAll()
                    .where { Users.email eq email }
                    .firstOrNull()
            }

            if (row == null) {
                // Verificación señuelo: el camino "no existe" tiene que costar lo mismo que el
                // camino "existe pero la contraseña está mal". Sin esto, el 401 instantáneo del
                // desconocido contra los ~293 ms del registrado es un oráculo de enumeración
                // perfecto — más grande que el que el endpoint de reset gasta 250 ms en tapar.
                // Se hace trabajo real (no `delay`) contra un hash del mismo costo.
                BCrypt.verifyer().verify(req.password.toCharArray(), DUMMY_PASSWORD_HASH)
                return@post call.respond(HttpStatusCode.Unauthorized, INVALID_CREDENTIALS)
            }

            val verified = BCrypt.verifyer()
                .verify(req.password.toCharArray(), row[Users.passwordHash]).verified
            if (!verified) return@post call.respond(HttpStatusCode.Unauthorized, INVALID_CREDENTIALS)

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

            // Este era el peor caso del esquema viejo: `pwreset-req:$ip` con 5 pedidos por 15
            // minutos es, detrás del proxy, UN balde para todo internet — cinco pedidos de
            // cualquiera dejaban a todo el mundo sin recuperación durante 15 minutos. El techo
            // global sube y el límite estricto se mueve al correo, que es lo que hay que
            // proteger (no inundar la casilla de nadie). Ver el bloque de arriba.
            val ip = call.request.origin.remoteHost
            if (!RateLimiter.allow("pwreset-req:global:$ip", maxAttempts = 30, windowMs = WINDOW_RESET_MS)) {
                return@post call.respond(HttpStatusCode.TooManyRequests, "Demasiados pedidos, esperá unos minutos")
            }
            val req = call.receive<PasswordResetRequest>()
            val email = req.email.lowercase().trim()

            // El 429 depende solo de la cadena pedida, no de si existe la cuenta: un correo
            // desconocido consume su balde igual, así que esto tampoco enumera.
            if (!RateLimiter.allow("pwreset-req:email:$email", maxAttempts = 5, windowMs = WINDOW_RESET_MS)) {
                return@post call.respond(HttpStatusCode.TooManyRequests, "Demasiados pedidos, esperá unos minutos")
            }

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
            // Sin balde por correo: el confirm no lleva correo, y keyearlo por token sería
            // inútil (cada intento trae un token distinto). Queda solo el techo global, subido
            // por la misma razón que los otros: en producción es un balde para todo el mundo.
            // Lo que de verdad protege este endpoint es la entropía del token, no el limitador.
            if (!RateLimiter.allow("pwreset-confirm:global:$ip", maxAttempts = 60, windowMs = WINDOW_RESET_MS)) {
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
            val newHash = BCrypt.withDefaults().hashToString(BCRYPT_COST, req.newPassword.toCharArray())

            val updatedRows = dbQuery {
                val n = Users.update({ Users.id eq userId }) { it[passwordHash] = newHash }
                // Consume el token usado y, de paso, cualquier otro pendiente del mismo usuario:
                // si pidió tres enlaces, canjear uno mata los otros dos. Se sella incluso si el
                // usuario ya no está: un token huérfano no puede prosperar nunca.
                PasswordResetTokens.update({
                    (PasswordResetTokens.userId eq userId) and (PasswordResetTokens.usedAt.isNull())
                }) { it[usedAt] = now }
                n
            }

            // Falla cerrado. Si la fila del usuario ya no existe (cuenta borrada entre el pedido
            // y el canje), el UPDATE toca CERO filas: nada cambió, y contestar "tu contraseña
            // quedó actualizada" sería mentir en el único flujo donde la persona no tiene otra
            // forma de verificarlo. Se contesta el mismo rechazo genérico que un token inválido
            // —no hace falta un mensaje nuevo, y así tampoco revela que el token era bueno.
            if (updatedRows == 0) {
                call.application.log.error(
                    "password-reset: token válido para $userId pero el UPDATE tocó 0 filas " +
                        "(¿el usuario ya no existe?). Se responde rechazo, NO éxito.",
                )
                return@post call.respond(HttpStatusCode.BadRequest, RESET_TOKEN_REJECTED)
            }

            call.application.log.info("password-reset: contraseña restablecida para $userId")
            call.respond(HttpStatusCode.OK, "Listo, tu contraseña quedó actualizada. Ya podés entrar con la nueva.")
        }
    }
}
