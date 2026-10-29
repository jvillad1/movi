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

/** «Mínimo $180.000 · pagar antes del 5 nov» (sin mínimo cargado, solo la fecha). */
fun detalleDelExtracto(e: ExtractoDeLaTarjeta): String = listOfNotNull(
    e.minimo?.let { "Mínimo ${montoDelExtracto(it, e.moneda)}" },
    "pagar antes del ${fechaCortaDeFactura(e.fechaDePago) ?: e.fechaDePago}",
).joinToString(" · ").replaceFirstChar { it.uppercase() }

/** «Del extracto del 20 oct: $1.292.700 · mínimo $180.000 · pagar antes del 5 nov», en un renglón. */
fun lineaDelExtracto(e: ExtractoDeLaTarjeta): String =
    "${tituloDelExtracto(e)}: ${montoDelExtracto(e.facturado, e.moneda)} · " +
        detalleDelExtracto(e).replaceFirstChar { it.lowercase() }

/** «Después del corte: $290.300 (va al próximo extracto)», o `null` si no hay nada después del corte. */
fun lineaDespuesDelCorte(e: ExtractoDeLaTarjeta): String? =
    e.despuesDelCorte.takeIf { it > 0L }?.let { "Después del corte: ${montoDelExtracto(it, e.moneda)} (va al próximo extracto)" }

/** Lo que se dice cuando Movi no conoce las cuotas de lo facturado: no inventa ninguna. */
const val PUEDE_INCLUIR_DIFERIDOS: String = "Puede incluir compras diferidas: la cifra exacta la dice el extracto del banco."

/** El rótulo al lado de la cifra de la fila de Pagos cuando la cifra es la del extracto. */
const val ROTULO_DEL_EXTRACTO: String = "del extracto"

/** «o el mínimo, $180.000»: la alternativa debajo de la fila de Pagos. */
fun alternativaDelMinimo(e: ExtractoDeLaTarjeta): String? =
    e.minimo?.let { "o el mínimo, ${montoDelExtracto(it, e.moneda)}" }

/** La pastilla «Total del extracto $1.292.700» de «Anotar pago». */
fun pastillaDelTotalDelExtracto(e: ExtractoDeLaTarjeta): String = "Total del extracto ${montoDelExtracto(e.facturado, e.moneda)}"

/** La pastilla «Mínimo $180.000» de «Anotar pago». */
fun pastillaDelMinimo(minimo: Long, moneda: String): String = "Mínimo ${montoDelExtracto(minimo, moneda)}"

/** La pastilla que deja escribir otra cifra. */
const val PASTILLA_OTRO_MONTO: String = "Otro monto"

/**
 * **El extracto que le corresponde a esta fila de Pagos**, o `null`. Solo la fila de una tarjeta
 * (`card_<cuenta>`) sin pagar, y solo si el pago de ese extracto es el vencimiento de la fila: antes
 * del corte de este mes, la fila habla del pago que viene y el último extracto ya venció.
 */
fun extractoDeLaFila(pago: PagoDelPeriodo, extractos: Map<String, ExtractoDeLaTarjeta>): ExtractoDeLaTarjeta? {
    if (pago.pagado || !pago.ruleId.startsWith(CARD_RULE_PREFIX)) return null
    val e = extractos[pago.ruleId.removePrefix(CARD_RULE_PREFIX)] ?: return null
    return e.takeIf { it.fechaDePago == pago.vence.take(10) && it.moneda == pago.moneda }
}

/** Una pastilla de «Anotar pago» de una tarjeta: su texto y la cifra que pone (`null` = «Otro monto»). */
data class PastillaDelPago(val texto: String, val monto: Long?)

/**
 * **Las pastillas de «Anotar pago» de una tarjeta** (vuelta 62): «Total del extracto $X», «Mínimo $Y» y
 * «Otro monto». Sin extracto ni mínimo, ninguna (la cifra se escribe como siempre). Pura.
 */
fun pastillasDelPagoDeLaTarjeta(tarjeta: com.jvillada.movi.shared.model.CardSummary?): List<PastillaDelPago> {
    if (tarjeta == null) return emptyList()
    val moneda = tarjeta.account.currency
    val extracto = tarjeta.extracto
    val minimo = (extracto?.minimo ?: tarjeta.terms?.pagoMinimo)?.takeIf { it > 0L }
    val conCifra = buildList {
        extracto?.takeIf { it.facturado > 0L }?.let { add(PastillaDelPago(pastillaDelTotalDelExtracto(it), it.facturado)) }
        minimo?.let { add(PastillaDelPago(pastillaDelMinimo(it, moneda), it)) }
    }
    return if (conCifra.isEmpty()) emptyList() else conCifra + PastillaDelPago(PASTILLA_OTRO_MONTO, null)
}
