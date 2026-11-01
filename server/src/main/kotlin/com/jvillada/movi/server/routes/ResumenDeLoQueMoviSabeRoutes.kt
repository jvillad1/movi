package com.jvillada.movi.server.routes

import com.jvillada.movi.server.db.Accounts
import com.jvillada.movi.server.db.Events
import com.jvillada.movi.server.db.MemoriaDelAsistente
import com.jvillada.movi.server.db.VoidEvents
import com.jvillada.movi.server.db.dbQuery
import com.jvillada.movi.server.plugins.userId
import com.jvillada.movi.server.sms.memoriaDe
import com.jvillada.movi.server.time.epochMillisToAppDateString
import com.jvillada.movi.shared.model.OPENING_CATEGORY
import com.jvillada.movi.shared.model.ResumenDeLoQueMoviSabe
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.neq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.notInSubQuery
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.count
import org.jetbrains.exposed.sql.min
import org.jetbrains.exposed.sql.selectAll

/**
 * `GET /api/asistente/resumen` (vuelta 95): cuánto sabe Movi del dueño, en cuatro conteos (ver
 * [ResumenDeLoQueMoviSabe]). Autenticada y solo del usuario que pregunta; no devuelve ninguna fila, solo
 * números y la fecha del primer movimiento.
 */
fun Route.resumenDeLoQueMoviSabeRoutes() {
    get("/api/asistente/resumen") {
        call.respond(dbQuery { resumenDeLoQueMoviSabeDe(call.userId()) })
    }
}

internal fun Transaction.resumenDeLoQueMoviSabeDe(uid: String): ResumenDeLoQueMoviSabe {
    val anulados = VoidEvents.select(VoidEvents.originalEventId).where { VoidEvents.userId eq uid }
    val cuantos = Events.id.count()
    val primero = Events.timestamp.min()
    val fila = Events.select(cuantos, primero)
        .where {
            (Events.userId eq uid) and (Events.category neq OPENING_CATEGORY) and (Events.id notInSubQuery anulados)
        }
        .single()
    val movimientos = fila[cuantos].toInt()
    return ResumenDeLoQueMoviSabe(
        movimientos = movimientos,
        desde = fila[primero]?.takeIf { movimientos > 0 }?.let { epochMillisToAppDateString(it) },
        cuentas = Accounts.selectAll().where { Accounts.userId eq uid }.count().toInt(),
        reglasAprendidas = memoriaDe(uid).cuantasHuellas,
        recuerdos = MemoriaDelAsistente.selectAll().where { MemoriaDelAsistente.userId eq uid }.count().toInt(),
    )
}
