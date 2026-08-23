package com.jvillada.movi.server.reminders

import com.jvillada.movi.server.credits.toCreditTerms
import com.jvillada.movi.server.db.Accounts
import com.jvillada.movi.server.db.Credits
import com.jvillada.movi.server.db.dbQuery
import com.jvillada.movi.shared.model.CREDIT_RULE_PREFIX
import com.jvillada.movi.shared.model.CreditTerms
import com.jvillada.movi.shared.model.RecurringRule
import com.jvillada.movi.shared.model.TransactionType
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.selectAll

/**
 * Regla recurrente sintética para la cuota de un crédito. NO existe en recurring_rules:
 * se construye al vuelo desde credit_terms para entrar al mismo motor de DueDates.
 */
fun virtualRuleFor(terms: CreditTerms, accountName: String): RecurringRule =
    RecurringRule(
        id         = "$CREDIT_RULE_PREFIX${terms.accountId}",
        name       = "Cuota $accountName",
        category   = "Créditos",
        amount     = terms.installment,
        dayOfMonth = terms.dayOfMonth,
        type       = TransactionType.EXPENSE,
        // La regla es sintética, pero la decisión de avisar es del dueño y vive en credit_terms.
        remindMe   = terms.remindMe,
    )

/** Pares (regla virtual, lastRemindedPeriod) de todos los créditos del usuario. */
suspend fun loadCreditRulePairs(userId: String): List<Pair<RecurringRule, String?>> = dbQuery {
    Credits.join(Accounts, JoinType.INNER, Credits.accountId, Accounts.id)
        .selectAll()
        .where { Credits.userId eq userId }
        .map { row ->
            virtualRuleFor(row.toCreditTerms(), row[Accounts.name]) to row[Credits.lastRemindedPeriod]
        }
}
