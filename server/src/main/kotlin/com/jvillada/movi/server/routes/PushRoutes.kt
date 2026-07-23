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
        if (body.endpoint.isBlank() || body.endpoint.length > 500) {
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
