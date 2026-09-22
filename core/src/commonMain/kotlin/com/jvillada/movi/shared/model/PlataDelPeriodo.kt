package com.jvillada.movi.shared.model

/**
 * # «Tu plata» y lo que pasó con ella en el período
 *
 * ## La regla de «Tu plata», en un solo lugar
 *
 * «Tu plata» —la cifra grande del Inicio— son las cuentas que el dueño **puede usar**: ni deuda
 * (tarjetas, préstamos) ni condicionadas a algo puntual ([Account.condicionadaA]: la pensión
 * voluntaria, una AFC, el ahorro de Nu con uso condicionado). La regla vivía en `:shared`
 * (`cuentasLibres`, en `DashboardLogic.kt`), y la tarjeta «Disponible» la necesita del lado del
 * server. Se movió acá para que las dos mitades pregunten a la MISMA función: dos copias del
 * mismo predicado ya se desalinearon dos veces en este proyecto.
 *
 * ## Por qué el Disponible necesita más que los ingresos
 *
 * El dueño vio «te pasaste por $11M» con plata en su cuenta principal. El Disponible era
 * «ingresos del período menos fijos», y los gastos grandes del período se pagaron con plata que no
 * es ingreso: la Gardenera ($9,96M) con un préstamo de su papá de $10M, anotado como traspaso desde
 * la cuenta del crédito; el colegio ($3M) directo desde Nu, que es ahorro condicionado; y lo que
 * tenía el primer día del período no contaba para nada. El gasto sí contaba entero.
 *
 * Él decidió:
 * 1. **Lo que tenías al empezar el período cuenta.**
 * 2. **La plata que entra de un préstamo o de un ahorro cuenta.** El gasto se sigue contando, así
 *    que las dos cosas se compensan.
 *
 * [PlataDelPeriodo] es esa cuenta, sin los fijos (que siguen saliendo del checklist, en el cliente).
 */

/** Tarjetas y préstamos: el saldo es lo que se debe. */
fun esCuentaDeDeuda(tipo: AccountType): Boolean =
    tipo == AccountType.CREDIT_CARD || tipo == AccountType.LOAN

/**
 * ¿Esta cuenta es parte de «Tu plata»? **Ni deuda ni condicionada.** Es la regla del hero del
 * Inicio, de la fila «Cuentas» y del renglón «Tu plata» de Cuentas, y ahora también de la tarjeta
 * «Disponible» y del server que la alimenta.
 */
fun esDeTuPlata(tipo: AccountType, condicionadaA: String?): Boolean =
    !esCuentaDeDeuda(tipo) && condicionadaA.isNullOrBlank()

/** Lo mismo que la otra, para una cuenta ya armada. */
fun Account.esDeTuPlata(): Boolean = esDeTuPlata(type, condicionadaA)

/** Lo que el Disponible necesita saber de una cuenta: su tipo y si está condicionada. */
data class CuentaDelDisponible(val tipo: AccountType, val condicionadaA: String?) {
    val esTuPlata: Boolean get() = esDeTuPlata(tipo, condicionadaA)
    val esDeuda: Boolean get() = esCuentaDeDeuda(tipo)
}

/**
 * La suma de los movimientos de una cuenta de un tipo (ingreso o egreso), como la devuelve un
 * `GROUP BY account_id, type` del server.
 */
data class SumaDeMovimientos(val accountId: String, val tipo: TransactionType, val monto: Long)

/**
 * **El saldo de «Tu plata»** a partir de las sumas de sus movimientos — con [signedDelta], la
 * MISMA regla de signo que derivan los saldos de cada cuenta. Solo las cuentas de [cuentas] que son
 * de Tu plata; una cuenta que no se conoce no se cuenta.
 *
 * El server la llama con los movimientos **anteriores al primer día del período** (en pesos, sin
 * anulados): eso es lo que el dueño tenía al empezar. Los «Por confirmar» sí suman acá, porque el
 * saldo de una cuenta los incluye: la plata se movió, confirmada o no.
 */
fun saldoDeTuPlata(sumas: List<SumaDeMovimientos>, cuentas: Map<String, CuentaDelDisponible>): Long =
    sumas.sumOf { suma ->
        val cuenta = cuentas[suma.accountId]
        if (cuenta == null || !cuenta.esTuPlata) 0L else signedDelta(cuenta.tipo, suma.tipo, suma.monto)
    }

