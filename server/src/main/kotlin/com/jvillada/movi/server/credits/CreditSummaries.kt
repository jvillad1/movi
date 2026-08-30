package com.jvillada.movi.server.credits

import com.jvillada.movi.server.db.Credits
import com.jvillada.movi.shared.model.CreditTerms
import org.jetbrains.exposed.sql.ResultRow

/**
 * Progreso de pago de un crédito: 1 − deuda/principal, clampado a [0, 1].
 * Null cuando el principal no es positivo (términos inválidos/incompletos).
 * La deuda llega derivada de los eventos de la cuenta LOAN — nunca se almacena aquí.
 */
fun paidPctFor(principal: Long, debt: Long): Double? {
    if (principal <= 0L) return null
    return (1.0 - debt.toDouble() / principal.toDouble()).coerceIn(0.0, 1.0)
}

fun ResultRow.toCreditTerms() = CreditTerms(
    accountId  = this[Credits.accountId],
    bank       = this[Credits.bank],
    principal  = this[Credits.principal],
    rateEa     = this[Credits.rateEa],
    termMonths = this[Credits.termMonths],
    installment = this[Credits.installment],
    dayOfMonth = this[Credits.dayOfMonth],
    startDate  = this[Credits.startDate],
    notes      = this[Credits.notes],
    remindMe   = this[Credits.remindMe],
    // Nullable en la base, `false` en el wire: las filas viejas no la tienen.
    payrollDeduction = this[Credits.payrollDeduction] ?: false,
    paidBy = this[Credits.paidBy]?.takeIf { it.isNotBlank() },
)
