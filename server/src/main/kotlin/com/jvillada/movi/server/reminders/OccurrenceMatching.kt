package com.jvillada.movi.server.reminders

import com.jvillada.movi.server.time.AppClock
import com.jvillada.movi.server.time.epochMillisToAppDate
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.RecurringRule
import com.jvillada.movi.shared.model.claveComparableDeNombre
import com.jvillada.movi.shared.model.isReservedCategory
import java.time.LocalDate
import java.time.YearMonth
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
 *  - **Identifica** lo estable: el tipo (ingreso/gasto), la ventana alrededor del vencimiento y la
 *    coincidencia de nombre o categoría. La cuenta suma pero no identifica sola (ver abajo).
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
 * Además de las puertas, un candidato tiene que **llamarse igual o compartir la categoría**. La
 * cuenta *suma* —ordena mejor a lo que cae donde el dueño dijo que cae— pero **no alcanza sola**,
 * y esto costó una revisión: `rule.accountId != null` no mira el movimiento, así que con la
 * cuenta como seña suficiente TODO gasto de esa cuenta en la ventana pasaba el mínimo. La regla
 * «Arriendo · Vivienda · $1.800.000 · Bancolombia» proponía el mercado del Éxito de $1.750.000
 * como el arriendo — un toque y el arriendo real dejaba de avisar. Es literalmente el modo de
 * falla que este párrafo decía evitar.
 *
 * Lo que sí queda pasando, y conviene tener presente: dos cosas de la MISMA categoría —«Energía»
 * y «Agua», las dos en «Servicios»— se proponen la una por la otra. No hay forma de separarlas
 * por lo único que comparten los dos modelos, y a diferencia del caso de arriba la propuesta al
 * menos comparte algo que el dueño eligió a mano. Se muestra con su nota y su monto, y el más
 * cercano al esperado va primero.
 *
 * ## La cuenta ya NO filtra
 *
 * Antes, una regla con cuenta descartaba todo lo que estuviera en otra. El silencio era el lado
 * seguro del error, pero dejaba sin propuesta un «Salario» anotado en Nequi que se llamaba
 * exactamente igual que la regla — el nombre idéntico pesando menos que un campo que el dueño
 * llenó de pasada. Ahora la cuenta suma como seña y el orden hace el resto.
 */

/**
 * Cuántos días alrededor del vencimiento se buscan candidatos.
 *
 * Diez a cada lado: cubre el pago adelantado, el sueldo que cae el viernes porque el 25 fue
 * domingo, y el atraso de un par de días hábiles.
 *
 * **Pero nunca hacia atrás más allá del mes del vencimiento** (ver [occurrenceCandidatesFor]).
 * Sin ese piso, una regla de día 1 o 2 —el día típico de un arriendo o una nómina— proponía el
 * pago del mes ANTERIOR para cerrar el vencimiento de este: el arriendo de agosto pagado tarde el
 * 25 de agosto ofrecido como el arriendo de septiembre, con el monto exacto (así que ni siquiera
 * salía el aviso de «no es el monto que anotaste») y sin que ningún texto dijera de qué mes se
 * hablaba. Confirmarlo hacía desaparecer el arriendo de septiembre: fuera de «Próximos», fuera
 * del barrido, sin correo. Perder una propuesta legítima —el sueldo que cayó el último día del
 * mes anterior— cuesta un «Ya me llegó»; cerrar el mes equivocado cuesta plata.
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
    // El piso: nada anterior al mes del vencimiento. Ver el KDoc de OCCURRENCE_WINDOW_DAYS.
    val primerDiaDelPeriodo = YearMonth.from(dueDate).atDay(1)

    return events
        .asSequence()
        .filter { it.id !in usedEventIds }
        .filter { it.type == rule.type }
        .filter { it.transferId == null }
        .filter { !isReservedCategory(it.category) }
        .mapNotNull { event ->
            val fecha = epochMillisToAppDate(event.timestamp, zone)
            val dias = ChronoUnit.DAYS.between(dueDate, fecha)
            if (abs(dias) > windowDays) return@mapNotNull null
            if (fecha.isBefore(primerDiaDelPeriodo)) return@mapNotNull null
            val nombrePega = claveRegla.isNotEmpty() &&
                (claveComparableDeNombre(event.description) == claveRegla ||
                    claveComparableDeNombre(event.merchant.orEmpty()) == claveRegla)
            val categoriaPega = claveCategoria.isNotEmpty() &&
                claveComparableDeNombre(event.category) == claveCategoria
            // La seña mínima es el NOMBRE o la CATEGORÍA. La cuenta no basta sola: no dice nada
            // del movimiento, solo de dónde está guardado (ver el KDoc de arriba).
            if (!nombrePega && !categoriaPega) return@mapNotNull null
            val laCuentaPega = rule.accountId != null && event.accountId == rule.accountId
            // El nombre pesa más que la categoría: «Salario» dicho igual identifica mejor que
            // «Otros ingresos» compartido con media docena de cosas. La cuenta desempata.
            val senas = (if (nombrePega) 3 else 0) +
                (if (categoriaPega) 1 else 0) +
                (if (laCuentaPega) 1 else 0)
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
