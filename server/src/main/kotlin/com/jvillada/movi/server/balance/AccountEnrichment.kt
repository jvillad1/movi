package com.jvillada.movi.server.balance

import com.jvillada.movi.server.db.Accounts
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.normalizarCondicion
import org.jetbrains.exposed.sql.ResultRow

/** Fila de `accounts` → wire [Account] (balance crudo de la fila; ver [enrichWith] para el derivado). */
fun ResultRow.toAccount() = Account(
    id       = this[Accounts.id],
    name     = this[Accounts.name],
    type     = AccountType.valueOf(this[Accounts.type]),
    balance  = this[Accounts.balance],
    currency = this[Accounts.currency],
    // Mismo helper que usan la escritura (`POST`/`PUT /{id}/conditioned-to`) y el espejo local:
    // una fila con espacios en blanco no puede salir de «Tu plata» solo en un lado.
    condicionadaA = normalizarCondicion(this[Accounts.conditionedTo]),
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
