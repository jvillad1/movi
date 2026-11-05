package com.jvillada.movi.server.routes

import com.jvillada.movi.server.db.PushSubscriptions
import com.jvillada.movi.server.db.dbQuery
import com.jvillada.movi.server.plugins.userId
import com.jvillada.movi.server.push.VapidConfig
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.upsert

@Serializable
private data class PushSubscribeRequest(val endpoint: String, val p256dh: String, val auth: String)

@Serializable
private data class PushUnsubscribeRequest(val endpoint: String)

@Serializable
private data class VapidKeyResponse(val key: String)

/** Pública — el cliente necesita la clave ANTES de autenticar el subscribe. */
/**
 * Revisión de lanzamiento (nov-2026): el server le hace un POST a la URL que guarde el usuario. Sin
 * esto, un usuario cualquiera podía registrar `http://postgres.railway.internal:5432/…` o la IP de
 * metadatos y usar los avisos para sondear la red privada de Railway (SSRF). Solo `https://` y los
 * servicios de push de los navegadores: Chrome/Edge-Chromium (FCM), Firefox (Mozilla), Edge (WNS) y
 * Safari (Apple).
 */
internal fun esEndpointDePush(endpoint: String): Boolean {
    val uri = runCatching { java.net.URI(endpoint) }.getOrNull() ?: return false
    if (!uri.scheme.equals("https", ignoreCase = true) || uri.userInfo != null) return false
    if (uri.port != -1 && uri.port != 443) return false
    val host = uri.host?.lowercase() ?: return false
    return host == "fcm.googleapis.com" ||
        host == "web.push.apple.com" ||
        host.endsWith(".push.apple.com") ||
        host.endsWith(".push.services.mozilla.com") ||
        host.endsWith(".notify.windows.com")
}

fun Route.pushPublicRoutes() {
    get("/api/push/vapid-key") {
        val key = VapidConfig.publicKey()
        if (key.isNullOrBlank() || !VapidConfig.isConfigured()) {
            call.respond(HttpStatusCode.NotFound, "Push no configurado")
        } else {
            call.respond(VapidKeyResponse(key))
        }
    }
}

fun Route.pushRoutes() {
    post("/api/push/subscribe") {
        val uid = call.userId()
        val body = call.receive<PushSubscribeRequest>()
        if (body.endpoint.length > 500 || !esEndpointDePush(body.endpoint)) {
            return@post call.respond(HttpStatusCode.BadRequest, "Endpoint inválido")
        }
        dbQuery {
            PushSubscriptions.upsert {
                it[endpoint]  = body.endpoint
                it[userId]    = uid
                it[p256dh]    = body.p256dh.take(200)
                it[auth]      = body.auth.take(50)
                it[createdAt] = System.currentTimeMillis()
            }
        }
        call.respond(HttpStatusCode.Created)
    }

    delete("/api/push/subscribe") {
        val uid = call.userId()
        val body = call.receive<PushUnsubscribeRequest>()
        val deleted = dbQuery {
            PushSubscriptions.deleteWhere { (PushSubscriptions.endpoint eq body.endpoint) and (PushSubscriptions.userId eq uid) }
        }
        if (deleted == 0) call.respond(HttpStatusCode.NotFound)
        else call.respond(HttpStatusCode.NoContent)
    }
}
