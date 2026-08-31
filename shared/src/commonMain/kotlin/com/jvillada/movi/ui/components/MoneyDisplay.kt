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

/**
 * Currency-aware money text: COP -> "$222.933", USD -> "US$181", other -> "EUR 50".
 *
 * A propósito usa `groupThousands` (siempre positivo) y NO [formatCOP] — desde F36 [formatCOP]
 * ya trae su propio signo, y este helper necesita quedarse sin signo para que [signedMoney]
 * (el único llamador que le importa el signo) pueda ponerlo una sola vez sin duplicarlo.
 */
fun formatMoney(amount: Long, currency: String): String = when (currency) {
    "COP" -> "$" + groupThousands(amount)
    "USD" -> "US$" + groupThousands(amount)
    else  -> "$currency " + groupThousands(amount)
}

/** Sign-aware money text: -181 USD -> "−US$181". [formatMoney] strips signs; this preserves them. */
fun signedMoney(amount: Long, currency: String): String =
    (if (amount < 0) "−" else "") + formatMoney(amount, currency)

/** True for account types whose balance represents debt (positive = owed). */
fun isDebtAccount(type: AccountType): Boolean =
    type == AccountType.CREDIT_CARD || type == AccountType.LOAN

/**
 * (activos, deudas, neto) across accounts.
 * Assets = COP balance of non-debt accounts. Debts = each debt account's COP estimate
 * (or its COP balance when there is nothing foreign to estimate). Net = assets − deudas.
 */
fun assetsDebtsNet(accounts: List<Account>): Triple<Long, Long, Long> {
    val activos = accounts.filter { !isDebtAccount(it.type) }.sumOf { it.balance }
    val deudas = accounts.filter { isDebtAccount(it.type) }
        .sumOf { it.estimatedTotalCop ?: it.balance }
    return Triple(activos, deudas, activos - deudas)
}

/**
 * **El total de la deuda, en pesos, estimado cuando hace falta.** La convención de la tarjeta
 * grande del detalle de una cuenta: un solo número, siempre en COP, con «≈» cuando lo de adentro
 * pasó por la TRM.
 *
 * Ver [saldoEnSuMoneda], que es la OTRA convención de la app y está a propósito acá al lado.
 */
fun cardDebt(account: Account): Pair<Long, Boolean> =
    (account.estimatedTotalCop ?: account.balance) to (account.estimatedTotalCop != null)

/**
 * **El saldo de una cuenta en la moneda de la cuenta**, con la moneda con que hay que rotularlo.
 *
 * ## Por qué esto NO es [cardDebt], y por qué las dos conviven
 *
 * La «Master Black 3684 USD» del dueño sale acá como `181 a "USD"` y en [cardDebt] como
 * `≈1.5xx.xxx COP`. Son dos cifras distintas para la misma tarjeta, y las dos son ciertas —
 * contestan preguntas distintas:
 *
 * - **[cardDebt] contesta «¿cuánto pesa esto en mi patrimonio?»**, que solo se puede contestar en
 *   una sola moneda y por lo tanto es siempre una estimación cuando hay dólares adentro. Es lo que
 *   necesita la tarjeta grande del detalle, que es la pantalla del patrimonio de esa cuenta, y por
 *   eso ahí abajo va además el desglose por moneda (`CurrencyBreakdown`) y la TRM aplicada.
 * - **Esto contesta «¿cuánto hay/debo acá?»**, exacto y sin TRM. Es lo que necesita un renglón de
 *   una LISTA de cuentas —el selector de «¿de dónde sale la plata?»—, donde no hay lugar para un
 *   desglose ni para explicar una estimación, y donde poner un número aproximado sin poder decir
 *   que lo es sería peor que decir la cifra exacta con su símbolo.
 *
 * O sea: **una cifra por pantalla, elegida por lo que la pantalla pregunta**, y las dos con la
 * moneda a la vista para que nadie las lea como si fueran la misma.
 *
 * ## El respaldo, que antes mentía
 *
 * Sin red, [com.jvillada.movi.shared.repository.LocalRepository] arma el `Account` desde la fila
 * de SQLDelight, que guarda `balance` y `currency` pero **no** `balancesByCurrency`. Con un
 * respaldo `?: account.balance` a secas, el componente en PESOS salía rotulado con la moneda de la
 * cuenta: «Debes US$15.534.069» sobre una cifra en COP. Por eso el respaldo devuelve también su
 * propia moneda: si lo único que se sabe es el componente en pesos, se dice en pesos. Menos
 * informativo que el número en dólares, y verdadero, que es el orden correcto.
 *
 * @return el monto y la moneda con la que hay que escribirlo.
 */
fun saldoEnSuMoneda(account: Account): Pair<Long, String> =
    account.balancesByCurrency[account.currency]?.let { it to account.currency }
        ?: (account.balance to "COP")

/**
 * **En una cuenta de deuda el signo va al revés**, y este es el único lugar donde eso está escrito.
 *
 * En una tarjeta o un préstamo, `balance` positivo es lo que se DEBE; un balance **negativo** es
 * saldo a favor — el dueño sobrepagó la Nu. Escribirlo con el signo crudo produce «Debes
 * −$50.000», que dice dos cosas opuestas en cuatro palabras.
 *
 * Devuelve las dos mitades y no el texto entero porque los dos lugares que lo dicen lo dicen
 * distinto, y está bien que así sea: la tarjeta grande del detalle de la cuenta tiene el espacio y
 * el color para un «+$50.000» en verde; el renglón del selector de cuentas, que es una línea de
 * 12 sp bajo un nombre, necesita la palabra («A favor $50.000»), porque ahí un «+» suelto no se
 * lee. Lo que **no** puede volver a pasar es que cada uno decida por su cuenta qué significa el
 * menos.
 *
 * @property aFavor la cifra está a favor del dueño, no en su contra.
 * @property magnitud el monto sin signo, ya escrito en su moneda.
 */
data class SaldoDeDeuda(val aFavor: Boolean, val magnitud: String)

/** Ver [SaldoDeDeuda]. */
fun saldoDeDeuda(monto: Long, currency: String = "COP"): SaldoDeDeuda =
    SaldoDeDeuda(aFavor = monto < 0, magnitud = formatMoney(kotlin.math.abs(monto), currency))

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
    val balances = account.balancesByCurrency
    val cop = balances["COP"] ?: 0L
    val trm = impliedTrm(account)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (cop != 0L) BreakdownRow("En pesos", signedMoney(cop, "COP"))
        balances.forEach { (cur, amt) ->
            if (cur != "COP" && amt != 0L) {
                val label = if (cur == "USD") "En dólares" else "En $cur"
                BreakdownRow(label, signedMoney(amt, cur))
            }
        }
        if (trm != null) BreakdownRow("TRM aplicada", "≈$" + groupThousands(trm) + "/USD")
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
