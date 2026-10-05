package com.jvillada.movi.shared.model

import kotlinx.serialization.Serializable

/**
 * # Un pago, una tarjeta
 *
 * Un mismo pago llega por varios canales —el SMS de Bancolombia, la notificación de la app del
 * banco, la de Google Wallet, el correo— y cada aviso es una fila de `sms_messages`. El server los
 * junta ([SmsMessage.grupoId]) y la bandeja muestra **uno por pago**. Este archivo es lo que cliente
 * y server comparten: cómo se cuenta y qué viaja.
 */

/**
 * **Los pendientes de [mensajes], uno por pago**, en el orden en que vienen: de cada pago queda el
 * primero que aparece (en la bandeja, el más reciente). Un aviso sin [SmsMessage.grupoId] es su
 * propio pago.
 */
fun unoPorPago(mensajes: List<SmsMessage>): List<SmsMessage> =
    mensajes.filter { it.state == SMS_STATE_PENDING }.distinctBy { it.grupoId ?: it.id }

/**
 * **Cuántos pagos esperan en «Por revisar»** por los mensajes del banco. La MISMA cuenta en el
 * Inicio (server, `pendingSms`), en el renglón de Movimientos y en la bandeja (cliente): un pago
 * avisado tres veces es uno.
 */
fun cuantosPagosPorRevisar(mensajes: List<SmsMessage>): Int = unoPorPago(mensajes).size

/**
 * **Los avisos de un mismo pago**, como los devuelve `GET /api/sms/grupo/{grupoId}`.
 *
 * @property miembros todos los avisos del pago, en cualquier estado: primero [propuestaDe] y
 *   después del más viejo al más nuevo.
 * @property propuestaDe el aviso del que sale la propuesta de Reconciliar: el que más dice (ver
 *   `propuestaDelGrupo` en el server).
 * @property yaAnotadoCon el aviso del pago que ya se confirmó, si hay uno: el pago ya tiene su
 *   movimiento y lo que queda es cerrar los demás.
 */
@Serializable
data class GrupoDeAvisos(
    val grupoId: String,
    val miembros: List<SmsMessage>,
    val propuestaDe: String,
    val yaAnotadoCon: String? = null,
)

/**
 * Cuerpo de `POST /api/sms/grupo/{grupoId}/confirmar`: **un solo movimiento para todos los avisos
 * del mismo pago.**
 *
 * - Con [evento]: el server lo crea y marca confirmados a todos los [miembros], en una transacción.
 * - Con [eventoExistenteId] («Es este»): no crea nada, enlaza los avisos a ese movimiento.
 * - Con [patas]: el server arma las dos patas del pago (una tarjeta, una cuota, un traspaso, un
 *   avance; ver [DosPatasDelAviso]) y marca los avisos con la del dinero, en la misma transacción.
 *   Un APK viejo no lo manda y sigue con [evento].
 * - Sin ninguno: solo vale si algún aviso ya estaba confirmado («ya anotado»), y cierra los demás.
 *
 * Si algún miembro ya estaba confirmado, **nunca** se crea otro movimiento: así un doble toque, o
 * dos teléfonos a la vez, no duplican.
 */
@Serializable
data class ConfirmarElMismoPago(
    val miembros: List<String>,
    val evento: FinancialEvent? = null,
    val eventoExistenteId: String? = null,
    val patas: DosPatasDelAviso? = null,
)

/**
 * Lo que contesta el confirmar: con qué movimiento quedó el pago, si se creó ahora y qué avisos se
 * cerraron. [patas] son los ids de todos los movimientos del pago cuando es de dos patas (la del
 * dinero, que es [eventoId], y la otra); vacía si es un movimiento suelto. Un doble toque contesta
 * lo mismo que el primero, con `creado = false`.
 */
@Serializable
data class MismoPagoConfirmado(
    val eventoId: String? = null,
    val creado: Boolean = false,
    val cerrados: List<String> = emptyList(),
    val patas: List<String> = emptyList(),
)

/** Cuerpo de `…/ignorar` y `…/desagrupar`: los avisos a los que se aplica. */
@Serializable
data class AvisosDelMismoPago(val miembros: List<String>)
