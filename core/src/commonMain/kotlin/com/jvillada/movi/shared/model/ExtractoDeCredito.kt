package com.jvillada.movi.shared.model

import kotlin.math.abs
import kotlin.math.round
import kotlinx.serialization.Serializable

/**
 * # Lo que dice el extracto de un crédito, y la propuesta de actualizar Movi con eso
 *
 * El extracto de un crédito no trae movimientos para importar: trae **cómo va la deuda** —el saldo
 * de capital, el interés y el seguro de la cuota que viene, la tasa—. Hasta acá Movi lo rechazaba
 * («no contiene transacciones importables») y el dueño corregía a mano en la base, uno por uno, lo
 * que el papel decía: la mitad de los 33 respaldos de créditos de la revisión del 29-sep son eso.
 *
 * Ahora la lectura del extracto arma una **propuesta**: «El extracto dice: saldo $X (Movi $Y),
 * interés del mes $A (Movi estimaba $B). ¿Actualizar?». **Nada se actualiza solo.** «Actualizar»
 * (`POST /api/credits/{id}/extracto`) escribe en las condiciones el seguro, la tasa y el interés
 * del extracto ([CreditTerms.interesReal]), y anota la diferencia del saldo con el ajuste de
 * siempre (categoría «Ajuste de saldo»), fechado en el corte del extracto.
 */
@Serializable
data class ResumenDelExtractoDeCredito(
    /** El número de la obligación, entero («290109695»). */
    val numero: String? = null,
    /** El saldo de capital al corte: lo que la cuota amortiza, y lo que Movi lleva como deuda. */
    val saldoCapital: Long? = null,
    /** El interés de la cuota que viene (la columna «cuota a pagar»). */
    val interesDelPeriodo: Long? = null,
    /** El seguro de vida de la cuota que viene. */
    val seguro: Long? = null,
    /** La tasa efectiva anual, en por ciento (11.27). */
    val tasaEa: Double? = null,
    /** El total a pagar de la cuota que viene. */
    val cuota: Long? = null,
    /** El vencimiento de la cuota que viene, ISO (`2026-09-15`). Decide de qué cuota es el interés. */
    val fechaDePago: String? = null,
    /** El corte del extracto, ISO. El saldo se compara —y el ajuste se fecha— ahí. */
    val fechaDeCorte: String? = null,
) {
    /** ¿Trae algo que se pueda proponer? Un papel sin ninguna de las cuatro cifras no es una propuesta. */
    val traeAlgo: Boolean get() = saldoCapital != null || interesDelPeriodo != null || seguro != null || tasaEa != null
}

/**
 * **La propuesta**: lo que dice el extracto al lado de lo que dice Movi, de la cuenta que el número
 * del extracto nombra. Sin [cuentaId] el crédito no se encontró en Movi y no hay nada que actualizar.
 */
@Serializable
data class PropuestaDelExtractoDeCredito(
    val resumen: ResumenDelExtractoDeCredito,
    val cuentaId: String? = null,
    val cuentaNombre: String? = null,
    /** La deuda de Movi en el corte del extracto, sin anulados. */
    val saldoEnMovi: Long? = null,
    /** Lo que Movi estimaba de interés para esa cuota: `saldo × tasa mensual`, como [desglosarCuota]. */
    val interesEstimado: Long? = null,
    val seguroEnMovi: Long? = null,
    val tasaEnMovi: Double? = null,
)

/**
 * **Los renglones de la propuesta**, uno por cifra que el extracto dice: «Saldo $40.710.555 (Movi
 * $41.093.905)», «Interés del mes $594.567 (Movi estimaba $363.905)». Lo que coincide se dice igual
 * («Seguro $156.800, igual que en Movi»): el dueño tiene que ver qué se miró, no solo qué cambia.
 */
fun renglonesDelExtractoDeCredito(p: PropuestaDelExtractoDeCredito): List<String> = buildList {
    val r = p.resumen
    r.saldoCapital?.let { add(comparado("Saldo", plata(it), p.saldoEnMovi?.let(::plata), it == p.saldoEnMovi, "Movi")) }
    r.interesDelPeriodo?.let {
        add(comparado("Interés del mes", plata(it), p.interesEstimado?.let(::plata), it == p.interesEstimado, "Movi estimaba"))
    }
    r.seguro?.let { add(comparado("Seguro", plata(it), p.seguroEnMovi?.let(::plata) ?: "sin seguro", it == p.seguroEnMovi, "Movi")) }
    r.tasaEa?.let {
        val igual = p.tasaEnMovi != null && abs(it - p.tasaEnMovi) < 0.005
        add(comparado("Tasa", tasa(it), p.tasaEnMovi?.let(::tasa), igual, "Movi"))
    }
}

/** ¿Algo de lo que dice el extracto es distinto de lo que tiene Movi? Si no, no hay nada que actualizar. */
fun hayAlgoQueActualizar(p: PropuestaDelExtractoDeCredito): Boolean {
    if (p.cuentaId == null) return false
    val r = p.resumen
    return (r.saldoCapital != null && r.saldoCapital != p.saldoEnMovi) ||
        // El interés siempre se guarda —es el de una cuota nueva—, aunque coincida con la estimación.
        (r.interesDelPeriodo != null && r.fechaDePago != null) ||
        (r.seguro != null && r.seguro != p.seguroEnMovi) ||
        (r.tasaEa != null && (p.tasaEnMovi == null || abs(r.tasaEa - p.tasaEnMovi) >= 0.005))
}

private fun comparado(que: String, delExtracto: String, deMovi: String?, igual: Boolean, quien: String): String = when {
    deMovi == null -> "$que $delExtracto"
    igual -> "$que $delExtracto, igual que en Movi"
    else -> "$que $delExtracto ($quien $deMovi)"
}

private fun plata(valor: Long): String = "$" + conPuntosDeMiles(valor)

/** «11,27 % E.A.». */
private fun tasa(valor: Double): String {
    val centesimas = round(valor * 100).toLong()
    val decimales = (centesimas % 100).toString().padStart(2, '0').trimEnd('0')
    return (centesimas / 100).toString() + (if (decimales.isEmpty()) "" else ",$decimales") + " % E.A."
}
