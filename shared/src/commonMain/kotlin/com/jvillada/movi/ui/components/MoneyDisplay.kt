package com.jvillada.movi.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.theme.MinTextMute

/** Thousands-grouped absolute value: 222933 -> "222.933". Sign is the caller's concern. */
private fun groupThousands(amount: Long): String {
    val abs = kotlin.math.abs(amount)
    return abs.toString().reversed().chunked(3).joinToString(".").reversed()
}

/** Currency-aware money text: COP -> "$222.933", USD -> "US$181", other -> "EUR 50". */
fun formatMoney(amount: Long, currency: String): String = when (currency) {
    "COP" -> formatCOP(amount)
    "USD" -> "US$" + groupThousands(amount)
    else  -> "$currency " + groupThousands(amount)
}

/**
 * (activos, deudas, neto) across accounts.
 * Assets = COP balance of non-card accounts. Debts = each card's COP estimate
 * (or its COP balance when there is nothing foreign to estimate). Net = assets − deudas.
 */
fun assetsDebtsNet(accounts: List<Account>): Triple<Long, Long, Long> {
    val activos = accounts.filter { it.type != AccountType.CREDIT_CARD }.sumOf { it.balance }
    val deudas = accounts.filter { it.type == AccountType.CREDIT_CARD }
        .sumOf { it.estimatedTotalCop ?: it.balance }
    return Triple(activos, deudas, activos - deudas)
}

/** The display value for a card's debt and whether it is a TRM estimate. */
fun cardDebt(account: Account): Pair<Long, Boolean> =
    (account.estimatedTotalCop ?: account.balance) to (account.estimatedTotalCop != null)

/** USD→COP rate the server applied, derived from the estimate. Null when not applicable. */
fun impliedTrm(account: Account): Long? {
    val est = account.estimatedTotalCop ?: return null
    val usd = account.balancesByCurrency["USD"] ?: return null
    if (usd == 0L) return null
    val cop = account.balancesByCurrency["COP"] ?: 0L
    return (est - cop) / usd
}

/** True when the account holds any non-zero balance in a currency other than COP. */
fun hasForeignBalance(account: Account): Boolean =
    account.balancesByCurrency.any { (cur, amt) -> cur != "COP" && amt != 0L }

/** Per-currency balance lines + the implied TRM, for the account-detail hero card. */
@Composable
fun CurrencyBreakdown(account: Account) {
    val cop = account.balancesByCurrency["COP"] ?: 0L
    val usd = account.balancesByCurrency["USD"] ?: 0L
    val trm = impliedTrm(account)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (cop != 0L) BreakdownRow("En pesos", formatMoney(cop, "COP"))
        if (usd != 0L) BreakdownRow("En dólares", formatMoney(usd, "USD"))
        if (trm != null) BreakdownRow("TRM aplicada", "≈$" + groupThousands(trm))
    }
}

@Composable
private fun BreakdownRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = label, fontSize = 12.sp, color = MinTextMute)
        MonoText(text = value, fontSize = 12f, color = MinTextMute)
    }
}