/**
 * Lo que el período le puso y le quitó a «Tu plata», aparte de los gastos (que se miden contra el
 * Disponible) y de los fijos (que salen del checklist).
 *
 * @property saldoAlInicio lo que había en Tu plata a las 00:00 del primer día del período.
 * @property ingresos los ingresos del período que cayeron en una cuenta de Tu plata, con la regla
 *   de «Ingresos» (sin traspasos, sin «Por confirmar», sin anulados). Un rendimiento que cae en un
 *   ahorro condicionado no está: esa plata no se puede usar.
 * @property desdeFuera traspasos **hacia** Tu plata **desde** una cuenta de afuera: el desembolso de
 *   un préstamo, un retiro de un ahorro condicionado o de una inversión fuera de Tu plata.
 * @property pagadoDesdeFuera lo que se pagó **directo** desde una cuenta de ahorro o inversión de
 *   afuera (el colegio desde Nu). El gasto se cuenta igual —como gasto variable o como fijo— y esto
 *   lo financia, así que se compensan. Una compra con tarjeta no está: se va a pagar después desde
 *   Tu plata.
 * @property guardado traspasos **desde** Tu plata **hacia** una cuenta de ahorro o inversión de
 *   afuera: plata que se apartó y ya no se puede gastar. Un pago a una deuda no es esto: ya está en
 *   los fijos (la cuota) o no cuenta (el pago de la tarjeta).
 */
data class PlataDelPeriodo(
    val saldoAlInicio: Long,
    val ingresos: Long,
    val desdeFuera: Long,
    val pagadoDesdeFuera: Long,
    val guardado: Long,
) {
    /** «Entraron»: ingresos + lo que llegó de afuera + lo que afuera pagó por Tu plata. */
    val entradas: Long get() = ingresos + desdeFuera + pagadoDesdeFuera

    /** Lo que hubo para el período antes de los fijos. */
    val total: Long get() = saldoAlInicio + entradas - guardado
}

/**
 * Arma [PlataDelPeriodo] con los movimientos del período.
 *
 * @param eventos los movimientos **vivos** (no anulados) de la ventana del período, con
 *   `countsAsCashFlow` ya derivado — los mismos que usa [gastoVariablePorDia].
 * @param cuentas todas las cuentas del usuario, por id.
 *
 * Solo pesos, igual que el resto de la tarjeta. Lo que espera en «Por confirmar» no cuenta en nada:
 * tampoco cuenta como gasto variable ni como ingreso.
 *
 * Un traspaso entre dos cuentas de Tu plata no cambia nada (sale de una y entra a otra). Una pata
 * de traspaso cuya otra pata no aparece no se cuenta: sin la otra pata no se sabe de dónde vino.
 */
fun plataDelPeriodo(
    saldoAlInicio: Long,
    eventos: List<FinancialEvent>,
    cuentas: Map<String, CuentaDelDisponible>,
): PlataDelPeriodo {
    val vivos = eventos.filter { it.currency == "COP" && !esperaEnPorConfirmar(it.reconciliationStatus) }
    fun cuentaDe(e: FinancialEvent) = cuentas[e.accountId]

    val ingresos = vivos
        .filter { it.type == TransactionType.INCOME && cuentaEnGastosEIngresos(it) && cuentaDe(it)?.esTuPlata == true }
        .sumOf { it.amount }

    // Las patas de cada traspaso, por su `transferId`. Se busca en TODOS los movimientos del
    // período (no solo los de pesos): la otra pata solo hace falta para saber de qué cuenta es.
    val patasPorTraspaso = eventos
        .filter { it.category == TRANSFER_CATEGORY && it.transferId != null }
        .groupBy { it.transferId!! }

    var desdeFuera = 0L
    var guardado = 0L
    vivos.filter { it.category == TRANSFER_CATEGORY && it.transferId != null && cuentaDe(it)?.esTuPlata == true }
        .forEach { pata ->
            val otra = patasPorTraspaso[pata.transferId].orEmpty().firstOrNull { it.id != pata.id } ?: return@forEach
            val otraCuenta = cuentas[otra.accountId] ?: return@forEach
            if (otraCuenta.esTuPlata) return@forEach
            when {
                pata.type == TransactionType.INCOME -> desdeFuera += pata.amount
                // Hacia un ahorro o una inversión de afuera: se apartó. Hacia una deuda: es un
                // pago, y ese no se cuenta acá.
                !otraCuenta.esDeuda -> guardado += pata.amount
            }
        }

    val pagadoDesdeFuera = vivos
        .filter { e ->
            val cuenta = cuentaDe(e)
            e.type == TransactionType.EXPENSE &&
                cuenta != null && !cuenta.esTuPlata && !cuenta.esDeuda &&
                // Lo que se cuenta como gasto (variable o fijo, cuotas incluidas) o lo que paga
                // compras con tarjeta ya contadas como gasto variable. Un traspaso, un ajuste o una
                // apertura no son pagos de nada.
                (cuentaEnGastosEIngresos(e) || e.category == CARD_PAYMENT_CATEGORY)
        }
        .sumOf { it.amount }

    return PlataDelPeriodo(
        saldoAlInicio = saldoAlInicio,
        ingresos = ingresos,
        desdeFuera = desdeFuera,
        pagadoDesdeFuera = pagadoDesdeFuera,
        guardado = guardado,
    )
}
