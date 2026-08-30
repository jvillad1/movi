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
        .filter { (terms, _) -> entraAlBarridoDeAvisos(terms) }
        .map { (terms, row) ->
            virtualRuleFor(terms, row[Accounts.name]) to row[Credits.lastRemindedPeriod]
        }
}

/**
 * ¿Esta cuota le genera un aviso de vencimiento al dueño?
 *
 * Función aparte del `filter` para poder testearla sin base de datos — el filtro vivía dentro de
 * un `dbQuery` y era, en la práctica, código sin prueba.
 *
 * Dice que **no** en los dos casos en que la cuota no sale de su cuenta:
 *
 * - **Libranza**: la retiene el empleador del sueldo antes de depositarlo. Recordarle que
 *   «pague» algo que ya le descontaron es pedirle una acción que no existe — y si la registrara
 *   como gasto, descontaría dos veces, porque el salario que ve ya viene neto.
 * - **La paga otro**: Skandia gira las dos hipotecas de Davibank, su esposa paga el Cotrafa que
 *   está a nombre de él. Mismo razonamiento, otra fuente.
 *
 * En los dos casos la deuda igual tiene que bajar todos los meses, y eso se registra desde la
 * tarjeta del crédito con `POST /api/credits/{id}/payroll-deduction`.
 *
 * Un nombre **en blanco** cuenta como «lo pago yo», no como un tercero anónimo. El server ya
 * normaliza a null lo que llegue vacío, pero apagar los avisos de un crédito que el dueño sí
 * paga —por un espacio de más— es el error caro de esta función, y no puede depender de que la
 * normalización de otro archivo siga estando.
 */
fun entraAlBarridoDeAvisos(terms: CreditTerms): Boolean =
    !terms.payrollDeduction && terms.paidBy.isNullOrBlank()
