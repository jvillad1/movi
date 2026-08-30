package com.jvillada.movi.shared.model

/** Nombre de la categoría que marca el pago del extracto de una tarjeta de crédito. */
const val CARD_PAYMENT_CATEGORY = "Pago de tarjeta"

/**
 * Categoría del evento de apertura que [openingEventFor] genera al crear una cuenta con saldo —
 * tanto para el activo ("Saldo inicial") como para la deuda ("Deuda inicial"; la descripción
 * distingue, la categoría es la misma para las dos). Existe para que [isCashFlow] pueda reconocer
 * y excluir el evento sin importar el tipo de cuenta (ver F54).
 */
const val OPENING_CATEGORY = "Saldo inicial"

/**
 * Categoría reservada de las **dos patas de un traspaso** (ver [transferLegsFor]): mover plata
 * entre cuentas propias — ahorros → CDT, ahorros → efectivo — no es ni ingreso ni egreso, pero
 * los dos saldos sí se mueven.
 *
 * Es *reservada*: nadie la escribe a mano. Se crea solo desde `POST /api/transfers`, y ni el POST
 * de eventos sueltos ni el `PUT /api/events/{id}/category` dejan entrar o salir de ella — sacar
 * una pata de acá reviviría el gasto fantasma que este cambio vino a matar, y meter un evento
 * suelto acá fabricaría medio traspaso sin la otra pata que lo compensa.
 */
const val TRANSFER_CATEGORY = "Traspaso"

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
 * - **Categoría [TRANSFER_CATEGORY] → nunca**, sin importar tipo de cuenta ni tipo de
 *   movimiento. Un traspaso es la misma plata cambiando de cuenta: sale de ahorros y entra al
 *   CDT en el mismo instante. Sin esta regla las dos patas se contaban, y una sola movida de
 *   $5.000.000 inflaba a la vez "Ingresos del mes" y "Gastos del mes" en esa cifra —el neto
 *   quedaba bien, las dos cifras que el dueño lee estaban mal— y además el presupuesto de la
 *   categoría con la que se hubiera anotado el egreso se comía plata que nunca se gastó. Los
 *   **saldos** sí se mueven, y tienen que moverse: cada pata es un evento normal de su cuenta
 *   y `signedDelta`/`computeBalances` no pasan por acá.
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
 * - **Categoría [ORPHANED_LEG_CATEGORY] → nunca**, sin importar tipo de cuenta ni tipo de
 *   movimiento. Es la pata de un traspaso que **sobrevivió al borrado de la cuenta de la otra
 *   punta** (ver `desenlazarPatasHermanas` en `AccountRoutes.kt`). Nació sin ser flujo de caja, y
 *   perder a la hermana es un hecho sobre el REGISTRO de Movi, no sobre la plata: nada se ganó ni
 *   se gastó el día que el dueño borró una cuenta. Durante una ola esta categoría estuvo del otro
 *   lado, con el argumento de que «con la otra cuenta fuera de Movi, esa plata salió del perímetro
 *   que la app lleva». El argumento se cae solo en el caso que la ola 14 volvió probable: un
 *   crédito de $257.000.000 desembolsado a la cuenta corriente, y después el crédito borrado. Ahí
 *   la plata no salió del perímetro — **entró**, y es prestada. Medido: «Ingresos del mes»
 *   pasaba de $12.400.000 a **$269.400.000** por un borrado, o sea plata prestada presentada como
 *   plata ganada. El **saldo** de la cuenta sobreviviente sigue moviéndose (esta función no lo
 *   toca), que es justo lo que tiene que pasar: la plata está ahí.
 *
 *   Es la única de las cuatro reservadas de la que se puede **salir**: `PUT
 *   /api/events/{id}/category` bloquea recategorizar una pata de traspaso viva, pero no una
 *   huérfana. Ese es el escape para el caso contrario —el traspaso a una cuenta que dejó de ser
 *   del hogar, donde la plata SÍ se fue—: el dueño le pone la categoría real y vuelve a contar,
 *   en un toque. Por defecto no se inventa un ingreso; contarlo es una decisión suya, no del
 *   borrado.
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
 *   dominante — excluirla dejaría "Gastos del mes" prácticamente vacío. El **pago** de la
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
    category == TRANSFER_CATEGORY -> false
    category == OPENING_CATEGORY -> false
    category == CARD_PAYMENT_CATEGORY -> false
    category == ORPHANED_LEG_CATEGORY -> false
    // Por nombre y no solo por tipo de cuenta: un descuento de nómina vive en una cuenta LOAN,
    // que ya está excluida más abajo, pero dejarlo implícito haría que la exclusión dependiera de
    // dónde quedó guardado. La plata retenida del sueldo NUNCA es gasto ni ingreso del mes.
    category == PAYROLL_DEDUCTION_CATEGORY -> false
    accountType == AccountType.LOAN        -> false
    accountType == AccountType.CREDIT_CARD -> type == TransactionType.EXPENSE
    else                                    -> true
}

/**
 * El descuento de una **libranza**: la cuota que el empleador retiene del sueldo **antes** de
 * depositarlo.
 *
 * El dueño lo explicó así: *«las deducciones del pago de la cuota se aplican en tu cuenta de
 * nómina antes de recibir el dinero, es como si se pagara la cuota mensual automáticamente y el
 * dinero no alcanza a llegar a tu cuenta»*.
 *
 * Eso rompe el modelo normal de una cuota **en las dos direcciones**:
 *
 * - **Registrarla como gasto cuenta doble.** El salario que llega a la cuenta ya viene NETO, así
 *   que sumar el sueldo como ingreso y la cuota como gasto descuenta dos veces la misma plata.
 * - **No registrar nada deja la deuda congelada.** El crédito se paga todos los meses aunque el
 *   dueño no toque la app.
 *
 * La forma correcta es un movimiento que **baje la deuda sin tocar ninguna cuenta de dinero**: un
 * INCOME sobre la cuenta del préstamo. `signedDelta` ya lo lee como «la deuda baja», e [isCashFlow]
 * ya deja fuera del mes todo lo que pasa en una cuenta LOAN — así que no hace falta ninguna regla
 * nueva para que las cifras del mes queden bien.
 */
const val PAYROLL_DEDUCTION_CATEGORY = "Descuento de nómina"
