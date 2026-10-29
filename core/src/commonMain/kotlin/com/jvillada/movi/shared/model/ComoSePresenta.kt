package com.jvillada.movi.shared.model

/*
 * # Cómo se presenta un movimiento (vuelta 50)
 *
 * En «Últimos movimientos» del Inicio la «Cuota de crédito $980.000» salía con las flechas, sin signo y
 * en gris, igual que «Traspaso $50.000» y «Pago de tarjeta $100.000». Pero por la regla del dueño **la
 * cuota sí es gasto** («es plata que salió», ver [CUOTA_CATEGORY]) y el pago de tarjeta no. La fila
 * decía lo contrario de lo que sumaban los totales, porque la fila de un par miraba la forma (dos patas)
 * y no lo que cuenta.
 *
 * Ahora la fila y los totales preguntan acá. **No es una regla de plata nueva**: es [isCashFlow] —ya
 * derivado en `countsAsCashFlow`— leído con el tipo del movimiento. La regla de «Gastos»
 * ([esGastoDelPeriodo]) y el «Gastaste» del día salen de esta misma función.
 */

/** Cómo se presenta un movimiento en una fila: lo que cuenta en «Gastos» o en «Entró», o nada. */
enum class PresentacionDelMovimiento {
    /** Plata que salió del bolsillo: «−$980.000», en tinta. */
    GASTO,

    /** Plata que entró al bolsillo: «+$X», en verde. */
    INGRESO,

    /** No es gasto ni ingreso: un traspaso, un pago de tarjeta, la pata de una deuda, un ajuste. Sin signo. */
    NEUTRO,
}

/**
 * **Cómo se presenta [evento]**: gasto, ingreso o neutro, con la regla con la que suman «Gastos» y
 * «Entró».
 *
 * - **Neutro** si no es flujo de caja: `countsAsCashFlow` (que el server deriva con [isCashFlow] y el
 *   tipo de cuenta real) y, si se conoce [tipoDeCuenta], [isCashFlow] con él. Sin el tipo, igual se
 *   aplican las exclusiones por categoría (traspaso, pago de tarjeta, ajuste…): se pregunta como si
 *   fuera una cuenta de activo, que es donde esas categorías viven. Es lo mismo que hacía
 *   [esGastoDelPeriodo].
 * - **Ingreso** si entró plata; **gasto** si salió.
 *
 * No mira la moneda ni «Por confirmar»: un cargo en dólares o un aviso sin confirmar se siguen viendo
 * como gasto (con su píldora); que entren a una suma en pesos lo decide cada total.
 */
fun comoSePresenta(evento: FinancialEvent, tipoDeCuenta: AccountType? = null): PresentacionDelMovimiento = when {
    !evento.countsAsCashFlow -> PresentacionDelMovimiento.NEUTRO
    !isCashFlow(tipoDeCuenta ?: AccountType.CHECKING, evento.type, evento.category) -> PresentacionDelMovimiento.NEUTRO
    evento.type == TransactionType.INCOME -> PresentacionDelMovimiento.INGRESO
    else -> PresentacionDelMovimiento.GASTO
}

/**
 * **La pata de un par que cuenta**, o `null` si ninguna cuenta.
 *
 * Un par (las dos patas con el mismo `transferId`) se lista como un renglón, y ese renglón se presenta
 * como la pata que cuenta en los totales:
 *
 * - **Cuota de crédito** ([pagoDeCuotaLegs]): la pata que sale de la cuenta de dinero es un gasto; la
 *   del crédito no es flujo (cuenta LOAN). → la de dinero, gasto.
 * - **Desembolso** ([DESEMBOLSO_CATEGORY]): la pata que entra a la cuenta de dinero es un ingreso; la
 *   del crédito no. → la de dinero, ingreso.
 * - **Pago de tarjeta, traspaso, abono extraordinario**: ninguna pata es flujo. → `null`, neutro.
 *
 * Se mira primero la que sale y después la que entra. [tipos] son los tipos de cuenta por id, si ya
 * llegaron: sin ellos manda `countsAsCashFlow` de cada pata.
 */
fun laPataQueCuenta(
    salida: FinancialEvent,
    entrada: FinancialEvent,
    tipos: Map<String, AccountType> = emptyMap(),
): FinancialEvent? =
    listOf(salida, entrada).firstOrNull { comoSePresenta(it, tipos[it.accountId]) != PresentacionDelMovimiento.NEUTRO }

/** Cómo se presenta un par: como [laPataQueCuenta], o neutro si ninguna cuenta. */
fun comoSePresentaElPar(
    salida: FinancialEvent,
    entrada: FinancialEvent,
    tipos: Map<String, AccountType> = emptyMap(),
): PresentacionDelMovimiento =
    laPataQueCuenta(salida, entrada, tipos)?.let { comoSePresenta(it, tipos[it.accountId]) }
        ?: PresentacionDelMovimiento.NEUTRO
