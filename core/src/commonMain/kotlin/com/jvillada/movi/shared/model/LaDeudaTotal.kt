package com.jvillada.movi.shared.model

/**
 * # La deuda total, sin deudas que resten (vuelta 59)
 *
 * **Lo que pasaba.** «Vehículo 4083» se creó con un capital de $40M, pero sin saldo: en el alta, «Deuda
 * actual» era opcional y, en blanco, valía 0. La primera cuota lo dejó en −$980.000, y como la deuda
 * total sumaba cada cuenta con su signo, Créditos decía «Deuda total $603.000»: los $1.583.000 de Nu
 * **menos** los $980.000 del vehículo. Una deuda que no se conoce le restaba a las que sí.
 *
 * **La regla.** Una deuda con saldo a favor en Movi (préstamo o tarjeta con `balance < 0`) casi siempre
 * quiere decir «Movi no tiene su saldo», no «el banco me debe». No se suma con el signo contrario: suma
 * **cero** en la deuda total, y se nombra aparte ([deudasSinSaldoDe]) para que el dueño la cuadre. Una
 * tarjeta sobrepagada de verdad también suma cero, y eso es cierto: no se debe nada.
 *
 * ## Y [patrimonioDe] (vuelta 59, vuelta 73 y vuelta 77)
 *
 * Las vueltas 59 y 73 dejaron [patrimonioDe] sumando cada deuda **con su signo**: en una tarjeta
 * sobrepagada es la verdad contable (el banco le debe esa plata al dueño), y en un crédito sin saldo
 * cambiar la regla «no lo hacía más cierto». **La vuelta 77 lo cambió solo para los préstamos**
 * ([prestamoSinSaldo]): un `LOAN` en negativo cuenta cero en el patrimonio, mientras no tenga saldo.
 * Contarlo con su signo le subía $980.000 al neto y a la curva, y Patrimonio decía «Deudas $603.000»
 * al lado de «Créditos y tarjetas $1.583.000». Una tarjeta a favor sigue con su signo; un préstamo
 * con saldo suma igual que siempre.
 *
 * Por eso lo que cambia es «Deuda total» (Créditos y el acceso del Inicio, con [deudaTotalDe]) y la
 * presentación: la deuda sin saldo se dice como tal, nunca como una cifra con el signo al revés.
 */

/** Lo que una cuenta de deuda aporta a la deuda total: lo que se debe en pesos, nunca menos de cero. */
fun deudaQueSumaDe(cuenta: Account): Long = valorEnPesosDe(cuenta).coerceAtLeast(0L)

/** ¿[cuenta] es una deuda (préstamo o tarjeta) que cuenta en la deuda total? Un bien nunca. */
private fun esDeuda(cuenta: Account): Boolean = esCuentaDeDeuda(cuenta.type) && !cuenta.esBien

/**
 * **La deuda total en pesos**: préstamos y tarjetas de [cuentas], cada una con [deudaQueSumaDe]. Las
 * demás cuentas se ignoran, así que se le puede pasar la lista entera de cuentas.
 */
fun deudaTotalDe(cuentas: List<Account>): Long = cuentas.filter(::esDeuda).sumOf(::deudaQueSumaDe)

/**
 * **Las deudas sin saldo en Movi**: préstamos y tarjetas cuyo saldo en pesos es negativo. No suman en
 * [deudaTotalDe]; la pantalla las nombra aparte («1 crédito sin saldo · Cuadrar ›»).
 */
fun deudasSinSaldoDe(cuentas: List<Account>): List<Account> =
    cuentas.filter { esDeuda(it) && valorEnPesosDe(it) < 0L }

/**
 * **Vuelta 73 · ¿a este crédito le falta su saldo en Movi?** Un préstamo (`LOAN`) con condiciones
 * (`CreditTerms.principal > 0`) cuya deuda en su moneda es negativa, o es cero sin un solo movimiento
 * que la haya llevado ahí ([CreditSummary.hasMovements]: en cero CON movimientos está pagado, y eso es
 * cierto).
 *
 * Cómo se llega ahí: la deuda de un préstamo sale de sus movimientos (positiva = lo que se debe). Las
 * condiciones (`PUT /api/credits/{id}`) no escriben ningún movimiento, así que un crédito cargado con
 * sus condiciones y sin el desembolso ni un ajuste queda en $0; la primera cuota le resta su capital y
 * queda en negativo —la demo: Vehículo 4083, capital $40M, deuda −$980.000—. Desde la vuelta 77 ese
 * negativo cuenta cero en [patrimonioDe] ([prestamoSinSaldo]); la vuelta 73 hizo que se diga: «Falta el
 * saldo de este crédito · Cargarlo», con el ajuste de saldo de siempre. Pura.
 */
fun creditoSinSaldo(credito: CreditSummary): Boolean {
    val cuenta = credito.account
    if (cuenta.type != AccountType.LOAN || (credito.terms?.principal ?: 0L) <= 0L) return false
    val deuda = cuenta.balancesByCurrency[cuenta.currency] ?: cuenta.balance
    return deuda < 0L || (deuda == 0L && !credito.hasMovements)
}
