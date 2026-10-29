package com.jvillada.movi.shared.model

import kotlinx.datetime.LocalDate

/**
 * # El saldo que da el plan, para el alta de un crédito que ya venía (vuelta 59)
 *
 * En «No, ya lo tenía desde antes» el campo «Deuda actual» era opcional y, en blanco, valía 0. Así
 * nació «Vehículo 4083» —$40M de capital desde enero, sin saldo— y la primera cuota lo dejó en
 * −$980.000, restándole a la deuda total. La hoja ahora **propone** el saldo que da el plan («Por tu
 * plan deberías unos $34,5M · Usar este») y, si falta un dato para calcularlo, lo pide en vez de
 * suponer cero.
 *
 * **No es una amortización nueva**: es [desglosarCuota] —la misma cuenta que separa cada cuota que se
 * registra en interés, seguro, otros cargos y capital— aplicada una vez por cada cuota que ya venció
 * desde el desembolso. Si el dueño hubiera anotado cada cuota en Movi, su deuda sería esta.
 */

/** Lo que el plan necesita y todavía no está escrito. */
enum class FaltaParaElSaldoDelPlan(val texto: String) {
    CAPITAL("el capital original"),
    TASA("la tasa"),
    PLAZO("el plazo"),
    CUOTA("la cuota mensual"),
    DIA("el día de pago"),
    FECHA("la fecha del desembolso"),
}

/**
 * El saldo según el plan, o lo que falta para calcularlo. Exactamente uno de los dos.
 *
 * @property saldo lo que debería deber hoy; `0` si por el plan ya terminó de pagar.
 * @property cuotasVencidas cuántas cuotas se descontaron (las que vencieron después del desembolso y
 *   hasta hoy, sin pasar del plazo).
 */
data class SaldoSegunElPlan(
    val saldo: Long? = null,
    val cuotasVencidas: Int = 0,
    val falta: FaltaParaElSaldoDelPlan? = null,
)

/**
 * **Cuánto debería deber hoy** un crédito con este capital, tasa, plazo, cuota y día de pago,
 * desembolsado el [desde], si pagó cada cuota que venció hasta [hoy] (incluido). Ver el KDoc del
 * archivo.
 *
 * El primer dato que falta, en el orden de la hoja, sale en [SaldoSegunElPlan.falta]: con un dato de
 * menos no hay saldo honesto que proponer.
 */
fun saldoSegunElPlan(
    capital: Long?,
    tasaEa: Double?,
    sinIntereses: Boolean,
    plazoMeses: Int?,
    cuota: Long?,
    seguroMensual: Long?,
    otrosCargosMensuales: Long?,
    diaDePago: Int?,
    desde: LocalDate?,
    hoy: LocalDate,
): SaldoSegunElPlan {
    val falta = when {
        capital == null || capital <= 0L -> FaltaParaElSaldoDelPlan.CAPITAL
        !sinIntereses && (tasaEa == null || tasaEa <= 0.0 || !tasaEa.isFinite()) -> FaltaParaElSaldoDelPlan.TASA
        plazoMeses == null || plazoMeses <= 0 -> FaltaParaElSaldoDelPlan.PLAZO
        cuota == null || cuota <= 0L -> FaltaParaElSaldoDelPlan.CUOTA
        diaDePago == null || diaDePago !in 1..31 -> FaltaParaElSaldoDelPlan.DIA
        desde == null -> FaltaParaElSaldoDelPlan.FECHA
        else -> null
    }
    if (falta != null) return SaldoSegunElPlan(falta = falta)

    // La primera cuota vence después del desembolso (ver [mesDeLaPrimeraCuotaSinPagar]); se cuentan las
    // que vencieron hasta hoy, sin pasar del plazo pactado.
    var mes = mesDeLaPrimeraCuotaSinPagar(diaDePago!!, desde!!, pagadas = emptyList(), desde = desde)
    var saldo = capital!!
    var vencidas = 0
    while (vencidas < plazoMeses!! && saldo > 0L && vencimientoDelMes(mes.year, mes.month, diaDePago) <= hoy) {
        val desglose = desglosarCuota(
            cuota = cuota!!,
            tipoDeLaDeuda = AccountType.LOAN,
            saldoDeLaDeuda = saldo,
            rateEa = tasaEa,
            seguroMensual = seguroMensual,
            otrosCargosMensuales = otrosCargosMensuales,
            yaCobradoEnElMes = 0L,
            sinIntereses = sinIntereses,
        )
        saldo = (saldo - desglose.capital).coerceAtLeast(0L)
        vencidas++
        mes = periodoSiguiente(mes)
    }
    return SaldoSegunElPlan(saldo = saldo, cuotasVencidas = vencidas)
}
