package com.jvillada.movi.server.routes

import com.jvillada.movi.server.storage.Stores
import com.jvillada.movi.shared.model.Account
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route

fun Route.accountRoutes() {
    route("/api/accounts") {
        get {
            call.respond(Stores.accounts.snapshot())
        }
        get("/{id}") {
            val id = call.parameters["id"] ?: return@get call.respond(HttpStatusCode.BadRequest)
            val account = Stores.accounts.snapshot().find { it.id == id }
                ?: return@get call.respond(HttpStatusCode.NotFound)
            call.respond(account)
        }
        post {
            val body = call.receive<Account>()
            val account = if (body.id.isBlank())
                body.copy(id = "acc_${System.currentTimeMillis()}")
            else body
            Stores.accounts.mutate { it.add(account) }
            call.respond(HttpStatusCode.Created, account)
        }
    }
}
