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
        // La primera cuota va DESPUÉS del desembolso, no el mismo día. Sin esto, un crédito
        // desembolsado el 1 con pago el día 1 anunciaba su primera cuota para ese mismo día.
        activeFrom = terms.startDate,
    )

/** Pares (regla virtual, lastRemindedPeriod) de todos los créditos del usuario. */
suspend fun loadCreditRulePairs(userId: String): List<Pair<RecurringRule, String?>> = dbQuery {
    Credits.join(Accounts, JoinType.INNER, Credits.accountId, Accounts.id)
        .selectAll()
        .where { Credits.userId eq userId }
        .map { row -> row.toCreditTerms() to row }
        // Una LIBRANZA no entra al barrido de avisos: su cuota ya se pagó sola, retenida del
        // sueldo antes de que la plata llegara a la cuenta. Recordarle al dueño que «pague» algo
        // que el empleador ya descontó es pedirle una acción que no existe — y si la registrara
        // como gasto, descontaría dos veces, porque el salario que ve ya viene neto.
        //
        // La deuda igual tiene que bajar todos los meses: eso se registra desde la tarjeta del
        // crédito, con `POST /api/credits/{id}/payroll-deduction`.
        .filterNot { (terms, _) -> terms.payrollDeduction }
        .map { (terms, row) ->
            virtualRuleFor(terms, row[Accounts.name]) to row[Credits.lastRemindedPeriod]
        }
}
