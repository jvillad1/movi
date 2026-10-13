package com.jvillada.movi.server.routes

import at.favre.lib.crypto.bcrypt.BCrypt
import com.jvillada.movi.server.auth.RateLimiter
import com.jvillada.movi.server.cuenta.borrarTodoLoDe
import com.jvillada.movi.server.db.Users
import com.jvillada.movi.server.db.dbQuery
import com.jvillada.movi.server.plugins.userId
import com.jvillada.movi.shared.model.BorrarLaCuentaRequest
import com.jvillada.movi.shared.model.PALABRA_PARA_BORRAR_LA_CUENTA
import com.jvillada.movi.shared.model.PasswordPolicy
import com.jvillada.movi.shared.model.confirmaElBorrado
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.log
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import java.io.File

// Mismo balde que el cambio de contraseña (ver UserRoutes.kt): quien llama ya está autenticado, así
// que la clave por usuario aísla de verdad, y 5 intentos cada 15 minutos alcanzan para errores de
// tipeo sin dejar que un token robado martille contraseñas hasta borrar la cuenta.
private const val MAX_INTENTOS_DE_BORRAR = 5
private const val VENTANA_DE_BORRAR_MS = 15 * 60_000L

internal const val CONTRASENA_NO_COINCIDE = "La contraseña no coincide"

/**
 * # `DELETE /api/me` — borrar la cuenta
 *
 * Autenticada, y además pide **la contraseña** en el cuerpo (nunca en la URL: la ruta queda en el
 * log de cada pedido) y la palabra [PALABRA_PARA_BORRAR_LA_CUENTA]. Con un token robado no alcanza:
 * sin la contraseña, 403 y no se toca nada.
 *
 * Borra todo lo de la persona en **una transacción** (ver `cuenta/BorrarLaCuenta.kt`) y después el
 * archivo viejo de reglas de comercio, si quedó alguno. Contesta 204; desde el pedido
 * siguiente ningún token de esa cuenta entra, porque ya no hay fila de `users` contra la que
 * validarlo.
 */
fun Route.borrarLaCuentaRoutes() {
    delete("/api/me") {
        val uid = call.userId()
        if (!RateLimiter.allow("borrar-cuenta:$uid", maxAttempts = MAX_INTENTOS_DE_BORRAR, windowMs = VENTANA_DE_BORRAR_MS)) {
            return@delete call.respond(HttpStatusCode.TooManyRequests, "Demasiados intentos, espera unos minutos")
        }
        val req = runCatching { call.receive<BorrarLaCuentaRequest>() }.getOrNull()
            ?: return@delete call.respond(HttpStatusCode.BadRequest, "Falta la contraseña")
        if (!confirmaElBorrado(req.confirmacion)) {
            return@delete call.respond(
                HttpStatusCode.BadRequest,
                "Escribe $PALABRA_PARA_BORRAR_LA_CUENTA para confirmar que quieres borrar tu cuenta",
            )
        }
        // Mismo techo que el login: `at.favre.lib:bcrypt` lanza sobre 72 bytes en vez de truncar, y
        // una contraseña así nunca pudo haberse registrado.
        if (PasswordPolicy.byteLength(req.contrasena) > PasswordPolicy.MAX_BYTES) {
            return@delete call.respond(HttpStatusCode.Forbidden, CONTRASENA_NO_COINCIDE)
        }
        val hash = dbQuery {
            Users.select(Users.passwordHash).where { Users.id eq uid }.firstOrNull()?.get(Users.passwordHash)
        } ?: return@delete call.respond(HttpStatusCode.NotFound)
        if (!BCrypt.verifyer().verify(req.contrasena.toCharArray(), hash).verified) {
            return@delete call.respond(HttpStatusCode.Forbidden, CONTRASENA_NO_COINCIDE)
        }

        val filas = dbQuery { borrarTodoLoDe(uid) }
        // Las reglas de comercio ya viven en la base (`reglas_de_comercio`, entra arriba), pero un
        // server viejo pudo dejar el archivo de antes en `movi-data`. La migración solo lo lee para
        // usuarios que existen, así que no resucitaría nada; igual es suyo, y se va. Si falla, la
        // cuenta ya no existe y nadie puede leerlo: se dice en el log.
        runCatching { File("movi-data", "merchant-rules-$uid.json").delete() }
            .onFailure { call.application.log.warn("borrar-cuenta: no pude borrar el archivo viejo de reglas de $uid: ${it.message}") }
        call.application.log.info("borrar-cuenta: $uid borró su cuenta ($filas filas)")
        call.respond(HttpStatusCode.NoContent)
    }
}
