package com.jvillada.movi.server.routes

import com.jvillada.movi.server.admin.AdminConfig
import com.jvillada.movi.server.auth.JwtConfig
import com.jvillada.movi.server.auth.RateLimiter
import com.jvillada.movi.server.observabilidad.Observabilidad
import com.jvillada.movi.server.plugins.userId
import com.jvillada.movi.shared.model.FalloDeLaApp
import com.jvillada.movi.shared.model.LoteDeUso
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.plugins.origin
import com.auth0.jwt.interfaces.JWTVerifier
import com.jvillada.movi.server.db.Users
import com.jvillada.movi.server.db.dbQuery
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.contentLength
import io.ktor.server.request.receiveChannel
import io.ktor.utils.io.readRemaining
import kotlinx.io.readByteArray
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import kotlinx.serialization.json.Json

/**
 * # Fallos y uso (vuelta 100)
 *
 * - `POST /api/fallos` — **pública**: la app puede caerse antes de tener sesión, y un 401 borraría
 *   justo el caso que más importa (el teléfono del papá con la app cerrada). El token es opcional y
 *   solo sirve para atribuir el fallo: uno inválido o de un usuario que no existe no rechaza el
 *   reporte, lo deja sin dueño. La frenan un límite global, el tope del cuerpo (leído con tope) y los
 *   topes por huella y por día de [Observabilidad.guardarFallo].
 * - `POST /api/uso` — autenticada, en lotes de a lo sumo `MAX_EVENTOS_POR_LOTE`.
 * - `GET /api/admin/fallos`, `GET /api/admin/uso` y el visor del recorrido (`/api/admin/uso/usuarios`,
 *   `…/usuarios/{id}/sesiones`, `…/sesiones/{sesionId}/eventos`) — solo un admin (`ADMIN_USER_IDS`);
 *   cualquier otro, 403.
 *
 * Ver [Observabilidad] para lo que se guarda y cómo se cuenta.
 */

internal const val MAX_CUERPO_DEL_FALLO = 16_000
internal const val MAX_CUERPO_DEL_USO = 96_000
/** Global (ver [fallosRoutes]): todo internet comparte este balde. */
internal const val FALLOS_POR_VENTANA = 120
internal const val LOTES_DE_USO_POR_VENTANA = 120
private const val VENTANA_DE_OBSERVABILIDAD_MS = 10 * 60_000L

private val jsonDeObservabilidad = Json { ignoreUnknownKeys = true }

/**
 * El usuario que dice un `Authorization: Bearer …` válido de Movi **y que existe**, o `null`. Solo
 * atribuye: no mira la versión de sesiones (un token viejo igual dice de quién es el fallo). Nunca lanza.
 */
internal suspend fun usuarioDelTokenDeMovi(encabezado: String?, verificador: JWTVerifier = JwtConfig.verifier()): String? {
    val uid = runCatching {
        val token = encabezado?.removePrefix("Bearer ")?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        verificador.verify(token).getClaim("userId").asString()
    }.getOrNull() ?: return null
    return runCatching { dbQuery { Users.select(Users.id).where { Users.id eq uid }.any() } }.getOrDefault(false).let { if (it) uid else null }
}

/** Lo que pasó al leer un cuerpo con tope. */
internal sealed interface CuerpoLeido {
    data class Texto(val texto: String) : CuerpoLeido
    data object Grande : CuerpoLeido
    data object Ilegible : CuerpoLeido
}

/**
 * **Lee el cuerpo sin pasarse de [max] bytes**: con un `Content-Length` más grande contesta
 * [CuerpoLeido.Grande] sin leer nada; sin él (chunked), lee a lo sumo `max + 1` y, si llegó a eso,
 * también. Así un cuerpo enorme nunca entra entero a la memoria.
 */
internal suspend fun ApplicationCall.leerConTope(max: Int): CuerpoLeido {
    request.contentLength()?.let { if (it > max) return CuerpoLeido.Grande }
    val bytes = runCatching { receiveChannel().readRemaining(max + 1L).readByteArray() }.getOrNull()
        ?: return CuerpoLeido.Ilegible
    if (bytes.size > max) return CuerpoLeido.Grande
    return CuerpoLeido.Texto(bytes.decodeToString())
}

/**
 * La ruta pública de los fallos. [verificador] es inyectable para las pruebas (otro secreto).
 *
 * **El límite es global, no por IP**: sin `XForwardedHeaders` (ver `AuthRoutes.kt`, por qué no se
 * instaló) el peer TCP es el borde de Railway, una sola dirección para todo internet. Lo que impide
 * que una inundación tape los fallos reales es lo de [Observabilidad.guardarFallo]: la misma huella
 * pasado su tope por hora solo se cuenta, y hay un tope de filas por día.
 */
