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
import org.jetbrains.exposed.sql.countDistinct
import org.jetbrains.exposed.sql.SqlExpressionBuilder.isNotNull
import org.jetbrains.exposed.sql.SqlExpressionBuilder.isNull
import org.jetbrains.exposed.sql.min
import org.jetbrains.exposed.sql.selectAll

/**
 * `GET /api/asistente/resumen` (vuelta 95): cuánto sabe Movi del dueño, en cuatro conteos (ver
 * [ResumenDeLoQueMoviSabe]). Autenticada y solo del usuario que pregunta; no devuelve ninguna fila, solo
 * números y la fecha del primer movimiento. Tres de los conteos son agregados de la base; las reglas
 * aprendidas salen de [memoriaDe], que lee hasta 2.000 movimientos para armar las huellas. Un traspaso
 * (dos patas) cuenta como un solo movimiento.
 */
fun Route.resumenDeLoQueMoviSabeRoutes() {
    get("/api/asistente/resumen") {
        call.respond(dbQuery { resumenDeLoQueMoviSabeDe(call.userId()) })
    }
}

internal fun Transaction.resumenDeLoQueMoviSabeDe(uid: String): ResumenDeLoQueMoviSabe {
    val anulados = VoidEvents.select(VoidEvents.originalEventId).where { VoidEvents.userId eq uid }
    val deLosQueCuentan = (Events.userId eq uid) and (Events.category neq OPENING_CATEGORY) and (Events.id notInSubQuery anulados)
    // Un traspaso son dos patas con el mismo `transfer_id` y para el dueño es UN movimiento: los sueltos
    // se cuentan uno a uno y los traspasos por su `transfer_id`.
    val sueltos = Events.id.count()
    val primero = Events.timestamp.min()
    val filaSueltos = Events.select(sueltos, primero)
        .where { deLosQueCuentan and Events.transferId.isNull() }
        .single()
    val traspasos = Events.transferId.countDistinct()
    val filaTraspasos = Events.select(traspasos, primero)
        .where { deLosQueCuentan and Events.transferId.isNotNull() }
        .single()
    val movimientos = (filaSueltos[sueltos] + filaTraspasos[traspasos]).toInt()
    val fechas = listOfNotNull(filaSueltos[primero], filaTraspasos[primero])
    return ResumenDeLoQueMoviSabe(
        movimientos = movimientos,
        desde = fechas.minOrNull()?.takeIf { movimientos > 0 }?.let { epochMillisToAppDateString(it) },
        cuentas = Accounts.selectAll().where { Accounts.userId eq uid }.count().toInt(),
        // Esta sí lee filas: `memoriaDe` mira hasta los últimos 2.000 movimientos para armar las huellas
        // (la misma memoria que usa Revisar aviso). Los otros tres son conteos de la base.
        reglasAprendidas = memoriaDe(uid).cuantasHuellas,
        recuerdos = MemoriaDelAsistente.selectAll().where { MemoriaDelAsistente.userId eq uid }.count().toInt(),
    )
}
