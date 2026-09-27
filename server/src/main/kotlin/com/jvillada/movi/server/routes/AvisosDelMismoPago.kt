package com.jvillada.movi.server.routes

import com.jvillada.movi.shared.model.SMS_STATE_PENDING
import com.jvillada.movi.shared.model.SmsMessage
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.momentoDelSms
import kotlin.math.abs
import kotlin.math.roundToLong

/** Cuánto pueden separarse dos avisos del mismo pago. Llegan a segundos; diez minutos cubre una demora. */
internal const val MINUTOS_PARA_EL_MISMO_PAGO: Long = 10

/**
 * **Cada mensaje pendiente, marcado con el aviso que parece ser el mismo pago** ([SmsMessage.parecidoA]).
 *
 * El 25-sep el dueño pagó $15.100 con la tarjeta Glim y le llegaron dos avisos: el de Google Wallet
 * y el de la app de Glim. Los aprobó los dos, seis segundos aparte, y quedaron dos movimientos.
 * Nada le había dicho antes que eran el mismo pago. Esto es lo que se lo dice.
 *
 * Dos avisos se parecen cuando `parseSms` lee en los dos **el mismo monto** (redondeado, como se
 * guarda), **la misma moneda** y **el mismo tipo**, llegaron a [MINUTOS_PARA_EL_MISMO_PAGO] o menos
 * (según [momentoDelSms]) y vienen de **orígenes distintos** (`bank`). Lo último es lo que separa
 * el mismo pago avisado dos veces de dos cafés iguales pagados seguidos: una misma app no avisa dos
 * veces el mismo pago, y dos compras reales iguales sí existen.
 *
 * Solo los **pendientes** llevan la marca, porque solo ahí sirve: es una advertencia antes de
 * aprobar. El otro puede estar en cualquier estado — que ya se haya aprobado el primero es
 * justamente el caso en que aprobar el segundo duplica. Si hay varios, se apunta al más cercano.
 *
 * Un aviso cuya hora no se puede leer, o cae en el futuro, **no entra**: ni lleva la marca ni se
 * la da a otro. [momentoDelSms] los fecha «ahora» para poder anotarlos, pero acá dos de esos
 * quedarían a cero minutos y se verían como el mismo pago sin serlo.
 *
 * No cambia el orden ni ningún otro campo: solo llena `parecidoA`.
 */
internal fun conLosAvisosParecidos(mensajes: List<SmsMessage>, ahora: Long): List<SmsMessage> {
    if (mensajes.none { it.state == SMS_STATE_PENDING }) return mensajes
    val margen = MINUTOS_PARA_EL_MISMO_PAGO * 60_000L
    val leidos = mensajes.mapNotNull { sms ->
        val momento = momentoConfiable(sms.time, ahora) ?: return@mapNotNull null
        val parsed = parseSms(sms.text, sms.bank) ?: return@mapNotNull null
        Leido(sms, parsed.amount.roundToLong(), parsed.currency, parsed.type, momento)
    }
    val porId = leidos.associateBy { it.sms.id }
    return mensajes.map { sms ->
        if (sms.state != SMS_STATE_PENDING) return@map sms
        val este = porId[sms.id] ?: return@map sms
        val parecido = leidos
            .filter { otro ->
                otro.sms.id != sms.id &&
                    !otro.sms.bank.trim().equals(sms.bank.trim(), ignoreCase = true) &&
                    otro.monto == este.monto &&
                    otro.moneda == este.moneda &&
                    otro.tipo == este.tipo &&
                    abs(otro.momento - este.momento) <= margen
            }
            .minByOrNull { abs(it.momento - este.momento) }
        if (parecido == null) sms else sms.copy(parecidoA = parecido.sms.id)
    }
}

/**
 * El momento del aviso solo si de verdad se leyó y no es futuro. Con `ahora = Long.MAX_VALUE`,
 * [momentoDelSms] no recorta nada y devuelve ese mismo valor únicamente cuando no entendió la hora.
 */
private fun momentoConfiable(time: String, ahora: Long): Long? {
    val momento = momentoDelSms(time, ahora = Long.MAX_VALUE)
    return momento.takeIf { it != Long.MAX_VALUE && it <= ahora }
}

private class Leido(val sms: SmsMessage, val monto: Long, val moneda: String, val tipo: TransactionType, val momento: Long)
