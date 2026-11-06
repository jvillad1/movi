package com.jvillada.movi.server.routes

import com.jvillada.movi.server.conector.ConfigDelConector
import com.jvillada.movi.server.conector.conexionesActivas
import com.jvillada.movi.server.conector.desconectar
import com.jvillada.movi.server.db.dbQuery
import com.jvillada.movi.server.plugins.userId
import com.jvillada.movi.shared.model.ConectorDeClaude
import com.jvillada.movi.shared.model.ConexionDeClaude
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get

/**
 * # Vuelta 135 · «Movi en Claude» desde la app (Tú → Ajustes)
 *
 * Con la sesión de la app (`authenticate("jwt")`): la dirección del conector y las conexiones activas, y
 * «Desconectar». Desconectar revoca la concesión y todos sus tokens en la misma transacción: el pedido siguiente de
 * Claude a `/mcp` ya contesta 401. Solo las del dueño: una de otra persona da 404, igual que una que no existe.
 */
fun Route.conectorDeLaAppRoutes(ahora: () -> Long = System::currentTimeMillis) {
    get("/api/conector") {
        val uid = call.userId()
        val conexiones = dbQuery { conexionesActivas(uid, ahora()) }.map {
            ConexionDeClaude(id = it.id, cliente = it.cliente, host = it.host, conectadaEn = it.conectadaEn, ultimoUso = it.ultimoUso)
        }
        call.respond(ConectorDeClaude(url = ConfigDelConector.urlDelMcp, conexiones = conexiones))
    }
    delete("/api/conector/conexiones/{id}") {
        val uid = call.userId()
        val id = call.parameters["id"].orEmpty()
        val hecho = id.isNotEmpty() && id.length <= 50 && dbQuery { desconectar(uid, id, ahora()) }
        call.respond(if (hecho) HttpStatusCode.NoContent else HttpStatusCode.NotFound)
    }
}
