package com.jvillada.movi.ui.credits

import com.jvillada.movi.shared.model.CARD_RULE_PREFIX
import com.jvillada.movi.shared.model.ExtractoDeLaTarjeta
import com.jvillada.movi.ui.components.formatCOP
import com.jvillada.movi.ui.components.formatMoney
import com.jvillada.movi.ui.dashboard.PagoDelPeriodo
import com.jvillada.movi.ui.recurrentes.fechaCortaDeFactura

/*
 * # El extracto de la tarjeta, dicho igual en los tres lugares (vuelta 62)
 *
 * La ficha de la tarjeta, la fila «Pago tarjeta Nu» de Pagos y las pastillas de «Anotar pago» dicen
 * las mismas cifras del mismo `ExtractoDeLaTarjeta` (`:core`, una suma por fechas que hace el server).
 * Las frases viven acá para que no se separen.
 */

/** La cifra en la moneda de la tarjeta: «$1.292.700» o «US$420». */
internal fun montoDelExtracto(monto: Long, moneda: String): String =
    if (moneda == "COP") formatCOP(monto) else formatMoney(monto, moneda)

/** «Del extracto del 20 oct». */
fun tituloDelExtracto(e: ExtractoDeLaTarjeta): String = "Del extracto del ${fechaCortaDeFactura(e.corte) ?: e.corte}"

/** ¿Ya se pagó todo lo que pedía el extracto? */
fun extractoPagado(e: ExtractoDeLaTarjeta): Boolean = e.facturado <= 0L

/** «Extracto del 20 oct · pagado ✓»: el título de la ficha cuando no queda nada del extracto. */
fun tituloDelExtractoPagado(e: ExtractoDeLaTarjeta): String = "Extracto del ${fechaCortaDeFactura(e.corte) ?: e.corte} · pagado ✓"

/**
 * **Lo que falta del mínimo**, descontando lo ya pagado desde el corte (revisión de la vuelta 62: con
 * $100.000 pagados de un mínimo de $180.000, faltan $80.000). `null` sin mínimo cargado.
 */
fun minimoQueFalta(e: ExtractoDeLaTarjeta): Long? = e.minimo?.let { (it - e.pagadoDesdeElCorte).coerceAtLeast(0L) }

/** «Mínimo $180.000», o con algo pagado «Mínimo: te faltan $80.000» (o «Mínimo cubierto»). */
private fun textoDelMinimo(e: ExtractoDeLaTarjeta): String? {
    val falta = minimoQueFalta(e) ?: return null
    return when {
        e.pagadoDesdeElCorte <= 0L -> "Mínimo ${montoDelExtracto(falta, e.moneda)}"
        falta > 0L -> "Mínimo: te faltan ${montoDelExtracto(falta, e.moneda)}"
        else -> "Mínimo cubierto"
    }
}

/** «Mínimo $180.000 · pagar antes del 5 nov» (sin mínimo cargado, solo la fecha). */
fun detalleDelExtracto(e: ExtractoDeLaTarjeta): String = listOfNotNull(
    textoDelMinimo(e),
    "pagar antes del ${fechaCortaDeFactura(e.fechaDePago) ?: e.fechaDePago}",
).joinToString(" · ").replaceFirstChar { it.uppercase() }

/** «$1.392.700 · ya pagaste $100.000»: lo que pedía el extracto y lo pagado desde el corte. `null` sin pagos. */
fun loYaPagadoDelExtracto(e: ExtractoDeLaTarjeta): String? =
    e.pagadoDesdeElCorte.takeIf { it > 0L }?.let {
        "${montoDelExtracto(e.facturado + it, e.moneda)} · ya pagaste ${montoDelExtracto(it, e.moneda)}"
    }

/** «Del extracto del 20 oct: $1.292.700 · mínimo $180.000 · pagar antes del 5 nov», en un renglón. */
fun lineaDelExtracto(e: ExtractoDeLaTarjeta): String =
    if (extractoPagado(e)) tituloDelExtractoPagado(e)
    else "${tituloDelExtracto(e)}: ${montoDelExtracto(e.facturado, e.moneda)} · " +
        detalleDelExtracto(e).replaceFirstChar { it.lowercase() }

/** La línea de la tarjeta en la lista de Créditos: «Extracto $1.292.700 · pagar antes del 5 nov». */
fun lineaCortaDelExtracto(e: ExtractoDeLaTarjeta): String =
    if (extractoPagado(e)) tituloDelExtractoPagado(e)
    else "Extracto ${montoDelExtracto(e.facturado, e.moneda)} · pagar antes del ${fechaCortaDeFactura(e.fechaDePago) ?: e.fechaDePago}"

/** «Después del corte: $290.300 (va al próximo extracto)», o `null` si no hay nada después del corte. */
fun lineaDespuesDelCorte(e: ExtractoDeLaTarjeta): String? =
    e.despuesDelCorte.takeIf { it > 0L }?.let { "Después del corte: ${montoDelExtracto(it, e.moneda)} (va al próximo extracto)" }

/** Lo que se dice cuando Movi no conoce las cuotas de lo facturado: no inventa ninguna. */
const val PUEDE_INCLUIR_DIFERIDOS: String = "Puede incluir compras diferidas: la cifra exacta la dice el extracto del banco."

