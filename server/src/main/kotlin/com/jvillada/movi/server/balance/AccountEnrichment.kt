package com.jvillada.movi.server.balance

import com.jvillada.movi.server.db.Accounts
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.FinancialEvent
import org.jetbrains.exposed.sql.ResultRow

/** Fila de `accounts` → wire [Account] (balance crudo de la fila; ver [enrichWith] para el derivado). */
fun ResultRow.toAccount() = Account(
    id       = this[Accounts.id],
    name     = this[Accounts.name],
    type     = AccountType.valueOf(this[Accounts.type]),
    balance  = this[Accounts.balance],
    currency = this[Accounts.currency],
)

/** Reemplaza el balance almacenado por los derivados de eventos (por moneda + estimado COP). */
fun enrichWith(base: Account, events: List<FinancialEvent>, rate: Double): Account {
    val balances = computeBalances(base.type, events)
    return base.copy(
        balance            = balances["COP"] ?: 0L,
        balancesByCurrency = balances,
        estimatedTotalCop  = estimatedTotalCop(balances, rate),
    )
}
