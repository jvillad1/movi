package com.jvillada.movi.server.reminders

import com.jvillada.movi.server.time.AppClock
import com.jvillada.movi.server.time.epochMillisToAppDate
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.RecurringRule
import com.jvillada.movi.shared.model.claveComparableDeNombre
import com.jvillada.movi.shared.model.isReservedCategory
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.abs

/**
 * **Emparejamiento SUGERIDO: qué movimiento parece ser la ocurrencia de este recurrente.**
 *
 * Esto no marca nada. Devuelve una lista ordenada de propuestas que la pantalla le muestra al
 * dueño para que él confirme («sí, fue este» / «no fue este»). La asimetría del riesgo manda todo
 * el diseño: **marcar de más es peor que el ruido de hoy** — si la app da por ocurrido algo que no
 * ocurrió, el dueño deja de recibir el aviso de una deuda real, y eso cuesta plata. Un ruido de
 * más solo cuesta un toque.
 *
 * ## El monto NO filtra: ordena
 *
 * Palabras del dueño: «hay meses que mi salario es tal cual lo escribí en la base de datos pero
 * otros meses puede ser menos o más dependiendo de retenciones y cosas similares». O sea que el
 * monto de un recurrente es un **estimado, no un contrato**. Exigir monto exacto —o un margen
 * fijo de ±10 % elegido a ojo— haría fallar justo el caso que motivó la función. Así que:
 *
 *  - **Identifica** lo estable: el tipo (ingreso/gasto), la ventana alrededor del vencimiento, la
 *    cuenta (si la regla tiene una) y la cercanía de nombre o categoría.
 *  - **Ordena** por lo variable: entre los candidatos, el más cercano al monto esperado va
 *    primero — pero uno con una retención de más **no** queda descartado, solo va después.
 *  - **Decide el dueño.** Con confirmación humana no hace falta clavar un margen y rezar.
 *
 * ## Las cuatro puertas cerradas (lo que NUNCA es candidato)
 *
 *  1. **Anulado.** Quien llama pasa solo movimientos vivos (`loadNonVoidedEvents`): un movimiento
 *     anulado no ocurrió.
 *  2. **Pata de traspaso.** Mover plata de una cuenta propia a otra no es el pago del arriendo ni
 *     la llegada del sueldo. Se mira `transferId` **y** la categoría reservada, porque una pata
 *     huérfana (cuenta borrada) pierde el enlace pero no deja de ser lo que fue.
 *  3. **Categoría reservada** («Traspaso», «Saldo inicial», «Pago de tarjeta», «Cuenta
 *     eliminada»): son asientos internos de Movi, no hechos del mes.
 *  4. **Ya usado como ocurrencia** de este u otro recurrente. Sin esto, un mismo ingreso podría
 *     cerrar el «Salario» de agosto y el de septiembre — dos periodos cerrados con una sola
 *     entrada de plata es exactamente el «marcar de más» que hay que evitar.
 *
 * ## Y una señal mínima, para no proponer cualquier cosa
 *
 * Además de las puertas, un candidato tiene que compartir **al menos una** seña de identidad con
 * la regla: el mismo nombre normalizado, la misma categoría, o la cuenta (cuando la regla dice en
 * qué cuenta cae, quien está en esa cuenta ya está señalado). Sin ese mínimo, un gasto del 25
 * cualquiera se propondría como «tu arriendo», y una propuesta que se equivoca envalentona a
 * decir que sí sin mirar. Cuando no hay ninguna señal, la app no propone nada y queda el camino
 * manual: «Ya lo pagué» / «Ya me llegó», que cierra el periodo sin emparejar.
 */

/**
 * Cuántos días alrededor del vencimiento se buscan candidatos.
 *
 * Diez a cada lado: cubre el pago adelantado, el sueldo que cae el viernes porque el 25 fue
 * domingo, y el atraso de un par de días hábiles. Y deja diez días de aire contra la ventana del
 * periodo vecino (los vencimientos están a ~30 días), así que un mismo movimiento rara vez queda
 * propuesto para dos periodos — y si queda, la puerta 4 impide que cierre los dos.
 */
const val OCCURRENCE_WINDOW_DAYS: Long = 10

/** Cuántas propuestas se le muestran al dueño. Más de tres es una lista, no una propuesta. */
const val MAX_OCCURRENCE_CANDIDATES: Int = 3

/**
 * Los movimientos que **podrían** ser la ocurrencia de [rule] en el vencimiento [dueDate], del más
 * probable al menos. Ver el KDoc de arriba para el porqué de cada regla.
 *
 * @param events       movimientos vivos (no anulados) del usuario.
 * @param usedEventIds ids ya sellados como ocurrencia de cualquier regla.
 */
fun occurrenceCandidatesFor(
    rule: RecurringRule,
    dueDate: LocalDate,
    events: List<FinancialEvent>,
    usedEventIds: Set<String> = emptySet(),
    zone: ZoneId = AppClock.zone,
    windowDays: Long = OCCURRENCE_WINDOW_DAYS,
    max: Int = MAX_OCCURRENCE_CANDIDATES,
): List<FinancialEvent> {
    val claveRegla = claveComparableDeNombre(rule.name)
    val claveCategoria = claveComparableDeNombre(rule.category)

    return events
        .asSequence()
        .filter { it.id !in usedEventIds }
        .filter { it.type == rule.type }
        .filter { it.transferId == null }
        .filter { !isReservedCategory(it.category) }
        // La cuenta de la regla, cuando la tiene, es la seña más fuerte que dio el dueño sobre
        // dónde cae esto todos los meses: se respeta como filtro, no como preferencia. Si él
        // anotó el movimiento en otra cuenta no habrá propuesta — y ese silencio es correcto:
        // mejor que no proponga a que proponga el movimiento equivocado.
        .filter { rule.accountId == null || it.accountId == rule.accountId }
        .mapNotNull { event ->
            val fecha = epochMillisToAppDate(event.timestamp, zone)
            val dias = ChronoUnit.DAYS.between(dueDate, fecha)
            if (abs(dias) > windowDays) return@mapNotNull null
            val nombrePega = claveRegla.isNotEmpty() &&
                (claveComparableDeNombre(event.description) == claveRegla ||
                    claveComparableDeNombre(event.merchant.orEmpty()) == claveRegla)
            val categoriaPega = claveCategoria.isNotEmpty() &&
                claveComparableDeNombre(event.category) == claveCategoria
            val laCuentaLoSenala = rule.accountId != null
            if (!nombrePega && !categoriaPega && !laCuentaLoSenala) return@mapNotNull null
            // El nombre pesa más que la categoría: «Salario» dicho igual identifica mejor que
            // «Otros ingresos» compartido con media docena de cosas.
            val senas = (if (nombrePega) 2 else 0) + (if (categoriaPega) 1 else 0)
            Candidato(
                event = event,
                senas = senas,
                distanciaMonto = abs(event.amount - rule.amount),
                distanciaDias = abs(dias),
            )
        }
        // Señas primero (identidad), después el monto (lo variable, que ordena y no filtra),
        // después la cercanía al vencimiento. El id al final para que dos candidatos idénticos
        // salgan siempre en el mismo orden — una propuesta que baila entre recargas se ve como
        // un error.
        .sortedWith(
            compareByDescending<Candidato> { it.senas }
                .thenBy { it.distanciaMonto }
                .thenBy { it.distanciaDias }
                .thenBy { it.event.id },
        )
        .take(max)
        .map { it.event }
        .toList()
}

private data class Candidato(
    val event: FinancialEvent,
    val senas: Int,
    val distanciaMonto: Long,
    val distanciaDias: Long,
)
