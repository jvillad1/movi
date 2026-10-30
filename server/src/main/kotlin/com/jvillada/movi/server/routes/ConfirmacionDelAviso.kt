package com.jvillada.movi.server.routes

import com.jvillada.movi.server.db.Events
import com.jvillada.movi.server.db.SmsMessages
import com.jvillada.movi.server.db.VoidEvents
import com.jvillada.movi.server.db.toFinancialEvent
import com.jvillada.movi.shared.model.AvisoConfirmadoSinMovimiento
import com.jvillada.movi.shared.model.SMS_STATE_CONFIRMED
import com.jvillada.movi.shared.model.SmsMessage
import com.jvillada.movi.shared.model.momentoDelSms
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.greaterEq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.lessEq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.SqlExpressionBuilder.isNotNull
import org.jetbrains.exposed.sql.SqlExpressionBuilder.neq
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import kotlin.math.roundToLong

/*
 * # Con qué movimiento —y cuándo— se confirmó cada aviso
 *
 * Arreglo 6 de la auditoría de la ingesta (4-oct-2026). Hasta acá solo los comprobantes guardaban
 * con qué movimiento se confirmaron (`enlazarElComprobante`) y, desde #433, los avisos de un mismo
 * pago. Un SMS suelto confirmado no decía nada: 15 confirmados se quedaron sin movimiento vivo y no
 * había cómo saber por qué. Esto lo generaliza a `/api/sms/{id}/confirm`.
 */

/**
 * **Confirma el aviso [smsId] y anota con qué movimiento**, dentro de la transacción del llamador.
 * Devuelve cuántas filas eran del usuario (0 = no existe o es de otro).
 *
 * - [eventoPedido] es el que manda la app (el recién creado, o el que ya estaba: «Es este»). Un
 *   APK viejo no lo manda, y entonces se deduce con [eventoDeLaConfirmacion] cuando se puede.
 * - **Se escribe una sola vez.** Si el aviso ya tenía su movimiento o su hora, una segunda
 *   confirmación (un doble toque, un reintento) no los mueve: lo primero que se supo es lo que pasó.
 * - Un [eventoPedido] que existe pero es de otro usuario no se escribe. Uno que todavía no existe sí:
 *   en el teléfono el movimiento se guarda primero en local y llega al server después.
 */
internal fun Transaction.confirmarElAviso(uid: String, smsId: String, eventoPedido: String?, ahora: Long): Int {
    val fila = SmsMessages.selectAll()
        .where { (SmsMessages.id eq smsId) and (SmsMessages.userId eq uid) }
        .forUpdate()
        .firstOrNull() ?: return 0
    val ajeno = eventoPedido != null && Events.selectAll()
        .where { (Events.id eq eventoPedido) and (Events.userId neq uid) }
        .count() > 0
    val yaTenia = fila[SmsMessages.eventoId]
    // Revisión de la vuelta 74: un movimiento que ya es de OTRO aviso (dos compras iguales de $32.000
    // en Crepes, la segunda con «Es este» al movimiento de la primera) no se vuelve a vincular: los dos
    // avisos quedarían con el mismo movimiento y una compra se perdería. Los avisos del mismo pago sí
    // lo comparten (ver [avisosDelMismoPagoQue]).
    if (yaTenia == null && eventoPedido != null && !ajeno &&
        eventoPedido in movimientosYaReclamados(uid, excepto = avisosDelMismoPagoQue(uid, smsId, ahora))
    ) return CONFIRMAR_YA_RECLAMADO
    val evento = yaTenia
        ?: eventoPedido?.takeIf { !ajeno }
        ?: eventoDeLaConfirmacion(uid, fila.toSmsMessage(), ahora)
    SmsMessages.update({ (SmsMessages.id eq smsId) and (SmsMessages.userId eq uid) }) {
        it[state] = SMS_STATE_CONFIRMED
        if (yaTenia == null && evento != null) it[eventoId] = evento
        if (fila[SmsMessages.confirmadoEn] == null) it[confirmadoEn] = ahora
    }
    // Y la memoria aprende el comercio del banco, si el movimiento no lo tenía (ver
    // `aprenderElComercioDelAviso`).
    if (yaTenia == null && evento != null) aprenderElComercioDelAviso(uid, fila.toSmsMessage(), evento)
    return 1
}

/** Lo que contesta [confirmarElAviso] cuando el movimiento pedido ya es de otro aviso: la ruta da 409. */
internal const val CONFIRMAR_YA_RECLAMADO: Int = -1

/** La frase del 409 de un movimiento que ya tiene su aviso. */
internal const val YA_ES_DE_OTRO_AVISO: String = "Ese movimiento ya está vinculado a otro aviso del banco."

/**
 * **Los movimientos que ya tienen su aviso**: el `evento_id` y las `partes_eventos` de los avisos
 * confirmados del usuario, salvo los de [excepto] (el aviso que se está vinculando y los de su mismo
 * pago). Revisión de la vuelta 74: es lo que impide proponer, o aceptar, un movimiento que ya es de otro
 * aviso.
 */
internal fun Transaction.movimientosYaReclamados(uid: String, excepto: Collection<String> = emptyList()): Set<String> =
    SmsMessages.selectAll()
        .where { (SmsMessages.userId eq uid) and (SmsMessages.state eq SMS_STATE_CONFIRMED) }
        .filter { it[SmsMessages.id] !in excepto }
        .flatMap { fila -> listOfNotNull(fila[SmsMessages.eventoId]) + partesDeLaFila(fila) }
        .toSet()

