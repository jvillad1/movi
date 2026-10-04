package com.jvillada.movi.shared.model

import kotlinx.serialization.Serializable

/**
 * **Un aviso del banco que está confirmado, pero cuyo movimiento ya no está vivo.**
 *
 * La auditoría de la ingesta (4-oct-2026) encontró 15 avisos confirmados desde el 20-ago sin ningún
 * movimiento: tres Uber, YouTube, Google One, tres cobros de Anthropic, unos QR, ingresos del papá.
 * Unos $830.000 en gastos y $7 M en ingresos que el estado «confirmado» daba por anotados y que no
 * estaban. Puede que se hubieran anulado a propósito, pero no había forma de saberlo: confirmar no
 * guardaba con qué movimiento.
 *
 * Desde entonces cada confirmación guarda su movimiento (`sms_messages.evento_id`) y cuándo
 * (`confirmado_en`), y `GET /api/sms/confirmados-sin-movimiento` devuelve los que quedaron colgando.
 *
 * @property porQue [ANULADO] si el movimiento se anuló, [BORRADO] si no existe. Ojo con [BORRADO] en
 *   el teléfono: un movimiento recién anotado sin señal todavía no llegó al server, y mientras tanto
 *   se ve igual que uno borrado.
 */
@Serializable
data class AvisoConfirmadoSinMovimiento(
    val avisoId: String,
    val time: String,
    val bank: String,
    val text: String,
    val eventoId: String,
    val confirmadoEn: Long? = null,
    val porQue: String,
) {
    companion object {
        const val ANULADO: String = "ANULADO"
        const val BORRADO: String = "BORRADO"
    }
}
