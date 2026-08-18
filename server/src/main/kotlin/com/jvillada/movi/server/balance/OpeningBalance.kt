package com.jvillada.movi.server.balance

import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.EventSource
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.OPENING_CATEGORY
import com.jvillada.movi.shared.model.ReconciliationStatus
import com.jvillada.movi.shared.model.TransactionType
import java.util.UUID
import kotlin.math.abs

/**
 * Event representing an account's opening balance, or null when there is nothing to record.
 * Balances are derived from events, so a declared starting balance must exist as a real event:
 * assets open with an INCOME ("Saldo inicial"), debt accounts (credit card / loan) with an
 * EXPENSE ("Deuda inicial" — EXPENSE raises debt per [signedDelta]).
 *
 * Categoría [OPENING_CATEGORY] para los dos casos (F54): la descripción sigue distinguiendo
 * "Saldo inicial"/"Deuda inicial", pero es la categoría la que [isCashFlow] usa para excluir
 * este evento de ingresos/egresos del mes — abrir una cuenta con plata que ya tenías no es un
 * movimiento de agosto.
 */
fun openingEventFor(account: Account, now: Long): FinancialEvent? {
    if (account.balance == 0L) return null
    val isDebt = account.type == AccountType.CREDIT_CARD || account.type == AccountType.LOAN
    return FinancialEvent(
        id                   = "ev_${UUID.randomUUID()}",
        accountId            = account.id,
        type                 = if (isDebt) TransactionType.EXPENSE else TransactionType.INCOME,
        amount               = abs(account.balance),
        currency             = account.currency,
        category             = OPENING_CATEGORY,
        description          = if (isDebt) "Deuda inicial" else "Saldo inicial",
        timestamp            = now,
        source               = EventSource.MANUAL,
        reconciliationStatus = ReconciliationStatus.RECONCILED,
    )
}
