package com.jvillada.movi.server.reminders

import com.jvillada.movi.server.balance.computeBalances
import com.jvillada.movi.server.balance.loadNonVoidedEvents
import com.jvillada.movi.server.balance.toAccount
import com.jvillada.movi.server.credits.toCardTerms
import com.jvillada.movi.server.db.Accounts
import com.jvillada.movi.server.db.Cards
import com.jvillada.movi.server.db.dbQuery
import com.jvillada.movi.shared.model.CARD_RULE_PREFIX
import com.jvillada.movi.shared.model.CardTerms
import com.jvillada.movi.shared.model.RecurringRule
import com.jvillada.movi.shared.model.TransactionType
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.selectAll

/**
 * Regla recurrente sintética para el pago de una tarjeta — el equivalente de [virtualRuleFor]
 * para card_terms. La diferencia de fondo: la «cuota» de una tarjeta no es fija como la de un
 * préstamo, así que el monto es [currentDebt] — la deuda actual derivada de los eventos de la
 * cuenta.
 *
 * **Y por eso viaja marcado con `montoEsSaldo`.** «Lo que habría que pagar para quedar al día» es
 * cierto y aun así engaña cuando se pinta bajo el rótulo «Próximos pagos»: el dueño vio
 * $27.501.150 anunciados como su próximo pago, cuando el mínimo de esa tarjeta ronda el 5 %.
 */
fun virtualRuleForCard(terms: CardTerms, accountName: String, currentDebt: Long): RecurringRule =
    RecurringRule(
        id         = "$CARD_RULE_PREFIX${terms.accountId}",
        name       = "Pago tarjeta $accountName",
        category   = "Créditos",
        amount     = currentDebt,
        // Es el SALDO, no la cuota: quien lo muestre tiene que saberlo. Ver `montoEsSaldo`.
        montoEsSaldo = true,
        dayOfMonth = terms.paymentDay,
        type       = TransactionType.EXPENSE,
        // Igual que en créditos: la decisión de avisar vive en card_terms, no en la regla.
        remindMe   = terms.remindMe,
    )

/**
 * Pares (regla virtual, lastRemindedPeriod) de todas las tarjetas del usuario **con deuda**.
 *
 * Una tarjeta en $0 se excluye: no hay nada que pagar, y un recordatorio de "$0" sería ruido.
 * La deuda se toma en la moneda de la cuenta (una tarjeta USD debe dólares, no pesos) — el
 * mismo criterio que `cardSummaryFor`.
 */
suspend fun loadCardRulePairs(userId: String): List<Pair<RecurringRule, String?>> {
    val rows = dbQuery {
        Cards.join(Accounts, JoinType.INNER, Cards.accountId, Accounts.id)
            .selectAll()
            .where { Cards.userId eq userId }
            .map { row -> Triple(row.toCardTerms(), row.toAccount(), row[Cards.lastRemindedPeriod]) }
    }
    if (rows.isEmpty()) return emptyList()
    val eventsByAccount = loadNonVoidedEvents(userId).groupBy { it.accountId }
    return rows.mapNotNull { (terms, account, lastReminded) ->
        val debt = computeBalances(account.type, eventsByAccount[account.id] ?: emptyList())[account.currency] ?: 0L
        if (debt <= 0L) null
        else virtualRuleForCard(terms, account.name, debt) to lastReminded
    }
}
