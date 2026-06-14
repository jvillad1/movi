package com.jvillada.movi.server.balance

import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.TransactionType

/**
 * One event's contribution to its account balance.
 * Asset accounts: INCOME adds, EXPENSE subtracts.
 * CREDIT_CARD / LOAN (balance = positive debt): EXPENSE raises debt, INCOME (payment) lowers it.
 */
fun signedDelta(accountType: AccountType, type: TransactionType, amount: Long): Long =
    when (accountType) {
        AccountType.CREDIT_CARD,
        AccountType.LOAN -> if (type == TransactionType.EXPENSE) amount else -amount
        else             -> if (type == TransactionType.INCOME) amount else -amount
    }

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
