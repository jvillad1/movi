package com.jvillada.movi.ui.credits

import com.jvillada.movi.ui.components.formatMoney

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
 * Qué le pasó a la deuda con este pago, o `null` si no hay nada que contar.
 *
 * `null` en tres casos:
 * - **[noAmortiza] nulo**: el par es simétrico —una tarjeta, un crédito sin tasa— y la deuda bajó
 *   por todo lo pagado. No hay diferencia que explicar porque no hubo reparto.
 * - **sin cuota pactada** ([cuotaPactada] en cero o menos, o las condiciones que no se pudieron
 *   leer): no hay contra qué comparar, y comparar contra cero diría que pagó todo de más.
 * - **pagó exactamente la cuota**: es el caso normal y decirlo sería ruido en cada fila.
 *
 * @param capitalAbonado el monto de la pata de la deuda: lo que de verdad bajó el saldo.
 * @param noAmortiza interés + seguro + otros cargos de ese mes, tal como quedaron guardados.
 * @param cuotaPactada `credit_terms.installment`.
 */
fun textoDelPagoQueNoFueLaCuota(
    capitalAbonado: Long,
    noAmortiza: Long?,
    cuotaPactada: Long,
    moneda: String = "COP",
): String? {
    val noAmortizado = noAmortiza ?: return null
    if (cuotaPactada <= 0L) return null
    fun plata(valor: Long) = formatMoney(valor, moneda)

    // **El capital en cero se cuenta aparte, y no como «pagaste de menos».** Ahí el pago se
    // clampó (ver `desglosarCuota`), así que de esta fila no se puede deducir cuánto se pagó — y
    // lo que importa no es la diferencia sino que la deuda no se movió. Decirlo con la cifra de
    // intereses del mes, que sí es un hecho guardado, es lo único honesto que se puede afirmar.
    if (capitalAbonado <= 0L) {
        return "Este pago se fue entero en los " + plata(noAmortizado) +
            " de intereses y cargos de ese mes: no bajó nada de la deuda."
    }

    val pagado = capitalAbonado + noAmortizado
    val diferencia = pagado - cuotaPactada
    return when {
        diferencia == 0L -> null
        // Lo que motivó todo esto. El extra no se reparte: el interés y los cargos del mes ya
        // estaban cubiertos por la cuota, así que baja la deuda completo.
        diferencia > 0L ->
            "Pagaste " + plata(diferencia) + " más que la cuota de " + plata(cuotaPactada) +
                ": ese extra bajó la deuda completo."
        else ->
            "Pagaste " + plata(-diferencia) + " menos que la cuota de " + plata(cuotaPactada) +
                ": la deuda bajó " + plata(-diferencia) + " menos de lo que habría bajado."
    }
}
