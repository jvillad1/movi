package com.jvillada.movi.server.plugins

import com.jvillada.movi.server.auth.JwtConfig
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.authentication
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.auth.principal

fun Application.configureAuth() {
    authentication {
        jwt("jwt") {
            verifier(JwtConfig.verifier())
            validate { credential ->
                if (credential.payload.getClaim("userId").asString() != null)
                    JWTPrincipal(credential.payload)
                else null
            }
        }
    }
}

fun ApplicationCall.userId(): String =
    principal<JWTPrincipal>()!!.payload.getClaim("userId").asString()