fun Route.fallosRoutes(
    verificador: JWTVerifier? = null,
    ahora: () -> Long = System::currentTimeMillis,
) {
    post("/api/fallos") {
        val borde = call.request.origin.remoteHost
        if (!RateLimiter.allow("fallos:global:$borde", FALLOS_POR_VENTANA, VENTANA_DE_OBSERVABILIDAD_MS)) {
            return@post call.respond(HttpStatusCode.TooManyRequests)
        }
        val crudo = when (val leido = call.leerConTope(MAX_CUERPO_DEL_FALLO)) {
            CuerpoLeido.Grande -> return@post call.respond(HttpStatusCode.PayloadTooLarge)
            CuerpoLeido.Ilegible -> return@post call.respond(HttpStatusCode.BadRequest)
            is CuerpoLeido.Texto -> leido.texto
        }
        val fallo = runCatching { jsonDeObservabilidad.decodeFromString(FalloDeLaApp.serializer(), crudo) }.getOrNull()
            ?: return@post call.respond(HttpStatusCode.BadRequest)
        val encabezado = call.request.headers[HttpHeaders.Authorization]
        val uid = if (verificador != null) usuarioDelTokenDeMovi(encabezado, verificador) else usuarioDelTokenDeMovi(encabezado)
        Observabilidad.guardarFallo(fallo, uid, ahora())
        call.respond(HttpStatusCode.NoContent)
    }
}

/** Las rutas autenticadas: el uso y las dos de admin. Van dentro de `authenticate("jwt")`. */
fun Route.usoRoutes(ahora: () -> Long = System::currentTimeMillis) {
    post("/api/uso") {
        val uid = call.userId()
        // Este sí es por usuario: lo identifica el token, no la IP.
        if (!RateLimiter.allow("uso:$uid", LOTES_DE_USO_POR_VENTANA, VENTANA_DE_OBSERVABILIDAD_MS)) {
            return@post call.respond(HttpStatusCode.TooManyRequests)
        }
        val crudo = when (val leido = call.leerConTope(MAX_CUERPO_DEL_USO)) {
            CuerpoLeido.Grande -> return@post call.respond(HttpStatusCode.PayloadTooLarge)
            CuerpoLeido.Ilegible -> return@post call.respond(HttpStatusCode.BadRequest)
            is CuerpoLeido.Texto -> leido.texto
        }
        val lote = runCatching { jsonDeObservabilidad.decodeFromString(LoteDeUso.serializer(), crudo) }.getOrNull()
            ?: return@post call.respond(HttpStatusCode.BadRequest)
        call.respond(Observabilidad.guardarUso(uid, lote.eventos, ahora()))
    }

    get("/api/admin/fallos") {
        if (!AdminConfig.isAdmin(call.userId())) return@get call.respond(HttpStatusCode.Forbidden, "No autorizado")
        call.respond(Observabilidad.resumenDeFallos(ahora()))
    }

    get("/api/admin/uso") {
        if (!AdminConfig.isAdmin(call.userId())) return@get call.respond(HttpStatusCode.Forbidden, "No autorizado")
        call.respond(Observabilidad.resumenDeUso(ahora()))
    }

    // El visor del recorrido: elegir un usuario, ver sus sesiones y la línea de tiempo de una.
    get("/api/admin/uso/usuarios") {
        if (!AdminConfig.isAdmin(call.userId())) return@get call.respond(HttpStatusCode.Forbidden, "No autorizado")
        call.respond(Observabilidad.usuariosConUso())
    }
    get("/api/admin/uso/usuarios/{id}/sesiones") {
        if (!AdminConfig.isAdmin(call.userId())) return@get call.respond(HttpStatusCode.Forbidden, "No autorizado")
        val id = call.parameters["id"].orEmpty().take(50)
        call.respond(Observabilidad.sesionesDe(id))
    }
    get("/api/admin/uso/sesiones/{sesionId}/eventos") {
        if (!AdminConfig.isAdmin(call.userId())) return@get call.respond(HttpStatusCode.Forbidden, "No autorizado")
        val id = call.parameters["sesionId"].orEmpty().take(80)
        // La sesión es de una persona: sin `?usuario=` no se lee nada de nadie.
        val usuario = call.request.queryParameters["usuario"]?.take(50)
            ?: return@get call.respond(HttpStatusCode.BadRequest, "Falta ?usuario=")
        call.respond(Observabilidad.eventosDeLaSesion(usuario, id))
    }
}
