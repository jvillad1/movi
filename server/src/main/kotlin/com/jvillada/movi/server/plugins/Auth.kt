package com.jvillada.movi.server.plugins

import com.jvillada.movi.server.auth.JwtConfig
import com.jvillada.movi.server.db.Users
import com.jvillada.movi.server.db.dbQuery
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.authentication
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.auth.principal
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq

fun Application.configureAuth() {
    authentication {
        jwt("jwt") {
            verifier(JwtConfig.verifier())
            validate { credential ->
                val uid = credential.payload.getClaim("userId").asString() ?: return@validate null
                // **La firma no alcanza: la versión tiene que ser la vigente.** Ver
                // `Users.tokenVersion`. Un token sin claim es versión 0, igual que la columna de
                // una cuenta que nunca la subió — por eso el despliegue no desloguea a nadie.
                // Un usuario que ya no existe tampoco entra: no hay fila contra la que comparar.
                val vigente = versionVigenteDe(uid) ?: return@validate null
                if (JwtConfig.versionDelToken(credential.payload) != vigente) null
                else JWTPrincipal(credential.payload)
            }
        }
    }
}

/**
 * La versión de sesiones vigente de [uid], o `null` si el usuario no existe.
 *
 * Una lectura por la clave primaria en cada pedido autenticado. Es el precio de que «Cerrar sesión
 * en todos los aparatos» surta efecto en el pedido siguiente y no 30 días después; sin caché a
 * propósito, porque un caché es exactamente la ventana en la que el teléfono perdido sigue adentro.
 */
internal suspend fun versionVigenteDe(uid: String): Int? = dbQuery {
    Users.select(Users.tokenVersion).where { Users.id eq uid }.firstOrNull()?.get(Users.tokenVersion)
}

fun ApplicationCall.userId(): String =
    principal<JWTPrincipal>()!!.payload.getClaim("userId").asString()
