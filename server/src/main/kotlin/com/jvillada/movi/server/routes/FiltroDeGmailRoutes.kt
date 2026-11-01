package com.jvillada.movi.server.routes

import com.jvillada.movi.server.correo.ConfigDeCorreoEntrante
import com.jvillada.movi.server.correo.direccionDeReenvio
import com.jvillada.movi.server.correo.momentoDelCorreo
import com.jvillada.movi.server.correo.tokenDeCorreoDe
import com.jvillada.movi.server.db.SmsMessages
import com.jvillada.movi.server.db.dbQuery
import com.jvillada.movi.server.plugins.userId
import com.jvillada.movi.shared.model.FiltroDeGmail
import com.jvillada.movi.shared.model.MotivoDeApartado
import com.jvillada.movi.shared.model.codigoDeConfirmacionDeGmail
import com.jvillada.movi.shared.model.filtroDeGmailPara
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.greaterEq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.selectAll

/*
 * # Vuelta 93 · el filtro de Movi para Gmail (`GET /api/correo-entrante/filtro`)
 *
 * Autenticada y de solo lectura, y **solo habla de quien pregunta**: la dirección es la suya (se deriva
 * de su id, como `/direccion`), el código de confirmación sale de SUS filas de `sms_messages`, y la lista
 * de remitentes es la misma para todos (no es dato de nadie). Ver `RemitentesFinancieros.kt` en `:core`.
 */

/** Cuántos días vale un código de confirmación de Gmail: pasado eso, nadie lo está esperando. */
internal const val DIAS_DEL_CODIGO_DE_CONFIRMACION: Int = 3

/**
 * El código que Gmail mandó a la dirección de [uid] al agregarla como dirección de reenvío: el último
 * correo apartado como [MotivoDeApartado.CONFIRMACION_DE_REENVIO] de los últimos
 * [DIAS_DEL_CODIGO_DE_CONFIRMACION] días. `null` si no llegó o no se le ve el código.
 */
internal suspend fun codigoDeConfirmacionDe(uid: String, ahora: Long): String? {
    val desde = momentoDelCorreo(null, ahora - DIAS_DEL_CODIGO_DE_CONFIRMACION * 86_400_000L)
    return dbQuery {
        SmsMessages.selectAll()
            .where {
                (SmsMessages.userId eq uid) and
                    (SmsMessages.motivoApartado eq MotivoDeApartado.CONFIRMACION_DE_REENVIO.name) and
                    (SmsMessages.time greaterEq desde)
            }
            .orderBy(SmsMessages.time, SortOrder.DESC)
            .limit(5)
            .firstNotNullOfOrNull { codigoDeConfirmacionDeGmail(it[SmsMessages.text]) }
    }
}

/** Lo que contesta `/filtro` para [uid]. */
internal suspend fun filtroDeGmailDe(uid: String, ahora: Long): FiltroDeGmail {
    val base = ConfigDeCorreoEntrante.direccionBase()
    val direccion = base?.let { direccionDeReenvio(it, tokenDeCorreoDe(uid)) }
    return filtroDeGmailPara(
        direccion = direccion,
        configurado = ConfigDeCorreoEntrante.estaConfigurado(),
        codigoDeConfirmacion = codigoDeConfirmacionDe(uid, ahora),
    )
}

fun Route.filtroDeGmailRoutes() {
    get("/api/correo-entrante/filtro") {
        call.respond(filtroDeGmailDe(call.userId(), System.currentTimeMillis()))
    }
}
