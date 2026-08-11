package com.jvillada.movi.server.balance

import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.signedDelta

// signedDelta se movió a :core (com.jvillada.movi.shared.model.Balance.kt) para que el cliente
// offline-first y el server compartan una sola definición del signo por tipo de cuenta — ver el
// KDoc de la función allá para el porqué (Hallazgo bloqueante 2 de la revisión de esta rama).

/** Per-currency balance derived from an account's (already non-voided) events. */
fun computeBalances(accountType: AccountType, events: List<FinancialEvent>): Map<String, Long> =
    events.groupBy { it.currency }
        .mapValues { (_, evs) -> evs.sumOf { signedDelta(accountType, it.type, it.amount) } }

/**
 * Estimated total COP value: COP balance + each foreign balance converted at [usdToCop].
 * Returns null when there is no foreign-currency balance (nothing to estimate).
 * Only USD is converted; any other foreign currency contributes 0 (extend when needed).
 */
fun estimatedTotalCop(balances: Map<String, Long>, usdToCop: Double): Long? {
    val hasForeign = balances.keys.any { it != "COP" }
    if (!hasForeign) return null
    val cop = balances["COP"] ?: 0L
    val foreign = balances.entries.sumOf { (cur, amt) ->
        when (cur) {
            "COP" -> 0L
            "USD" -> Math.round(amt * usdToCop)
            else  -> 0L
        }
    }
    return cop + foreign
}

/** COP-equivalent value of an account: the estimate if it has foreign balances, else the COP balance. */
fun accountCopValue(accountType: AccountType, events: List<FinancialEvent>, usdToCop: Double): Long {
    val balances = computeBalances(accountType, events)
    return estimatedTotalCop(balances, usdToCop) ?: (balances["COP"] ?: 0L)
}

/**
 * Patrimonio: activos + deudas, no "suma de todos los saldos" (Hallazgo menor 4 de la revisión
 * de `feat/ajustar-saldo`). [accountCopValue] de una cuenta LOAN/CREDIT_CARD es deuda positiva
 * (ver [signedDelta]), así que sumarla de frente da "activos + deudas" en vez del neto. Mismo
 * criterio que `assetsDebtsNet` del lado del cliente (`MoneyDisplay.kt`), reimplementado acá
 * porque ese vive en `:shared` (Compose) y este código es server-only.
 */
fun netWorth(
    accountRows: List<Pair<String, AccountType>>,
    eventsByAccount: Map<String, List<FinancialEvent>>,
    usdToCop: Double,
): Long = accountRows.sumOf { (accId, accType) ->
    val value = accountCopValue(accType, eventsByAccount[accId] ?: emptyList(), usdToCop)
    if (accType == AccountType.LOAN || accType == AccountType.CREDIT_CARD) -value else value
}
