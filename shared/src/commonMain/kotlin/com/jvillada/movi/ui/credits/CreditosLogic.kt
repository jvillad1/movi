package com.jvillada.movi.ui.credits

import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.CardSummary
import com.jvillada.movi.shared.model.CreditSummary

/**
 * F20 — deuda total en COP: préstamos + tarjetas, **una sola función** para que la pantalla de
 * Créditos y el acceso «Créditos» del Inicio sumen exactamente igual (en la Ola 4 se encontró
 * que daban números distintos; una sola fuente hace imposible que diverjan de nuevo).
 *
 * Por cuenta se usa `estimatedTotalCop ?: balance` — el mismo criterio que `assetsDebtsNet`
 * (Cuentas): una tarjeta en USD aporta su deuda convertida a TRM, no solo su componente COP
 * (que sería $0 y mentiría el total).
 */
fun totalDebtCop(credits: List<CreditSummary>, cards: List<CardSummary>): Long =
    credits.sumOf { debtCopOf(it.account) } + cards.sumOf { debtCopOf(it.account) }

private fun debtCopOf(account: Account): Long = account.estimatedTotalCop ?: account.balance
