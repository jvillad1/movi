package com.jvillada.movi.shared.model

import kotlinx.serialization.Serializable

/**
 * # El saldo que dice el aviso del banco, contra el que dice Movi
 *
 * Algunos avisos traen el saldo de la cuenta después del movimiento («Saldo disponible $138.407»).
 * Cuando el dueño confirma un aviso así, Movi ya sabe dos cosas del mismo instante: lo que dice el
 * banco y lo que dice él. Si no coinciden, esto es la **propuesta** de cuadrar la cuenta, armada:
 * «Bancolombia Ahorros 8133: el banco dice $138.407 y Movi $138.361 → ajustar +$46».
 *
 * **Nunca se ajusta solo.** Se ajusta con «Ajustar» (`POST /api/cuadres-por-aviso/{avisoId}/ajustar`),
 * que escribe el mismo ajuste de siempre (`balanceAdjustmentEventFor`, categoría «Ajuste de saldo»),
 * fechado en el instante del aviso. «No ajustar» la saca para siempre.
 *
 * Se deriva en cada lectura (`GET /api/cuadres-por-aviso`), como los débitos automáticos: no hay
 * una fila «propuesta» que pueda quedar vieja. Por eso, una vez ajustada, desaparece sola: el saldo
 * de Movi en ese instante ya incluye el ajuste y coincide con el del banco.
 *
 * Solo cuentas de Dinero e Inversión: en una tarjeta «disponible» es el cupo, no la deuda (la misma
 * regla que el cuadre a mano, `sePuedeCuadrar`).
 */
@Serializable
data class PropuestaDeCuadrePorAviso(
    /** El aviso (`sms_messages.id`) que trajo el saldo. Es la clave de la propuesta. */
    val avisoId: String,
    val cuentaId: String,
    val cuentaNombre: String,
    /** El origen del aviso como lo guarda la bandeja («85540», «Notificación · Nu», «Correo · Bancolombia»). */
    val origen: String,
    /** Lo que dice el banco, ya en pesos enteros (los centavos se redondean). */
    val saldoDelBanco: Long,
    /** Lo que dice Movi en el instante del aviso, sin anulados. */
    val saldoEnMovi: Long,
    val moneda: String = "COP",
    /** El instante del movimiento confirmado con el aviso: ahí se compara, y ahí se fecha el ajuste. */
    val momento: Long,
) {
    /** Lo que se va a anotar: banco menos Movi. Nunca es cero (eso no es una propuesta). */
    val diferencia: Long get() = saldoDelBanco - saldoEnMovi
}
