package com.jvillada.movi.server.balance

import com.jvillada.movi.shared.model.ADJUSTMENT_CATEGORY
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.EventSource
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.ReconciliationStatus
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.signedDelta
import java.util.UUID
import kotlin.math.abs


/** Movimiento que hay que registrar para llevar un saldo de un valor a otro. */
data class AjusteDeSaldo(val type: TransactionType, val amount: Long)

/**
 * Movimiento que lleva el saldo de una cuenta [accountType] de [current] a [target], o null si ya
 * coinciden.
 *
 * El signo lo manda [signedDelta], que es donde vive la convención por tipo de cuenta, y esta
 * función es su inversa: en una cuenta de deuda (LOAN, CREDIT_CARD) un EXPENSE **sube** el saldo
 * —que ahí es lo que se debe— y un INCOME lo baja; en una cuenta de activo es al revés. Calcularlo
 * en vez de escribirlo dos veces es todo el punto: la primera versión de esta función solo sabía
 * de deudas, y llevada tal cual a una cuenta de ahorros habría anotado un **gasto** para subir el
 * saldo — el ajuste habría movido la plata para el lado contrario del que dice la pantalla.
 *
 * El monto siempre es positivo. Cuando no hay diferencia devuelve null a propósito: un evento de
 * $0 sería ruido en el listado de movimientos y no cambiaría ningún saldo. Eso además hace
 * idempotente a quien lo llame — cuadrar dos veces contra la misma cifra no escribe nada.
 */
fun balanceAdjustmentFor(accountType: AccountType, current: Long, target: Long): AjusteDeSaldo? {
    if (target == current) return null
    val sube = target > current
    val amount = abs(target - current)
    // Se pregunta por el signo en vez de listar los tipos de cuenta: así, el día que aparezca un
    // tipo nuevo, este código hereda la convención que se le ponga a `signedDelta` en vez de
    // quedarse con una copia de la de hoy.
    val gastoBaja = signedDelta(accountType, TransactionType.EXPENSE, 1L) < 0
    val type = if (sube == gastoBaja) TransactionType.INCOME else TransactionType.EXPENSE
    return AjusteDeSaldo(type, amount)
}

/**
 * Evento real y visible que deja el saldo de [account] en [target], o null si no hay nada que
 * ajustar. El saldo se deriva de los eventos, así que corregirlo es registrar un movimiento más —
 * nunca sobrescribir un número.
 *
 * **Es el único constructor de ajustes de la app**, y lo llaman las dos rutas que cuadran un
 * saldo (`POST /api/credits/{id}/balance-adjustment` para una deuda, `POST
 * /api/accounts/{id}/balance-adjustment` para el resto). Una sola forma de ajustar quiere decir
 * una sola categoría reservada, una sola descripción y un solo lugar donde revisar que un ajuste
 * no ensucie los gastos del período.
 *
 * Origen y estado de conciliación siguen a [com.jvillada.movi.shared.model.openingEventFor]: es
 * el mismo tipo de asiento declarado por la persona dueña de la cuenta, no un movimiento
 * observado del banco. La categoría sí se aparta (ver [ADJUSTMENT_CATEGORY]) — y de ahí sale que
 * el ajuste NO cuente como gasto ni como ingreso del período: `isCashFlow` la excluye entera, en
 * cualquier tipo de cuenta.
 */
fun balanceAdjustmentEventFor(
    account: Account,
    current: Long,
    target: Long,
    now: Long,
): FinancialEvent? {
    val adjustment = balanceAdjustmentFor(account.type, current, target) ?: return null
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
