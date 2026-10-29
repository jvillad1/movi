package com.jvillada.movi.shared.model

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus

/**
 * # La última cuota, bien contada (vuelta 59)
 *
 * El crédito decía «próxima 5 de noviembre · te faltan 49 cuotas · la última en octubre de 2030», y
 * era noviembre: la pantalla contaba el período en curso como la cuota 1 (`periodoDe(ahora).mas(n − 1)`)
 * aunque la cuota de octubre ya estuviera pagada. Nu, igual: «10 cuotas, hasta julio de 2027», y era
 * agosto.
 *
 * **Las N cuotas que faltan empiezan en la primera que no se ha pagado**, porque el saldo con el que se
 * proyectan ya descontó las pagadas. Si la de octubre está pagada, la primera es la de noviembre; si
 * no, la de octubre (por vencer o vencida: el saldo todavía la incluye). La última es esa más N − 1.
 *
 * Se cuenta en **meses de vencimiento** (el mes del día de pago), no en períodos del dueño: una cuota
 * vence un día del calendario, y es lo mismo que sella el pago de una cuota (`cuotaMasCercana`).
 */

/** Las categorías con que se anota el pago de una cuota: la tuya, la de la nómina y la de un tercero. */
val CATEGORIAS_DEL_PAGO_DE_UNA_CUOTA: Set<String> =
    setOf(CUOTA_CATEGORY, PAYROLL_DEDUCTION_CATEGORY, THIRD_PARTY_PAYMENT_CATEGORY)

/**
 * ¿[evento] es el pago de una cuota en la cuenta del crédito? La pata que baja la deuda (`INCOME` en
 * una cuenta de deuda) con una de [CATEGORIAS_DEL_PAGO_DE_UNA_CUOTA]. Un ajuste de saldo o un abono
 * extraordinario no pagan una cuota.
 */
fun esPagoDeUnaCuota(evento: FinancialEvent): Boolean =
    evento.type == TransactionType.INCOME && evento.category in CATEGORIAS_DEL_PAGO_DE_UNA_CUOTA

/** El vencimiento del día [dia] en el mes ([anio], [mes]), recortado al largo del mes (el 31 en noviembre es el 30). */
fun vencimientoDelMes(anio: Int, mes: Int, dia: Int): LocalDate {
    val primero = LocalDate(anio, mes, 1)
    val ultimo = primero.plus(1, DateTimeUnit.MONTH).minus(1, DateTimeUnit.DAY).dayOfMonth
    return LocalDate(anio, mes, dia.coerceIn(1, ultimo))
}

/** **El próximo vencimiento del día [dia] desde [hoy]**, hoy incluido. */
fun proximoVencimientoDelDia(dia: Int, hoy: LocalDate): LocalDate {
    val esteMes = vencimientoDelMes(hoy.year, hoy.monthNumber, dia)
    if (esteMes >= hoy) return esteMes
    val siguiente = LocalDate(hoy.year, hoy.monthNumber, 1).plus(1, DateTimeUnit.MONTH)
    return vencimientoDelMes(siguiente.year, siguiente.monthNumber, dia)
}

/** El mes de [fecha], como período de nombre. */
private fun mesDe(fecha: LocalDate): PeriodoFinanciero = PeriodoFinanciero(fecha.year, fecha.monthNumber)

/** El más tardío de dos meses. */
private fun elMasTarde(a: PeriodoFinanciero, b: PeriodoFinanciero): PeriodoFinanciero = if (b.prefijo > a.prefijo) b else a

/**
 * **El mes de la primera cuota sin pagar.**
 *
 * @param pagadas las cuotas ya pagadas (`"2026-10"`, ver [CreditSummary.cuotasPagadas]). Con ellas se
 *   arranca en el mes de [hoy] y se salta cada mes pagado: una cuota pagada por adelantado corre la
 *   primera un mes más. `null` = no se sabe (un server viejo, una tarjeta): se toma el mes del
 *   [proximoVencimientoDelDia], o sea, se supone pagada la cuota cuyo día ya pasó.
 * @param desde el desembolso, si se sabe: la primera cuota de un crédito vence **después** de él, nunca
 *   antes ni el mismo día.
 */
fun mesDeLaPrimeraCuotaSinPagar(
    dia: Int,
    hoy: LocalDate,
    pagadas: Collection<String>?,
    desde: LocalDate? = null,
): PeriodoFinanciero {
    val laPrimeraDelCredito = desde?.let { d ->
        val enSuMes = vencimientoDelMes(d.year, d.monthNumber, dia)
        if (enSuMes > d) mesDe(enSuMes) else periodoSiguiente(mesDe(enSuMes))
    }
    var mes = if (pagadas == null) mesDe(proximoVencimientoDelDia(dia, hoy)) else mesDe(hoy)
    if (laPrimeraDelCredito != null) mes = elMasTarde(mes, laPrimeraDelCredito)
    if (pagadas != null) {
        // Un tope por si alguien sella años por adelantado: nadie paga más de dos años de cuotas antes.
        var vueltas = 0
        while (mes.prefijo in pagadas && vueltas < MAX_CUOTAS_ADELANTADAS) {
            mes = periodoSiguiente(mes)
            vueltas++
        }
    }
    return mes
}

private const val MAX_CUOTAS_ADELANTADAS = 24

/** [mesDeLaPrimeraCuotaSinPagar] de un crédito, o `null` sin términos. */
fun mesDeLaPrimeraCuota(credit: CreditSummary, hoy: LocalDate): PeriodoFinanciero? {
    val terms = credit.terms ?: return null
    val desde = runCatching { LocalDate.parse(terms.startDate.trim()) }.getOrNull()
    return mesDeLaPrimeraCuotaSinPagar(terms.dayOfMonth, hoy, credit.cuotasPagadas, desde)
}

/** **El mes de la última cuota**: la primera sin pagar más las [meses] − 1 que siguen. */
fun mesDeLaUltimaCuota(primera: PeriodoFinanciero, meses: Int): PeriodoFinanciero = primera.mas((meses - 1).coerceAtLeast(0))

/**
 * **Cuándo cae la última cuota de todas**, cada crédito contado desde su primera cuota sin pagar.
 * `null` si alguno no se termina (una deuda que no se acaba le gana a cualquier fecha, ver
 * [ResumenDeDeudas.mesesHastaLaUltimaCuota]) o si ninguno tiene fecha.
 */
fun ultimaCuotaDeTodas(planes: List<Pair<PlanDelCredito, PeriodoFinanciero>>): PeriodoFinanciero? {
    if (planes.any { (plan, _) -> plan.noSeTermina }) return null
    return planes
        .mapNotNull { (plan, primera) -> plan.mesesHastaLaUltimaCuota?.let { mesDeLaUltimaCuota(primera, it) } }
        .maxByOrNull { it.prefijo }
}

/** Cuántos meses van de [desde] a [hasta] (negativo si [hasta] es anterior). */
fun mesesEntre(desde: PeriodoFinanciero, hasta: PeriodoFinanciero): Int =
    (hasta.year * 12 + hasta.month) - (desde.year * 12 + desde.month)
