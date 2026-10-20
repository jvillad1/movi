package com.jvillada.movi.server.routes

import com.jvillada.movi.server.apartados.apartadosDelUsuario
import com.jvillada.movi.server.db.Accounts
import com.jvillada.movi.server.db.Apartados
import com.jvillada.movi.server.db.VoidEvents
import com.jvillada.movi.server.db.dbQuery
import com.jvillada.movi.server.plugins.userId
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.Apartado
import com.jvillada.movi.shared.model.MAX_PROPOSITO_APARTADO
import com.jvillada.movi.shared.model.esDeTuPlata
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import java.util.UUID

/**
 * **Apartados** — plata que sigue en una cuenta tuya pero ya tiene dueño. No mueve dinero: solo
 * se descuenta de «Tu plata». El `restante` nunca se guarda, lo arma [apartadosDelUsuario] en cada
 * respuesta. POST es un upsert por `id` del cliente (el cliente offline-first reintenta).
 */
fun Route.apartadoRoutes() {
    route("/api/apartados") {
        get { call.respond(apartadosDe(call.userId())) }

        post {
            val uid = call.userId()
            val body = call.receive<Apartado>()
            val valido = validar(uid, body)
            valido.error?.let { return@post call.respond(it.first, it.second) }
            val id = body.id.trim().ifBlank { "apt_${UUID.randomUUID()}" }
            val ahora = System.currentTimeMillis()
            val creado = dbQuery {
                val propio = Apartados.selectAll().where { Apartados.id eq id }.firstOrNull()
                when {
                    propio == null -> {
                        Apartados.insert {
                            it[Apartados.id] = id
                            it[userId] = uid
                            it[accountId] = body.accountId
                            it[proposito] = valido.proposito
                            it[monto] = body.monto
                            it[categoria] = valido.categoria
                            it[desde] = if (body.desde == 0L) ahora else body.desde
                            it[createdAt] = ahora
                        }
                        true
                    }
                    propio[Apartados.userId] != uid -> null
                    else -> {
                        actualizar(uid, id, body, valido, ahora)
                        false
                    }
                }
            } ?: return@post call.respond(HttpStatusCode.Conflict, "Ese id ya existe")
            val guardado = apartadosDe(uid).first { it.id == id }
            call.respond(if (creado) HttpStatusCode.Created else HttpStatusCode.OK, guardado)
        }

        put("/{id}") {
            val uid = call.userId()
            val id = call.parameters["id"] ?: return@put call.respond(HttpStatusCode.BadRequest, "Missing id")
            val body = call.receive<Apartado>()
            val valido = validar(uid, body)
            valido.error?.let { return@put call.respond(it.first, it.second) }
            val actualizados = dbQuery { actualizar(uid, id, body, valido, System.currentTimeMillis()) }
            if (actualizados == 0) return@put call.respond(HttpStatusCode.NotFound)
            call.respond(apartadosDe(uid).first { it.id == id })
        }

        delete("/{id}") {
            val uid = call.userId()
            val id = call.parameters["id"] ?: return@delete call.respond(HttpStatusCode.BadRequest, "Missing id")
            val borrados = dbQuery { Apartados.deleteWhere { (Apartados.id eq id) and (Apartados.userId eq uid) } }
            if (borrados == 0) call.respond(HttpStatusCode.NotFound) else call.respond(HttpStatusCode.NoContent)
        }
    }
}

private class ApartadoValidado(
    val proposito: String,
    val categoria: String?,
    val error: Pair<HttpStatusCode, String>? = null,
)

/** Valida el cuerpo y que la cuenta sea del dueño y «de su plata» (no deuda, bien ni condicionada). */
private suspend fun validar(uid: String, body: Apartado): ApartadoValidado {
    val proposito = body.proposito.trim()
    val categoria = body.categoria?.trim()?.ifBlank { null }
    fun falla(estado: HttpStatusCode, motivo: String) = ApartadoValidado(proposito, categoria, estado to motivo)
    if (proposito.isBlank()) return falla(HttpStatusCode.BadRequest, "Falta el propósito")
    if (proposito.length > MAX_PROPOSITO_APARTADO) {
        return falla(HttpStatusCode.BadRequest, "El propósito no puede pasar de $MAX_PROPOSITO_APARTADO letras")
    }
    if (body.monto <= 0L) return falla(HttpStatusCode.BadRequest, "El monto debe ser mayor que cero")
    val cuenta = dbQuery {
        Accounts.selectAll().where { (Accounts.id eq body.accountId) and (Accounts.userId eq uid) }.firstOrNull()
    } ?: return falla(HttpStatusCode.NotFound, "Cuenta no encontrada")
    val sirve = esDeTuPlata(
        AccountType.valueOf(cuenta[Accounts.type]),
        cuenta[Accounts.conditionedTo],
        esBien = cuenta[Accounts.assetKind] != null,
    )
    if (!sirve) return falla(HttpStatusCode.BadRequest, "Elige una cuenta de tu plata: una deuda, un bien o una cuenta condicionada no se aparta")
    return ApartadoValidado(proposito, categoria)
}

private fun org.jetbrains.exposed.sql.Transaction.actualizar(uid: String, id: String, body: Apartado, v: ApartadoValidado, ahora: Long): Int =
    Apartados.update({ (Apartados.id eq id) and (Apartados.userId eq uid) }) {
        it[accountId] = body.accountId
        it[proposito] = v.proposito
        it[monto] = body.monto
        it[categoria] = v.categoria
        if (body.desde != 0L) it[desde] = body.desde
        it[lastEditedAt] = ahora
    }

private suspend fun apartadosDe(uid: String): List<Apartado> = dbQuery {
    val anulados = VoidEvents.selectAll().where { VoidEvents.userId eq uid }
        .map { it[VoidEvents.originalEventId] }.toSet()
    apartadosDelUsuario(uid, anulados)
}
