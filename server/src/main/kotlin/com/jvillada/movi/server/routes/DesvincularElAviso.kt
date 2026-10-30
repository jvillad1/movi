package com.jvillada.movi.server.routes

import com.jvillada.movi.server.db.SmsMessages
import com.jvillada.movi.server.db.dbQuery
import com.jvillada.movi.server.plugins.userId
import com.jvillada.movi.shared.model.AvisosDelMismoPago
import com.jvillada.movi.shared.model.SMS_STATE_CONFIRMED
import com.jvillada.movi.shared.model.SMS_STATE_PENDING
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.update

/*
 * # Vuelta 74 · desvincular un aviso: el «Deshacer» de «Es este»
 *
 * «Es este» confirma un aviso con un movimiento que ya estaba (`POST /api/sms/{id}/confirm?eventoId=`):
 * no crea nada, solo anota en el aviso con qué movimiento quedó (`evento_id`, `confirmado_en`). Su
 * «Deshacer» es la vuelta exacta de eso: el aviso vuelve a «Por revisar» y deja de apuntar al
 * movimiento. **No toca ningún movimiento ni ninguna cifra**: el movimiento sigue igual y sigue
 * contando igual, como antes del «Es este».
 *
 * Lo usa también el «Deshacer» de confirmar un aviso que SÍ creó su movimiento: la app anula ese
 * movimiento por el camino de siempre (`POST /api/events/{id}/void`, vuelta 67) y desvincula el aviso
 * con esto. Son dos reglas que ya existían —anular y la confirmación—, una detrás de la otra.
 */

/**
 * **Devuelve a «Por revisar» los avisos confirmados [ids] del usuario [uid]**, dentro de la transacción
 * del llamador: `pending`, sin movimiento (`evento_id`), sin hora de confirmación y sin las partes ni
 * el reparto que hubiera guardado una confirmación en partes (si no, «como la última vez» propondría
 * un reparto que se deshizo). Lo que no es del usuario, no existe o no está confirmado no se toca.
 * Devuelve cuántos avisos volvieron.
 */
internal fun Transaction.desvincularLosAvisos(uid: String, ids: List<String>): Int {
    if (ids.isEmpty()) return 0
    return SmsMessages.update({
        (SmsMessages.userId eq uid) and (SmsMessages.id inList ids) and (SmsMessages.state eq SMS_STATE_CONFIRMED)
    }) {
        it[state] = SMS_STATE_PENDING
        it[eventoId] = null
        it[confirmadoEn] = null
        it[partesEventos] = null
        it[reparto] = null
    }
}

fun Route.desvincularElAvisoRoutes() {
    /**
     * `POST /api/sms/desvincular` con [AvisosDelMismoPago]: los avisos confirmados vuelven a
     * pendientes (ver [desvincularLosAvisos]). 204 si volvió alguno; 404 si ninguno era del usuario y
     * estaba confirmado. Idempotente en la práctica: el segundo toque contesta 404 sin cambiar nada.
     */
    post("/api/sms/desvincular") {
        val uid = call.userId()
        val cuerpo = runCatching { call.receive<AvisosDelMismoPago>() }.getOrNull()
            ?: return@post call.respond(HttpStatusCode.BadRequest)
        val n = dbQuery { desvincularLosAvisos(uid, cuerpo.miembros.distinct()) }
        call.respond(if (n == 0) HttpStatusCode.NotFound else HttpStatusCode.NoContent)
    }
}
