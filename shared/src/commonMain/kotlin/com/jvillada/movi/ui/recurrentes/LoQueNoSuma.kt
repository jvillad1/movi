package com.jvillada.movi.ui.recurrentes

import com.jvillada.movi.shared.model.CARD_RULE_PREFIX
import com.jvillada.movi.ui.dashboard.PagoDelPeriodo

/*
 * # Lo que no suma se dice ahí mismo (vuelta 86)
 *
 * La revisión 9 vio «Falta por pagar $3.405.600» con la fila «Pago tarjeta Tarjeta Nu $540.000 saldo» y su
 * «Anotar pago» en la misma lista, y nada decía que esa fila no estaba en el total: solo lo explicaba la
 * tarjeta «Flujo libre», tres pantallas abajo. En el Inicio, el widget mostraba la fila de Nu sin monto,
 * callado.
 *
 * **No cambia qué suma.** Estas funciones solo preguntan a [com.jvillada.movi.shared.model.faltaPorPagar]
 * lo que ya hace: una fila pendiente que aporta cero a esa suma es la de una tarjeta (su monto es el SALDO,
 * una deuda) cuyo mínimo de este período no se conoce. Ver [aporteALoQueFalta].
 */

/** Por qué una fila pendiente no suma en «Falta por pagar». */
enum class PorQueNoSuma {
    /** La tarjeta no tiene su pago mínimo cargado: no se sabe cuánto hay que pagar. */
    FALTA_EL_MINIMO,

    /**
     * La tarjeta tiene un mínimo, pero no el de un extracto que venza en esta fecha (sin día de corte, o
     * la fila es de otro extracto): `faltaPorPagar` solo suma el mínimo del extracto.
     */
    FALTA_EL_CORTE,
}

/**
 * **Lo que esta fila aporta a «Falta por pagar»**, con la MISMA regla de
 * [com.jvillada.movi.shared.model.faltaPorPagar] fila por fila (la suma de esto sobre el checklist es esa
 * cifra; lo vigila `Vuelta86Test`). Pura.
 */
fun aporteALoQueFalta(pago: PagoDelPeriodo): Long = when {
    pago.esIngreso -> 0L
    !pago.montoEsSaldo -> if (!pago.pagado && !pago.esAbono) pago.monto else 0L
    pago.hecho -> 0L
    else -> pago.restoDelMinimo ?: pago.faltaDelMinimo ?: 0L
}

/**
 * **Por qué [pago] no suma en «Falta por pagar»**, o `null` si suma (o si no tiene por qué: ya está hecho,
 * es un ingreso o un abono planeado). Solo una tarjeta puede quedar afuera estando pendiente. Pura.
 */
fun porQueNoSuma(pago: PagoDelPeriodo): PorQueNoSuma? {
    if (pago.hecho || pago.esIngreso || pago.esAbono || !pago.montoEsSaldo) return null
    if (aporteALoQueFalta(pago) > 0L) return null
    return if (pago.pagoMinimo == null) PorQueNoSuma.FALTA_EL_MINIMO else PorQueNoSuma.FALTA_EL_CORTE
}

/** La píldora de la fila que no suma: «No suma: falta el mínimo». */
fun pildoraDeLoQueNoSuma(razon: PorQueNoSuma): String = when (razon) {
    PorQueNoSuma.FALTA_EL_MINIMO -> NO_SUMA_FALTA_EL_MINIMO
    PorQueNoSuma.FALTA_EL_CORTE -> NO_SUMA_FALTA_EL_CORTE
}

/** El botón de la fila que no suma: abre la edición de los términos de la tarjeta (`CardTermsSheet`). */
fun accionDeLoQueNoSuma(razon: PorQueNoSuma): String = when (razon) {
    PorQueNoSuma.FALTA_EL_MINIMO -> CARGAR_MINIMO
    PorQueNoSuma.FALTA_EL_CORTE -> CARGAR_CORTE
}

const val NO_SUMA_FALTA_EL_MINIMO: String = "No suma: falta el mínimo"
const val NO_SUMA_FALTA_EL_CORTE: String = "No suma: falta el corte"
const val CARGAR_MINIMO: String = "Cargar mínimo"
const val CARGAR_CORTE: String = "Cargar corte"

/** El tag de la píldora «No suma: …» y el de su botón. */
const val TAG_NO_SUMA: String = "pagos:no-suma"
const val TAG_CARGAR_MINIMO: String = "pagos:cargar-minimo"

/** El tag de «+ Tarjeta Nu sin contar», debajo del total. */
const val TAG_SIN_CONTAR: String = "pagos:sin-contar"

/**
 * **El nombre corto de lo que no suma**: «Pago tarjeta Tarjeta Nu» (así nombra el server la regla de una
 * tarjeta) → «Tarjeta Nu». Pura.
 */
fun nombreDeLoQueNoSuma(pago: PagoDelPeriodo): String =
    pago.nombre.removePrefix(PREFIJO_DEL_PAGO_DE_TARJETA).trim().ifBlank { pago.nombre }

private const val PREFIJO_DEL_PAGO_DE_TARJETA = "Pago tarjeta "

/** Las filas del período que no suman en «Falta por pagar», en el orden en que vienen. Pura. */
fun loQueNoSuma(checklist: List<PagoDelPeriodo>): List<PagoDelPeriodo> = checklist.filter { porQueNoSuma(it) != null }

/**
 * **La línea de debajo del total**: «+ Tarjeta Nu sin contar», «+ Nu y Master sin contar», «+ 3 tarjetas
 * sin contar»; `null` si todo lo pendiente suma. Sin cifra: lo que no suma es justamente lo que no se sabe
 * cuánto es. Pura.
 */
fun lineaDeLoQueNoSuma(checklist: List<PagoDelPeriodo>): String? {
    val nombres = loQueNoSuma(checklist).map(::nombreDeLoQueNoSuma).distinct()
    return when (nombres.size) {
        0 -> null
        1 -> "+ ${nombres.single()} sin contar"
        2 -> "+ ${nombres[0]} y ${nombres[1]} sin contar"
        else -> "+ ${nombres.size} tarjetas sin contar"
    }
}

/** La cuenta de la tarjeta de [pago] (su regla es `card_<cuenta>`), o `null` si no es una tarjeta. Pura. */
fun cuentaDeLaTarjetaDe(pago: PagoDelPeriodo): String? =
    pago.ruleId.takeIf { it.startsWith(CARD_RULE_PREFIX) }?.removePrefix(CARD_RULE_PREFIX)?.ifBlank { null }
