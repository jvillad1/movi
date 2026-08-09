package com.jvillada.movi.shared.model

/**
 * ¿Este movimiento es **flujo de caja del mes** — plata que entró o salió del bolsillo?
 *
 * No todo evento es un ingreso o un egreso. El saldo de una cuenta de deuda (LOAN,
 * CREDIT_CARD) también se mueve con eventos, pero mover deuda entre periodos nunca fue
 * ingreso ni gasto: es la misma plata cambiando de mano, o intereses que ya se reflejan
 * en cuánto se debe.
 *
 * Reglas, y el porqué de cada una:
 *
 * - **LOAN → nunca.** Un desembolso, la causación de intereses o un ajuste al saldo real
 *   del banco no son consumo; y el abono a la cuota, que llega como INCOME porque baja la
 *   deuda, mucho menos es un ingreso. Contarlos convertía un ajuste de $60.000.000 en
 *   "Ingresos del mes: $60.000.000".
 *
 * - **CREDIT_CARD → solo la compra (EXPENSE).** La compra con tarjeta sí es gasto real:
 *   es el momento en que el hogar consumió, y en Colombia la tarjeta es el instrumento
 *   dominante — excluirla dejaría "Egresos del mes" prácticamente vacío. El **pago** de la
 *   tarjeta, en cambio, entra como INCOME (baja la deuda) y no es ingreso de nadie: es un
 *   traslado que cancela deuda ya contada como gasto cuando se compró. Contarlo inflaba
 *   los ingresos y además duplicaba la salida.
 *
 * - **Cuentas de activo (CASH, CHECKING, SAVINGS, INVESTMENT) → siempre.** Ahí INCOME es
 *   plata que entró y EXPENSE plata que salió, sin ambigüedad.
 *
 * Los saldos de las cuentas NO usan esta función: se siguen derivando de todos los eventos
 * vía `signedDelta`/`computeBalances`. Esto solo decide qué se suma como ingreso/egreso.
 */
fun isCashFlow(accountType: AccountType, type: TransactionType): Boolean = when (accountType) {
    AccountType.LOAN        -> false
    AccountType.CREDIT_CARD -> type == TransactionType.EXPENSE
    else                    -> true
}
