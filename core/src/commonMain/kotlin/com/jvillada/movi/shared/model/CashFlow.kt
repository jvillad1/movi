package com.jvillada.movi.shared.model

/** Nombre de la categoría que marca el pago del extracto de una tarjeta de crédito. */
const val CARD_PAYMENT_CATEGORY = "Pago de tarjeta"

/**
 * Categoría del evento de apertura que [com.jvillada.movi.server.balance.openingEventFor] genera
 * al crear una cuenta con saldo — tanto para el activo ("Saldo inicial") como para la deuda
 * ("Deuda inicial"; la descripción distingue, la categoría es la misma para las dos). Existe para
 * que [isCashFlow] pueda reconocer y excluir el evento sin importar el tipo de cuenta (ver F54).
 */
const val OPENING_CATEGORY = "Saldo inicial"

/**
 * ¿Este movimiento es **flujo de caja del mes** — plata que entró o salió del bolsillo?
 *
 * No todo evento es un ingreso o un egreso. El saldo de una cuenta de deuda (LOAN,
 * CREDIT_CARD) también se mueve con eventos, pero mover deuda entre periodos nunca fue
 * ingreso ni gasto: es la misma plata cambiando de mano, o intereses que ya se reflejan
 * en cuánto se debe.
 *
 * Reglas, en orden, y el porqué de cada una:
 *
 * - **Categoría [OPENING_CATEGORY] → nunca**, sin importar tipo de cuenta ni tipo de movimiento
 *   (F54). Abrir una cuenta con plata que ya tenías no es un ingreso del mes — ni tampoco, para
 *   una deuda, un gasto del mes: es la foto inicial de algo que ya existía antes de que Movi
 *   empezara a llevarlo, igual que el desembolso de un LOAN de la regla de abajo. Antes de este
 *   fix, crear una cuenta de activo con $1.000.000 inflaba "Ingresos del mes" en esa misma cifra.
 *   Nota de datos: producción está vacía al momento de este cambio, así que no hace falta
 *   migración — los eventos de apertura viejos (si los hay) quedaron con categoría "Otros
 *   ingresos"/"Otros" y NO se benefician de esta regla; ver el commit de F54.
 *
 * - **Categoría [CARD_PAYMENT_CATEGORY] → nunca**, sin importar de qué cuenta salga. Una
 *   compra con tarjeta ya se contó como EXPENSE en la cuenta CREDIT_CARD — el momento real
 *   en que el hogar consumió. Pagar el extracto es plata saliendo de la cuenta de ahorros
 *   otra vez, pero es la *misma* compra, no una nueva: es un traslado que cancela deuda ya
 *   contada, no gasto adicional. Sin esta regla se duplicaba el egreso del mes (~$1.000.000
 *   mensuales en los datos reales) porque esta categoría vive en cuentas de activo, donde
 *   la regla por tipo de cuenta de abajo diría `true`.
 *
 * - **LOAN → nunca.** Un desembolso, la causación de intereses o un ajuste al saldo real
 *   del banco no son consumo; y el abono a la cuota, que llega como INCOME porque baja la
 *   deuda, mucho menos es un ingreso. Contarlos convertía un ajuste de $60.000.000 en
 *   "Ingresos del mes: $60.000.000". A diferencia de la tarjeta, aquí NO hay una compra
 *   previa ya contada en otro evento — la cuota es el único momento en que ese consumo
 *   aparece en el sistema — pero de todos modos no es un ingreso ni un gasto nuevo del mes:
 *   es el pago de una deuda que ya existía antes de que Movi empezara a llevarla.
 *
 * - **CREDIT_CARD → solo la compra (EXPENSE).** La compra con tarjeta sí es gasto real:
 *   es el momento en que el hogar consumió, y en Colombia la tarjeta es el instrumento
 *   dominante — excluirla dejaría "Egresos del mes" prácticamente vacío. El **pago** de la
 *   tarjeta, en cambio, entra como INCOME (baja la deuda) y no es ingreso de nadie: es un
 *   traslado que cancela deuda ya contada como gasto cuando se compró. Contarlo inflaba
 *   los ingresos y además duplicaba la salida. (En la práctica esa fila también trae la
 *   categoría [CARD_PAYMENT_CATEGORY] y ya cae en la primera regla, pero se deja esta rama
 *   como defensa si algún día el pago se registra sin categorizar.)
 *
 * - **Cuentas de activo (CASH, CHECKING, SAVINGS, INVESTMENT) → siempre.** Ahí INCOME es
 *   plata que entró y EXPENSE plata que salió, sin ambigüedad.
 *
 * Los saldos de las cuentas NO usan esta función: se siguen derivando de todos los eventos
 * vía `signedDelta`/`computeBalances`. Esto solo decide qué se suma como ingreso/egreso.
 */
fun isCashFlow(accountType: AccountType, type: TransactionType, category: String): Boolean = when {
    category == OPENING_CATEGORY -> false
    category == CARD_PAYMENT_CATEGORY -> false
    accountType == AccountType.LOAN        -> false
    accountType == AccountType.CREDIT_CARD -> type == TransactionType.EXPENSE
    else                                    -> true
}
