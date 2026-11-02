package com.jvillada.movi.server.routes

import com.jvillada.movi.server.admin.AdminConfig
import com.jvillada.movi.server.ai.ElCostoDeLaIa
import com.jvillada.movi.server.ai.ElTopeDelMes
import com.jvillada.movi.server.ai.MODELO_SIN_IA
import com.jvillada.movi.server.db.AiTurns
import com.jvillada.movi.server.db.TopesDeLaIaPorUsuario
import com.jvillada.movi.server.db.Users
import com.jvillada.movi.server.db.dbQuery
import com.jvillada.movi.server.plugins.userId
import com.jvillada.movi.shared.model.GuardarTopeDeLaIa
import com.jvillada.movi.shared.model.TOPE_DE_PREGUNTAS_POR_DEFECTO
import com.jvillada.movi.shared.model.TopeDeUnUsuario
import com.jvillada.movi.shared.model.TopesDeLaIa
import com.jvillada.movi.shared.model.problemaDelTope
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.selectAll

/**
 * # Movi AI para quien administra (vuelta 106)
 *
 * - `GET /api/admin/ia/costo` — lo que cuesta la IA en pesos: 7 y 30 días, por usuario activo, por
 *   pregunta, el mes y su proyección, y los 10 que más gastan (ver [ElCostoDeLaIa]).
 * - `GET /api/admin/ia/topes` — el tope global de preguntas libres por mes, las excepciones y los
 *   usuarios con cuántas hicieron este mes.
 * - `PUT /api/admin/ia/topes/global` — `{"tope": N}`.
 * - `PUT /api/admin/ia/topes/usuarios/{id}` — `{"tope": N}`: la excepción de un usuario.
 * - `DELETE /api/admin/ia/topes/usuarios/{id}` — le vuelve a tocar el global.
 *
 * Dentro de `authenticate("jwt")`; **403** a quien no está en `ADMIN_USER_IDS`.
 */
fun Route.adminIaRoutes() {
    route("/api/admin/ia") {
        get("/costo") {
            if (!soloAdmin()) return@get
            call.respond(ElCostoDeLaIa.resumen())
        }
        route("/topes") {
            get {
                if (!soloAdmin()) return@get
                call.respond(topesParaElAdmin())
            }
            put("/global") {
                if (!soloAdmin()) return@put
                val tope = call.receive<GuardarTopeDeLaIa>().tope
                problemaDelTope(tope)?.let { return@put call.respond(HttpStatusCode.UnprocessableEntity, mapOf("error" to it)) }
                ElTopeDelMes.guardarGlobal(tope)
                call.respond(topesParaElAdmin())
            }
            put("/usuarios/{id}") {
                if (!soloAdmin()) return@put
                val id = call.parameters["id"] ?: return@put call.respond(HttpStatusCode.BadRequest)
                val tope = call.receive<GuardarTopeDeLaIa>().tope
                problemaDelTope(tope)?.let { return@put call.respond(HttpStatusCode.UnprocessableEntity, mapOf("error" to it)) }
                val existe = dbQuery { Users.selectAll().where { Users.id eq id }.any() }
                if (!existe) return@put call.respond(HttpStatusCode.NotFound, mapOf("error" to "No existe ese usuario."))
                ElTopeDelMes.guardarExcepcion(id, tope)
                call.respond(topesParaElAdmin())
            }
            delete("/usuarios/{id}") {
                if (!soloAdmin()) return@delete
                val id = call.parameters["id"] ?: return@delete call.respond(HttpStatusCode.BadRequest)
                ElTopeDelMes.quitarExcepcion(id)
                call.respond(topesParaElAdmin())
            }
        }
    }
}

/** Lo que pinta la pantalla del tope: el global, las excepciones y todos con lo de este mes. */
internal suspend fun topesParaElAdmin(ahora: Long = System.currentTimeMillis()): TopesDeLaIa {
    val elegido = ElTopeDelMes.globalElegido()
    val desde = ElTopeDelMes.inicioDelMes(ahora)
    val admins = AdminConfig.adminIds()
    val (usuarios, excepciones) = dbQuery {
        val usadas = HashMap<String, Int>()
        AiTurns.select(AiTurns.userId)
            .where { (AiTurns.creadoEn greaterEq desde) and (AiTurns.modelo neq MODELO_SIN_IA) }
            .forEach { usadas.merge(it[AiTurns.userId], 1, Int::plus) }
        val topes = TopesDeLaIaPorUsuario.selectAll().associate { it[TopesDeLaIaPorUsuario.userId] to it[TopesDeLaIaPorUsuario.tope] }
        val todos = Users.selectAll().map { u ->
            val id = u[Users.id]
            TopeDeUnUsuario(
                usuarioId = id,
                correo = u[Users.email],
                usadasEsteMes = usadas[id] ?: 0,
                tope = topes[id],
                esAdmin = id in admins,
            )
        }.sortedWith(compareByDescending<TopeDeUnUsuario> { it.usadasEsteMes }.thenBy { it.correo })
        todos to todos.filter { it.tope != null }
    }
    return TopesDeLaIa(
        global = elegido ?: TOPE_DE_PREGUNTAS_POR_DEFECTO,
        globalElegido = elegido != null,
        excepciones = excepciones,
        usuarios = usuarios,
    )
}
