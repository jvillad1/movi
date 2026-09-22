package com.jvillada.movi.ui.credits

import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.cuotaMasCercana
import com.jvillada.movi.shared.time.AppTimeZone
import com.jvillada.movi.ui.components.formatMoney
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.toLocalDateTime

/**
 * # Cuando el pago no fue la cuota, decírselo
 *
 * El dueño pagó **$4.178.163** el 19 de septiembre contra una cuota registrada de **$4.101.123**:
 * *«tenía ese valor en mi cabeza y por eso lo puse; imagino pagué de más»*. Pagó $77.040 de más y
 * la app no se lo dijo en ningún lado — la deuda simplemente bajó un poco más.
 *
 * Esto es la línea que faltaba: **una sola frase, después del hecho**, sobre la fila del pago en el
 * detalle del crédito.
 *
 * ## De dónde salen los números, y por qué no hace falta recalcular nada
 *
 * La pata de la deuda de un pago de cuota guarda dos cosas: su **monto**, que es el capital que de
 * verdad bajó la deuda, y [com.jvillada.movi.shared.model.FinancialEvent.noAmortiza], que es el
 * interés más el seguro más los otros cargos de ese mes — un **hecho ya ocurrido**, guardado
 * justamente para no tener que deducirlo de una resta que miente cuando el capital se clampa a
 * cero. Con esas dos y la cuota pactada alcanza:
 *
 * ```
 * pagado      = capital + noAmortiza
 * diferencia  = pagado − cuota pactada
 * ```
 *
 * Y como el interés y los cargos del mes son los mismos pague lo que pague, **la diferencia en el
 * pago es exactamente la diferencia en el capital**: los $77.040 de más se fueron enteros a bajar
 * la deuda, y $77.040 de menos habrían sido $77.040 menos de capital. La frase dice eso y no una
 * proyección: no hay nada que estimar acá.
 *
 * ## Una cuota pagada en partes se juzga entera
 *
 * El dueño paga algunas cuotas por partes (ver el KDoc de `desglosarCuota`): la libranza de
 * $6.040.259 como $3.000.000 + $3.040.259. Mirada fila por fila, la primera parte «se fue entera
 * en los intereses» y la segunda «fue $3.000.000 menos que la cuota» — las dos frases falsas,
 * porque juntas fueron exactamente la cuota. Por eso los pagos se juntan **por cuota**, con la
 * misma regla con la que el server decide qué interés ya cubrieron ([cuotaMasCercana]), y la frase
 * sale una sola vez, en el último pago de la cuota, sobre el total.
 *
 * El total no es la suma de `capital + noAmortiza` de cada fila: la primera guarda el cargo ENTERO
 * del mes aunque no lo haya alcanzado a cubrir, y las siguientes guardan lo que faltaba. Si el
 * último pago bajó capital, los cargos quedaron cubiertos y el total es la suma de los capitales
 * más el cargo del mes (el `noAmortiza` más alto del grupo, el de la primera parte).
 *
 * ## Solo la cuota más reciente
 *
 * Las condiciones del crédito guardan la cuota de HOY y no desde cuándo vale. Comparar los pagos
 * viejos contra ella marcaba como «de más» o «de menos» cuotas que se pagaron justas antes de un
 * cambio. Sin esa fecha, lo único honesto es juzgar el último período pagado y callarse en los
 * anteriores.
 *
 * ## Por qué vive en el detalle del crédito y no en la hoja del movimiento
 *
 * Porque la fila que tiene los datos es la de la cuenta del crédito. En Movimientos el dueño ve la
 * **pata del dinero** —el gasto que salió de su cuenta de ahorros—, y esa pata no lleva
 * `noAmortiza` (`pagoDeCuotaLegs` lo escribe solo en la de la deuda, porque es ahí donde significa
 * algo): explicarlo desde ahí obligaría a traerse la pata hermana además de las condiciones del
 * crédito. En el detalle del crédito la fila ya está en pantalla con su `noAmortiza`, y lo único
 * que hay que leer de más es la cuota pactada. Un endpoint contra dos.
 *
 * Y es donde el dueño mira cuando quiere saber cómo va la deuda, que es la pregunta que esta línea
 * contesta.
 */

/**
 * Qué le pasó a la deuda con los pagos de la cuota más reciente, **por id de la fila** en la que
 * se dice (el último pago de esa cuota). Las demás filas no dicen nada.
 *
 * Vacío cuando:
 * - **ninguna fila trae [FinancialEvent.noAmortiza]**: el par es simétrico —una tarjeta, un crédito
 *   sin tasa— y la deuda bajó por todo lo pagado. No hay reparto que explicar.
 * - **no hay cuota pactada** ([cuotaPactada] en cero o menos, o las condiciones que no se pudieron
 *   leer): comparar contra cero diría que pagó todo de más.
 * - **se pagó exactamente la cuota de una vez**: es el caso normal y decirlo sería ruido.
 *
 * @param movimientos los movimientos de la cuenta del crédito, tal como los muestra el detalle.
 * @param cuotaPactada `credit_terms.installment`.
 * @param diaDePago `credit_terms.day_of_month`: con él se decide a qué cuota va cada pago.
 * @param hoy la fecha de hoy en la zona de la app. Mientras el período de la cuota sigue abierto,
 *   un pago corto no es «de menos» todavía: puede faltar la otra parte.
 */