/** El rótulo al lado de la cifra de la fila de Pagos cuando la cifra es la del extracto. */
const val ROTULO_DEL_EXTRACTO: String = "del extracto"

/** «o el mínimo, $180.000» (o «o lo que falta del mínimo, $80.000»): la alternativa de la fila de Pagos. */
fun alternativaDelMinimo(e: ExtractoDeLaTarjeta): String? {
    val falta = minimoQueFalta(e)?.takeIf { it > 0L } ?: return null
    val que = if (e.pagadoDesdeElCorte > 0L) "lo que falta del mínimo" else "el mínimo"
    return "o $que, ${montoDelExtracto(falta, e.moneda)}"
}

/**
 * **La fila pagada de una tarjeta cuyo extracto sigue abierto** (revisión de la vuelta 62): un pago
 * menor que el extracto sella la fila del vencimiento —la regla del sello no cambia—, pero el extracto
 * no está pagado. «Abonaste $100.000 · faltan $1.292.700 del extracto (mínimo: faltan $80.000)».
 */
fun loQueFaltaDelExtracto(e: ExtractoDeLaTarjeta): String {
    val minimo = minimoQueFalta(e)?.let { if (it > 0L) " (mínimo: faltan ${montoDelExtracto(it, e.moneda)})" else " (mínimo cubierto)" }.orEmpty()
    return "Abonaste ${montoDelExtracto(e.pagadoDesdeElCorte, e.moneda)} · faltan ${montoDelExtracto(e.facturado, e.moneda)} del extracto$minimo"
}

/** La pastilla «Extracto $1.292.700» de «Anotar pago» (corta: a 390 dp caben las tres en un renglón). */
fun pastillaDelTotalDelExtracto(e: ExtractoDeLaTarjeta): String = "Extracto ${montoDelExtracto(e.facturado, e.moneda)}"

/** La pastilla «Mínimo $80.000» de «Anotar pago»: lo que falta del mínimo. */
fun pastillaDelMinimo(minimo: Long, moneda: String): String = "Mínimo ${montoDelExtracto(minimo, moneda)}"

/** La pastilla que deja escribir otra cifra. */
const val PASTILLA_OTRO_MONTO: String = "Otro"

/**
 * «Pago de Nu · extracto del 20 oct»: lo que dice el encabezado de Cuota con el extracto a mano, en vez
 * del mes del período (un extracto se nombra por su corte, no por «octubre»).
 */
fun mesDelPagoDeLaTarjeta(e: ExtractoDeLaTarjeta): String = "extracto del ${fechaCortaDeFactura(e.corte) ?: e.corte}"

/**
 * **El extracto que le corresponde a esta fila de Pagos**, o `null`. Solo la fila de una tarjeta
 * (`card_<cuenta>`), y solo si el pago de ese extracto es el vencimiento de la fila: antes del corte
 * de este mes, la fila habla del pago que viene y el último extracto ya venció. Una fila pagada lo
 * trae solo si el extracto sigue abierto (un abono menor que el extracto sella la fila).
 */
fun extractoDeLaFila(pago: PagoDelPeriodo, extractos: Map<String, ExtractoDeLaTarjeta>): ExtractoDeLaTarjeta? {
    if (!pago.ruleId.startsWith(CARD_RULE_PREFIX)) return null
    val e = extractos[pago.ruleId.removePrefix(CARD_RULE_PREFIX)] ?: return null
    val delVencimiento = e.fechaDePago == pago.vence.take(10) && e.moneda == pago.moneda
    // Con el extracto ya pagado no hay nada que decir de él: la fila queda como siempre. Pagada, lo trae
    // porque sigue abierto, y eso es lo que tiene que decir (ver [loQueFaltaDelExtracto]).
    return e.takeIf { delVencimiento && !extractoPagado(it) }
}

/** Una pastilla de «Anotar pago» de una tarjeta: su texto y la cifra que pone (`null` = «Otro monto»). */
data class PastillaDelPago(val texto: String, val monto: Long?)

/**
 * **Las pastillas de «Anotar pago» de una tarjeta** (vuelta 62): «Extracto $X», «Mínimo $Y» y «Otro». Sin extracto ni mínimo, ninguna (la cifra se escribe como siempre). Pura.
 */
fun pastillasDelPagoDeLaTarjeta(tarjeta: com.jvillada.movi.shared.model.CardSummary?): List<PastillaDelPago> {
    if (tarjeta == null) return emptyList()
    val moneda = tarjeta.account.currency
    val extracto = tarjeta.extracto
    // Lo que falta del mínimo, con lo ya pagado desde el corte descontado.
    val minimo = (if (extracto != null) minimoQueFalta(extracto) else tarjeta.terms?.pagoMinimo)?.takeIf { it > 0L }
    val conCifra = buildList {
        extracto?.takeIf { it.facturado > 0L }?.let { add(PastillaDelPago(pastillaDelTotalDelExtracto(it), it.facturado)) }
        minimo?.let { add(PastillaDelPago(pastillaDelMinimo(it, moneda), it)) }
    }
    return if (conCifra.isEmpty()) emptyList() else conCifra + PastillaDelPago(PASTILLA_OTRO_MONTO, null)
}
