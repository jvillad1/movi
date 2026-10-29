package com.jvillada.movi.shared.model

/*
 * # Los avisos viejos se apartan (vuelta 51)
 *
 * El aviso de Rappi del 2 de septiembre seguía en «Por revisar» dos meses después, con su «Ojo», y
 * «Panamericana» seguía en «Otros». Un aviso pendiente de más de 30 días ya no compite con los de hoy:
 * la bandeja lo junta en «Más antiguos (N)», plegado, y no entra en «Por confirmar $X» ni en el «N por
 * revisar» del Inicio. **No se borra nada**: ignorarlos es el «Ignorar» de siempre.
 */

/** Desde cuántos días un aviso pendiente es «antiguo». Elegido, no medido. */
const val DIAS_PARA_QUE_UN_AVISO_SEA_ANTIGUO: Int = 30

private const val MILIS_POR_DIA: Long = 24L * 60 * 60 * 1000

/**
 * **¿El pago que encabeza [cabeza] es antiguo?** Se cuenta desde la fecha del aviso: el más reciente
 * de los avisos pendientes del mismo pago (un pago avisado por SMS y por la app es uno, y vale el
 * último). Más de [DIAS_PARA_QUE_UN_AVISO_SEA_ANTIGUO] días antes de [ahora] → antiguo. Una fecha que
 * no se lee cuenta como de ahora (`momentoDelSms`): nunca se aparta lo que no se sabe fechar.
 */
fun esPagoAntiguo(cabeza: SmsMessage, mensajes: List<SmsMessage>, ahora: Long): Boolean {
    val grupo = cabeza.grupoId
    val delPago = if (grupo == null) listOf(cabeza)
    else mensajes.filter { it.grupoId == grupo && it.state == SMS_STATE_PENDING }.ifEmpty { listOf(cabeza) }
    val masReciente = delPago.maxOf { momentoDelSms(it.time, ahora) }
    return ahora - masReciente > DIAS_PARA_QUE_UN_AVISO_SEA_ANTIGUO * MILIS_POR_DIA
}

/** Los pagos pendientes, partidos en los de los últimos 30 días y los antiguos, cada uno en su orden. */
data class PagosPorAntiguedad(val recientes: List<SmsMessage>, val antiguos: List<SmsMessage>)

/**
 * **Parte [pagos]** (uno por pago, como los da [unoPorPago]) en recientes y antiguos, sin reordenar.
 * [mensajes] es la bandeja entera, para encontrar los otros avisos de cada pago.
 */
fun separarLosPagosAntiguos(pagos: List<SmsMessage>, mensajes: List<SmsMessage>, ahora: Long): PagosPorAntiguedad {
    val (antiguos, recientes) = pagos.partition { esPagoAntiguo(it, mensajes, ahora) }
    return PagosPorAntiguedad(recientes = recientes, antiguos = antiguos)
}

/** **Los pagos de los últimos 30 días**: lo que cuenta en «N por revisar» y en «Por confirmar $X». */
fun pagosRecientesPorConfirmar(mensajes: List<SmsMessage>, ahora: Long): List<SmsMessage> =
    separarLosPagosAntiguos(unoPorPago(mensajes), mensajes, ahora).recientes

/** **Cuántos pagos antiguos** esperan: lo que la franja del Inicio menciona aparte («y 2 antiguos»). */
fun cuantosPagosAntiguos(mensajes: List<SmsMessage>, ahora: Long): Int =
    separarLosPagosAntiguos(unoPorPago(mensajes), mensajes, ahora).antiguos.size
