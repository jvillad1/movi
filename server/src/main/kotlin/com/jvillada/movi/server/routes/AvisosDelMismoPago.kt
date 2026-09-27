package com.jvillada.movi.server.routes

import com.jvillada.movi.shared.model.ParsedSms
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
internal fun conLosAvisosParecidos(
    mensajes: List<SmsMessage>,
    ahora: Long,
    /**
     * Si viene, solo se marca ese mensaje (el detalle de uno, `GET /api/sms/{id}`); los demás salen
     * tal cual. `null` marca todos los pendientes (la bandeja).
     */
    soloElDe: String? = null,
    /** Cómo se lee un aviso. Es `parseSms`; se deja cambiar para poder contar cuántos se leen. */
    leer: (SmsMessage) -> ParsedSms? = { parseSms(it.text, it.bank) },
): List<SmsMessage> {
    val margen = MINUTOS_PARA_EL_MISMO_PAGO * 60_000L
    val momentos = mensajes.associate { it.id to momentoConfiable(it.time, ahora) }
    // Los pendientes que hay que marcar, por momento. Solo se lee (con `parseSms`) lo que cae a
    // [MINUTOS_PARA_EL_MISMO_PAGO] de alguno: el historial entero crece sin tope y casi nada de él
    // está cerca de un pendiente.
    val anclas = mensajes
        .filter { it.state == SMS_STATE_PENDING && (soloElDe == null || it.id == soloElDe) }
        .mapNotNull { momentos[it.id] }
        .sorted()
    if (anclas.isEmpty()) return mensajes
    fun cercaDeUnPendiente(momento: Long): Boolean {
        val i = anclas.binarySearch(momento)
        if (i >= 0) return true
        val despues = -i - 1
        return (despues < anclas.size && anclas[despues] - momento <= margen) ||
            (despues > 0 && momento - anclas[despues - 1] <= margen)
    }
    val leidos = mensajes.mapNotNull { sms ->
        val momento = momentos[sms.id] ?: return@mapNotNull null
        if (!cercaDeUnPendiente(momento)) return@mapNotNull null
        val parsed = leer(sms) ?: return@mapNotNull null
        Leido(sms, parsed.amount.roundToLong(), parsed.currency, parsed.type, momento)
    }
    val porId = leidos.associateBy { it.sms.id }
    return mensajes.map { sms ->
        if (sms.state != SMS_STATE_PENDING) return@map sms
        if (soloElDe != null && sms.id != soloElDe) return@map sms
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