/**
 * [smsId] y los avisos del mismo pago (el SMS, la notificación y el correo de una sola compra, ver
 * `gruposDelMismoPago`): esos sí comparten movimiento, es lo que hace «Cerrar los avisos».
 */
internal fun Transaction.avisosDelMismoPagoQue(uid: String, smsId: String, ahora: Long): Set<String> =
    setOf(smsId) + bandejaConLosPagos(uid, ahora).firstOrNull { it.id == smsId }?.miembrosDelGrupo.orEmpty()

/**
 * **El movimiento con que se confirmó [sms], cuando la app no lo dijo** (un APK viejo). Dos pistas,
 * en orden de confianza, y solo entre movimientos vivos que ningún otro aviso ya reclamó:
 *
 * 1. **El texto exacto.** Confirmar un SMS crea un movimiento con el mensaje entero en
 *    `raw_payload` (ver `movimientoConfirmadoDelSms`): si hay uno, es ese. Si hubiera varios, el
 *    más reciente.
 * 2. **Exactamente una coincidencia** de las que ofrece «¿Ya lo anotaste?» (mismo monto, moneda y
 *    tipo, a [DIAS_PARA_COINCIDIR] días): es lo que el dueño tocó en «Es este». Con dos o más no
 *    se adivina.
 *
 * `null` si no hay ninguna de las dos: mejor no saber que enlazar con el movimiento equivocado.
 */
internal fun Transaction.eventoDeLaConfirmacion(uid: String, sms: SmsMessage, ahora: Long): String? {
    // Primero los candidatos, y solo si hay alguno se miran los anulados y los ya reclamados: un
    // aviso sin nada parecido anotado (el caso común de un APK viejo que crea y confirma) no paga
    // ninguna consulta más.
    val porElTexto = Events.selectAll()
        .where { (Events.userId eq uid) and (Events.rawPayload eq sms.text) }
        .map { it.toFinancialEvent() }
    val leido = parseSms(sms.text, sms.bank)
    val momento = momentoDelSms(sms.time, ahora)
    val margen = DIAS_PARA_COINCIDIR * 86_400_000L
    val cercanos = if (leido == null) emptyList() else Events.selectAll()
        .where {
            (Events.userId eq uid) and (Events.amount eq leido.amount.roundToLong()) and
                (Events.timestamp greaterEq momento - margen) and (Events.timestamp lessEq momento + margen)
        }
        .map { it.toFinancialEvent() }
    if (porElTexto.isEmpty() && cercanos.isEmpty()) return null

    val anulados = VoidEvents.selectAll()
        .where { VoidEvents.userId eq uid }
        .map { it[VoidEvents.originalEventId] }
        .toSet()
    val yaReclamados = SmsMessages.select(SmsMessages.eventoId)
        .where { (SmsMessages.userId eq uid) and (SmsMessages.eventoId.isNotNull()) and (SmsMessages.id neq sms.id) }
        .mapNotNull { it[SmsMessages.eventoId] }
        .toSet()
    fun libre(id: String) = id !in anulados && id !in yaReclamados

    porElTexto.filter { libre(it.id) }.maxByOrNull { it.createdAt ?: it.timestamp }?.let { return it.id }
    if (leido == null) return null
    return coincidenciasDelSms(leido, momento, cercanos.filter { libre(it.id) })
        .singleOrNull()?.id
}

/**
 * **Los avisos confirmados cuyo movimiento ya no está vivo**: anulado, o que no existe. Solo los que
 * saben con qué movimiento se confirmaron (`evento_id`); los confirmados antes de esa columna no
 * dicen nada y no se adivinan. Del más nuevo al más viejo.
 */
internal fun Transaction.avisosConfirmadosSinMovimiento(uid: String): List<AvisoConfirmadoSinMovimiento> {
    val confirmados = SmsMessages.selectAll()
        .where { (SmsMessages.userId eq uid) and (SmsMessages.state eq SMS_STATE_CONFIRMED) and (SmsMessages.eventoId.isNotNull()) }
        .toList()
    if (confirmados.isEmpty()) return emptyList()
    val ids = confirmados.mapNotNull { it[SmsMessages.eventoId] }.distinct()
    val existen = Events.select(Events.id)
        .where { (Events.userId eq uid) and (Events.id inList ids) }
        .map { it[Events.id] }
        .toSet()
    val anulados = VoidEvents.selectAll()
        .where { (VoidEvents.userId eq uid) and (VoidEvents.originalEventId inList ids) }
        .map { it[VoidEvents.originalEventId] }
        .toSet()
    return confirmados.mapNotNull { fila ->
        val evento = fila[SmsMessages.eventoId] ?: return@mapNotNull null
        val porQue = when {
            evento !in existen -> AvisoConfirmadoSinMovimiento.BORRADO
            evento in anulados -> AvisoConfirmadoSinMovimiento.ANULADO
            else -> return@mapNotNull null
        }
        AvisoConfirmadoSinMovimiento(
            avisoId = fila[SmsMessages.id],
            time = fila[SmsMessages.time],
            bank = fila[SmsMessages.bank],
            text = fila[SmsMessages.text],
            eventoId = evento,
            confirmadoEn = fila[SmsMessages.confirmadoEn],
            porQue = porQue,
        )
    }.sortedByDescending { it.time }
}
