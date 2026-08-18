package com.jvillada.movi.server.balance

import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.EventSource
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.ReconciliationStatus
import com.jvillada.movi.shared.model.TransactionType
import java.util.UUID
import kotlin.math.abs

/**
 * Categoría propia del ajuste, en vez de `"Otros"`.
 *
 * El ajuste ya no cuenta como flujo de caja (ver `isCashFlow`), pero **sigue siendo una fila
 * visible** en movimientos y en el detalle de la cuenta. Bajo `"Otros"` era indistinguible de
 * un gasto misceláneo real y además chocaba de frente con un presupuesto llamado "Otros",
 * que quedaba en OVER al instante. Con nombre propio se puede separar en cualquier desglose
 * que lo siga mostrando, sin depender de la bandera.
 */
const val ADJUSTMENT_CATEGORY = "Ajuste de saldo"

/** Movimiento que hay que registrar para llevar la deuda de un valor a otro. */
data class DebtAdjustment(val type: TransactionType, val amount: Long)

/**
 * Movimiento que lleva la deuda de [current] a [target], o null si ya coinciden.
 *
 * En una cuenta LOAN un EXPENSE sube la deuda y un INCOME la baja (ver [signedDelta]),
 * así que subir al objetivo es un cargo y bajar es un abono. El monto siempre es positivo.
 * Cuando no hay diferencia devuelve null a propósito: un evento de $0 sería ruido en el
 * listado de movimientos y no cambiaría ningún saldo.
 */
fun debtAdjustmentFor(current: Long, target: Long): DebtAdjustment? = when {
    target == current -> null
    target > current  -> DebtAdjustment(TransactionType.EXPENSE, target - current)
    else              -> DebtAdjustment(TransactionType.INCOME, current - target)
}

/**
 * Evento real y visible que deja la deuda de [account] en [target], o null si no hay nada
 * que ajustar. La deuda se deriva de los eventos, así que corregirla es registrar un
 * movimiento más — nunca sobrescribir un número.
 *
 * Origen y estado de conciliación siguen a [com.jvillada.movi.shared.model.openingEventFor]: es
 * el mismo tipo de asiento declarado por la persona dueña de la cuenta, no un movimiento
 * observado del banco. La categoría sí se aparta (ver [ADJUSTMENT_CATEGORY]).
 */
fun debtAdjustmentEventFor(
    account: Account,
    current: Long,
    target: Long,
    now: Long,
): FinancialEvent? {
    val adjustment = debtAdjustmentFor(current, target) ?: return null
    return FinancialEvent(
        id                   = "ev_${UUID.randomUUID()}",
        accountId            = account.id,
        type                 = adjustment.type,
        amount               = adjustment.amount,
        currency             = account.currency,
        category             = ADJUSTMENT_CATEGORY,
        description          = adjustmentDescription(target, account.currency),
        timestamp            = now,
        source               = EventSource.MANUAL,
        reconciliationStatus = ReconciliationStatus.RECONCILED,
    )
}

/**
 * Texto del ajuste. Dice a qué saldo quedó la cuenta, no cuánto se movió: dentro de un año
 * el monto del movimiento ya está en la fila, lo que no se puede reconstruir es contra qué
 * cifra del banco se cuadró.
 */
internal fun adjustmentDescription(target: Long, currency: String): String =
    "Ajuste al saldo del banco — quedó en ${formatAmount(target, currency)}"

internal fun formatAmount(amount: Long, currency: String): String {
    val grouped = abs(amount).toString().reversed().chunked(3).joinToString(".").reversed()
    return if (currency == "COP") "$$grouped" else "$grouped $currency"
}
