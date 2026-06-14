package com.jvillada.movi.server.routes

import at.favre.lib.crypto.bcrypt.BCrypt
import com.jvillada.movi.server.auth.JwtConfig
import com.jvillada.movi.server.auth.RateLimiter
import com.jvillada.movi.server.db.Users
import com.jvillada.movi.server.db.dbQuery
import com.jvillada.movi.shared.model.AuthResponse
import com.jvillada.movi.shared.model.LoginRequest
import com.jvillada.movi.shared.model.RegisterRequest
import io.ktor.http.HttpStatusCode
import io.ktor.server.plugins.origin
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll

fun Route.authRoutes() {
    route("/api/auth") {

        post("/register") {
            val ip = call.request.origin.remoteHost
            if (!RateLimiter.allow(ip, maxAttempts = 10, windowMs = 5 * 60_000L)) {
                return@post call.respond(HttpStatusCode.TooManyRequests, "Demasiados intentos, esperá unos minutos")
            }
            val req = call.receive<RegisterRequest>()
            if (req.email.isBlank() || req.name.isBlank() || req.password.length < 6) {
                return@post call.respond(HttpStatusCode.BadRequest, "Email required, password min 6 chars")
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
    }
}
