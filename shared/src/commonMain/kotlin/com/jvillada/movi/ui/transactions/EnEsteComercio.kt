package com.jvillada.movi.ui.transactions

import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.claveDeComercio
import com.jvillada.movi.shared.model.isOpeningBalance
import com.jvillada.movi.shared.model.marcaDe
import com.jvillada.movi.shared.time.AppTimeZone
import com.jvillada.movi.ui.fecha.etiquetaDeFecha
import com.jvillada.movi.ui.fecha.fechaDeEpoch
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * # «En este comercio»: cuántas veces este período, y cuánto
 *
 * Ola 2b · el detalle de un movimiento se lee primero. Debajo de la categoría, una línea como
 * «4 veces este período · $142.800» que al tocarla abre Movimientos con la búsqueda puesta en ese
 * comercio.
 *
 * **Sale de lo que la pantalla ya tiene cargado**: Movimientos pasa los movimientos del período del
 * movimiento abierto (los mismos `getEventsByDay` que pinta la lista). No hay lectura nueva ni ruta
 * nueva. Quien abre la hoja sin esa lista a mano (el detalle de una cuenta, «Por revisar») no la
 * pasa y la línea no aparece.
 *
 * Dos movimientos son «del mismo comercio» con la misma regla que ya agrupa en el resto de Movi: la
 * marca del catálogo (`marcaDe`) si la hay, y si no la clave de comercio de los avisos de «Lo que se
 * sale de lo normal» (`claveDeComercio`). Solo se suman los del mismo tipo (gasto con gasto) y la
 * misma moneda, y nunca la mitad de un traspaso ni un saldo inicial.
 */
data class EnEsteComercio(
    /** Cuántos movimientos, contando el abierto. Siempre 2 o más: con uno solo no hay nada que decir. */
    val veces: Int,
    val total: Long,
    val moneda: String,
    /** Lo que se escribe en la búsqueda de Movimientos para ver esos mismos movimientos. */
    val busqueda: String,
)

/** La clave con la que se agrupa: la marca primero, la clave de comercio después. */
internal fun claveParaAgrupar(evento: FinancialEvent): String? =
    marcaDe(evento)?.let { "marca:${it.id}" } ?: claveDeComercio(evento)?.let { "comercio:$it" }

/**
 * Los movimientos de [delPeriodo] que son del mismo comercio que [evento] (él incluido), o `null`
 * si no hay ningún otro.
 */
fun enEsteComercio(evento: FinancialEvent, delPeriodo: List<FinancialEvent>): EnEsteComercio? {
    if (evento.transferId != null || isOpeningBalance(evento)) return null
    val clave = claveParaAgrupar(evento) ?: return null
    val mismos = delPeriodo.filter {
        it.transferId == null &&
            !isOpeningBalance(it) &&
            it.type == evento.type &&
            it.currency == evento.currency &&
            claveParaAgrupar(it) == clave
    }.distinctBy { it.id }
    val conEste = if (mismos.any { it.id == evento.id }) mismos else mismos + evento
    if (conEste.size < 2) return null
    return EnEsteComercio(
        veces = conEste.size,
        total = conEste.sumOf { com.jvillada.movi.shared.model.montoContado(it) },
        moneda = evento.currency,
        busqueda = busquedaParaElComercio(evento, conEste),
    )
}

/**
 * Qué escribir en la búsqueda para que salgan esos movimientos. Se prueba el nombre de la marca, el
 * comercio y la descripción, y gana el primero que los encuentre a todos con [matchesQuery] —la
 * misma búsqueda de la pantalla—; si ninguno los encuentra a todos, el que encuentre más.
 */
internal fun busquedaParaElComercio(evento: FinancialEvent, grupo: List<FinancialEvent>): String {
    val candidatos = listOfNotNull(
        marcaDe(evento)?.nombre,
        evento.merchant?.trim()?.takeIf { it.isNotEmpty() },
        evento.description.trim().takeIf { it.isNotEmpty() },
    ).distinct()
    if (candidatos.isEmpty()) return ""
    return candidatos.firstOrNull { c -> grupo.all { matchesQuery(it, c) } }
        ?: candidatos.maxBy { c -> grupo.count { matchesQuery(it, c) } }
}

/** «4 veces este período · $142.800». El monto lo formatea quien llama (con su moneda). */
fun textoDeEnEsteComercio(resumen: EnEsteComercio, montoFormateado: String): String =
    "${resumen.veces} veces este período · $montoFormateado"

/**
 * La línea de debajo del monto: «Hoy, 12:40 · Nu».
 *
 * La hora solo se dice si es una hora de verdad. Un movimiento anotado para otro día se sella al
 * **mediodía** (ver `epochAlMediodia`): decir «12:00» de un café de ayer sería inventar una hora.
 * Sin el nombre de la cuenta (la lista todavía no llegó) se dice solo el cuándo.
 */
fun lineaDeCuandoYDonde(
    evento: FinancialEvent,
    nombreDeLaCuenta: String?,
    hoy: LocalDate,
    zone: TimeZone = AppTimeZone.zone,
): String {
    val instante = Instant.fromEpochMilliseconds(evento.timestamp).toLocalDateTime(zone)
    val dia = etiquetaDeFecha(fechaDeEpoch(evento.timestamp, zone), hoy)
    val esElMediodiaDeRelleno = instante.hour == 12 && instante.minute == 0 && instante.second == 0 &&
        instante.nanosecond == 0
    val cuando = if (esElMediodiaDeRelleno) dia
    else "$dia, ${instante.hour.toString().padStart(2, '0')}:${instante.minute.toString().padStart(2, '0')}"
    return listOfNotNull(cuando, nombreDeLaCuenta?.takeIf { it.isNotBlank() }).joinToString(" · ")
}