fun textosDeLosPagosQueNoFueronLaCuota(
    movimientos: List<FinancialEvent>,
    cuotaPactada: Long,
    diaDePago: Int,
    hoy: LocalDate,
): Map<String, String> {
    if (cuotaPactada <= 0L || diaDePago !in 1..31) return emptyMap()
    fun cuotaDe(fecha: LocalDate) = cuotaMasCercana(fecha.year, fecha.monthNumber, fecha.dayOfMonth, diaDePago)
    fun cuotaDe(timestamp: Long) =
        cuotaDe(Instant.fromEpochMilliseconds(timestamp).toLocalDateTime(AppTimeZone.zone).date)

    val porCuota = movimientos.filter { it.noAmortiza != null }.groupBy { cuotaDe(it.timestamp) }
    val ultima = porCuota.keys.maxOrNull() ?: return emptyMap()
    val pagos = porCuota.getValue(ultima).sortedWith(compareBy({ it.timestamp }, { it.id }))
    // Abierta mientras un pago hecho hoy todavía iría a esta cuota (o la cuota es adelantada).
    val abierta = cuotaDe(hoy) <= ultima
    val texto = textoDeLaCuota(
        capitales = pagos.map { it.amount },
        noAmortiza = pagos.map { it.noAmortiza ?: 0L },
        cuotaPactada = cuotaPactada,
        abierta = abierta,
        moneda = pagos.last().currency,
    ) ?: return emptyMap()
    return mapOf(pagos.last().id to texto)
}

/**
 * La frase de una cuota a partir de sus pagos, en orden. Ver [textosDeLosPagosQueNoFueronLaCuota].
 *
 * @param capitales el monto de la pata de la deuda de cada pago: lo que de verdad bajó el saldo.
 * @param noAmortiza lo que cada pago guardó de interés + seguro + otros cargos por cubrir.
 * @param abierta si el período de la cuota todavía no terminó.
 */
internal fun textoDeLaCuota(
    capitales: List<Long>,
    noAmortiza: List<Long>,
    cuotaPactada: Long,
    abierta: Boolean,
    moneda: String = "COP",
): String? {
    if (capitales.isEmpty() || cuotaPactada <= 0L) return null
    fun plata(valor: Long) = formatMoney(valor, moneda)
    val partes = capitales.size

    // **El último pago con capital en cero se cuenta aparte.** Ahí el pago se clampó (ver
    // `desglosarCuota`): de esta fila no se puede deducir cuánto se pagó, y los cargos de la cuota
    // todavía no quedaron cubiertos. Se dice con lo que faltaba cubrir, que es un hecho guardado.
    if (capitales.last() <= 0L) {
        val faltaba = noAmortiza.last()
        return if (abierta) {
            "Este pago se fue en los " + plata(faltaba) + " de intereses y cargos de esta cuota, " +
                "que sigue abierta: lo que pagues para completarla sí baja la deuda."
        } else {
            "Este pago se fue entero en los " + plata(faltaba) +
                " de intereses y cargos de ese mes: no bajó nada de la deuda."
        }
    }

    // El último pago bajó capital, así que los cargos del mes quedaron cubiertos. El cargo del mes
    // es el que guardó la primera parte (entero); las siguientes guardan solo lo que faltaba.
    val pagado = capitales.sum() + noAmortiza.max()
    val diferencia = pagado - cuotaPactada
    val entre = if (partes > 1) "Entre los $partes pagos de esta cuota pagaste " else "Pagaste "
    return when {
        diferencia == 0L ->
            if (partes > 1) "Con este pago completaste la cuota de " + plata(cuotaPactada) + " en $partes partes." else null
        // El extra no se reparte: el interés y los cargos del mes ya estaban cubiertos por la
        // cuota, así que baja la deuda completo.
        diferencia > 0L ->
            entre + plata(diferencia) + " más que la cuota de " + plata(cuotaPactada) +
                ": ese extra bajó la deuda completo."
        // Todavía puede llegar la otra parte: decir que la deuda bajó menos sería adelantarse.
        abierta ->
            "Llevas " + plata(pagado) + " de la cuota de " + plata(cuotaPactada) +
                ": te faltan " + plata(-diferencia) + " para completarla."
        else ->
            entre + plata(-diferencia) + " menos que la cuota de " + plata(cuotaPactada) +
                ": la deuda bajó " + plata(-diferencia) + " menos de lo que habría bajado."
    }
}
