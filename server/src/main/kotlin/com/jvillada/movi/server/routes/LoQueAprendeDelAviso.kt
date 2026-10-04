package com.jvillada.movi.server.routes

import com.jvillada.movi.server.db.Events
import com.jvillada.movi.server.sms.destinosDelDueno
import com.jvillada.movi.shared.model.AvisoPendienteParecido
import com.jvillada.movi.shared.model.HORAS_PARA_OFRECER_EL_AVISO
import com.jvillada.movi.shared.model.SMS_STATE_PENDING
import com.jvillada.movi.shared.model.SmsMessage
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.conElDestinoConocido
import com.jvillada.movi.shared.model.esIdDeComprobante
import com.jvillada.movi.shared.model.huellaDeUnMovimiento
import com.jvillada.movi.shared.model.momentoDelSms
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.isNull
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.or
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import kotlin.math.roundToLong

/*
 * # Lo que Movi aprende de un aviso que se confirma con un movimiento anotado a mano
 *
 * Arreglo 7 de la auditoría de la ingesta (4-oct-2026): la categoría acertaba en el 19 % de los
 * avisos, en buena parte porque los movimientos que el dueño anotó a mano nunca guardaban el texto
 * del banco, y la memoria no tenía con qué reconocer el siguiente aviso del mismo comercio.
 */

/**
 * **Los avisos pendientes que parecen ser lo que el dueño está anotando a mano** (ver
 * [AvisoPendienteParecido]): pendientes, legibles, con el mismo [monto] (redondeado, como se
 * guarda), la misma [moneda] y el mismo [tipo], llegados hace menos de [HORAS_PARA_OFRECER_EL_AVISO]
 * horas. Del más reciente al más viejo, máximo tres.
 *
 * Un pago avisado varias veces (SMS + Google Wallet, ver `AvisosDelMismoPago.kt`) sale una sola vez,
 * y uno que ya tiene un aviso confirmado no sale: ya está anotado. Desde «Agregar» se confirma solo el
 * aviso que se ofrece; los demás del mismo pago quedan en la bandeja con «Cerrar», como cuando el pago
 * se confirma por cualquiera de sus avisos.
 */
internal fun Transaction.avisosPendientesParecidos(
    uid: String,
    monto: Long,
    moneda: String,
    tipo: TransactionType,
    ahora: Long,
): List<AvisoPendienteParecido> {
    val desde = ahora - HORAS_PARA_OFRECER_EL_AVISO * 3_600_000L
    val pendientes = bandejaConLosPagos(uid, ahora)
        .filter { it.state == SMS_STATE_PENDING && it.yaAnotadoCon == null }
    val destinos = destinosDelDueno(uid)
    return pendientes
        .mapNotNull { aviso ->
            // Con `Long.MAX_VALUE` un tiempo que no se entiende vuelve como MAX y queda afuera: no se
            // ofrece un aviso que no se sabe cuándo llegó.
            val momento = momentoDelSms(aviso.time, Long.MAX_VALUE)
            if (momento < desde || momento > ahora + 3_600_000L) return@mapNotNull null
            val leido = parseSms(aviso.text, aviso.bank) ?: return@mapNotNull null
            if (leido.amount.roundToLong() != monto || leido.currency != moneda || leido.type != tipo) return@mapNotNull null
            val conNombre = conElDestinoConocido(leido, aviso.text, destinos)
            Triple(momento, aviso.grupoId ?: aviso.id, AvisoPendienteParecido(
                id = aviso.id,
                time = aviso.time,
                bank = aviso.bank,
                text = aviso.text,
                monto = leido.amount,
                moneda = leido.currency,
                tipo = leido.type,
                descripcion = conNombre.merchant,
                comercio = leido.merchant,
            ))
        }
        .sortedByDescending { it.first }
        .distinctBy { it.second }
        .map { it.third }
        .take(3)
}

/**
 * **Movi aprende el comercio del aviso.** Cuando un aviso se confirma con un movimiento que ya estaba
 * anotado a mano —«Es este», o «Agregar» que dijo «es este aviso»—, ese movimiento no traía el texto
 * del banco: el dueño escribió «Las Doce» y el banco dice «llave 0047142708». La memoria de categorías
 * aprende por ese texto (`merchant`), así que sin esto la llave de Las Doce salió 11 veces y nunca se
 * aprendió.
 *
 * Solo si el `merchant` del movimiento está vacío (lo que haya escrito él o el banco antes, no se
 * pisa), y solo si lo del banco identifica a alguien ([huellaDeUnMovimiento]: «Pago QR» a secas no).
 * No toca `last_edited_at`: no es una edición del dueño y no tiene que ganarle a ninguna.
 */
internal fun Transaction.aprenderElComercioDelAviso(uid: String, sms: SmsMessage, eventoId: String) {
    if (esIdDeComprobante(sms.id)) return
    val comercio = parseSms(sms.text, sms.bank)?.merchant ?: return
    if (huellaDeUnMovimiento(comercio) == null) return
    Events.update({
        (Events.id eq eventoId) and (Events.userId eq uid) and
            (Events.merchant.isNull() or (Events.merchant eq ""))
    }) {
        it[merchant] = comercio.take(255)
    }
}
