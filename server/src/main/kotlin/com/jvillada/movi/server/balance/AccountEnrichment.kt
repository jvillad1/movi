package com.jvillada.movi.server.balance

import com.jvillada.movi.server.db.Accounts
import com.jvillada.movi.shared.model.ADJUSTMENT_CATEGORY
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
    // La edad de la versión guardada. Viaja en toda respuesta para que el espejo local del
    // cliente la conserve: ver `Account.lastEditedAt`.
    lastEditedAt = this[Accounts.lastEditedAt],
)

/**
 * Reemplaza el balance almacenado por los derivados de eventos (por moneda + estimado COP) y
 * agrega **cuándo se cuadró por última vez** y **desde cuándo existe** la cuenta.
 *
 * Los dos sellos salen de los mismos eventos que ya están en la mano —cero consultas nuevas— y por
 * eso viajan en toda respuesta que pase por acá: la pantalla «Cuadre de saldos» y el aviso del
 * Inicio leen la lista de cuentas que ya pedían, sin una llamada más. Ver
 * [Account.lastAdjustmentAt] y [Account.firstEventAt] para qué significa cada uno.
 *
 * [events] son los eventos **no anulados** de la cuenta (ver `loadNonVoidedEvents`): un ajuste que
 * se anuló no cuadró nada, así que no puede seguir contando como la última vez que se miró.
 */
fun enrichWith(base: Account, events: List<FinancialEvent>, rate: Double): Account {
    val balances = computeBalances(base.type, events)
    return base.copy(
        balance            = balances["COP"] ?: 0L,
        balancesByCurrency = balances,
        estimatedTotalCop  = estimatedTotalCop(balances, rate),
        lastAdjustmentAt   = events.filter { it.category == ADJUSTMENT_CATEGORY }.maxOfOrNull { it.timestamp },
        firstEventAt       = events.minOfOrNull { it.timestamp },
    )
}
