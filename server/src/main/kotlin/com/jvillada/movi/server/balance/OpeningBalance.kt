package com.jvillada.movi.server.balance

import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.EventSource
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.ReconciliationStatus
import com.jvillada.movi.shared.model.TransactionType
import java.util.UUID
import kotlin.math.abs

/**
 * Event representing an account's opening balance, or null when there is nothing to record.
 * Balances are derived from events, so a declared starting balance must exist as a real event:
 * assets open with an INCOME ("Saldo inicial"), credit cards with an EXPENSE ("Deuda inicial" —
 * EXPENSE raises card debt per [signedDelta]).
 */
fun openingEventFor(account: Account, now: Long): FinancialEvent? {
    if (account.balance == 0L) return null
    val isCard = account.type == AccountType.CREDIT_CARD
    return FinancialEvent(
        id                   = "ev_${UUID.randomUUID()}",
        accountId            = account.id,
        type                 = if (isCard) TransactionType.EXPENSE else TransactionType.INCOME,
        amount               = abs(account.balance),
        currency             = account.currency,
        category             = if (isCard) "Otros" else "Otros ingresos",
        description          = if (isCard) "Deuda inicial" else "Saldo inicial",
        timestamp            = now,
        source               = EventSource.MANUAL,
        reconciliationStatus = ReconciliationStatus.RECONCILED,
    )
}
