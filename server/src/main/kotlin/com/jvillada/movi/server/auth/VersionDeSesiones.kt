package com.jvillada.movi.server.auth

import com.jvillada.movi.server.db.Users
import org.jetbrains.exposed.sql.SqlExpressionBuilder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update

/**
 * **Cerrar todas las sesiones de [uid]**: sube `users.token_version` en uno y devuelve la versión
 * nueva, o `null` si el usuario no existe. Todo token firmado antes deja de pasar `configureAuth`
 * en el pedido siguiente.
 *
 * Corre DENTRO de la transacción de quien llama (cambio de contraseña, reset, «Cerrar sesión en
 * todos los aparatos»), para que la contraseña nueva y el corte de sesiones pasen juntos o no
 * pasen. El incremento es en SQL (`token_version = token_version + 1`) y no leer-sumar-escribir:
 * dos cierres simultáneos suben dos, nunca se pisan.
 */
fun subirVersionDeSesiones(uid: String): Int? {
    val tocadas = Users.update({ Users.id eq uid }) {
        with(SqlExpressionBuilder) { it.update(Users.tokenVersion, Users.tokenVersion + 1) }
    }
    if (tocadas == 0) return null
    return Users.selectAll().where { Users.id eq uid }.first()[Users.tokenVersion]
}
