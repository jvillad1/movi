package com.jvillada.movi.server.credits

import com.jvillada.movi.server.db.Cards
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.CardSummary
import com.jvillada.movi.shared.model.CardTerms
import org.jetbrains.exposed.sql.ResultRow

fun ResultRow.toCardTerms() = CardTerms(
    accountId   = this[Cards.accountId],
    bank        = this[Cards.bank],
    creditLimit = this[Cards.creditLimit],
    cutoffDay   = this[Cards.cutoffDay],
    paymentDay  = this[Cards.paymentDay],
    notes       = this[Cards.notes],
    remindMe    = this[Cards.remindMe],
)

/**
 * Resumen de una tarjeta ya enriquecida ([com.jvillada.movi.server.balance.enrichWith]).
 *
 * `available` = cupo − deuda **en la moneda de la cuenta**: `account.balance` es solo el
 * componente COP, así que para una tarjeta USD la deuda sale de `balancesByCurrency` — el cupo
 * de una Mastercard en dólares es un número en dólares. Sin cupo declarado, null. Puede ser
 * negativo (sobregiro real): no se recorta a 0.
 */
fun cardSummaryFor(enriched: Account, terms: CardTerms?): CardSummary {
    val debt = enriched.balancesByCurrency[enriched.currency] ?: enriched.balance
    return CardSummary(
        account   = enriched,
        terms     = terms,
        available = terms?.creditLimit?.let { it - debt },
    )
}
