package com.jvillada.movi.server.routes

import com.jvillada.movi.server.auth.JwtConfig
import com.jvillada.movi.server.storage.Stores
import com.jvillada.movi.shared.model.AuthResponse
import com.jvillada.movi.shared.model.LoginRequest
import com.jvillada.movi.shared.model.RegisterRequest
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.server.routing.route

fun Route.authRoutes() {
    route("/api/auth") {
        post("/register") {
            val req = call.receive<RegisterRequest>()
            if (req.email.isBlank() || req.name.isBlank() || req.password.length < 6) {
                return@post call.respond(HttpStatusCode.BadRequest, "Email required, password min 6 chars")
            }
            if (Stores.users.findByEmail(req.email) != null) {
                return@post call.respond(HttpStatusCode.Conflict, "Email already registered")
            }
            val user = Stores.users.create(req.email, req.name, req.password)
            val token = JwtConfig.makeToken(user.id, user.email)
            call.respond(HttpStatusCode.Created, AuthResponse(token, user.id, user.name, user.email))
        }

        post("/login") {
            val req = call.receive<LoginRequest>()
            val user = Stores.users.findByEmail(req.email)
                ?: return@post call.respond(HttpStatusCode.Unauthorized, "Invalid credentials")
            if (!Stores.users.checkPassword(user, req.password)) {
                return@post call.respond(HttpStatusCode.Unauthorized, "Invalid credentials")
            }
            val token = JwtConfig.makeToken(user.id, user.email)
            call.respond(AuthResponse(token, user.id, user.name, user.email))
        }
    }
}
