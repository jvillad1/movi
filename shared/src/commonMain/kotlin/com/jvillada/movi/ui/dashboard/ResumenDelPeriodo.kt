package com.jvillada.movi.ui.dashboard

import com.jvillada.movi.shared.model.Budget
import com.jvillada.movi.shared.model.OccurrenceState
import com.jvillada.movi.shared.model.PeriodSettings
import com.jvillada.movi.shared.model.PeriodoFinanciero
import com.jvillada.movi.shared.model.UpcomingPayment
import com.jvillada.movi.shared.model.ventanaDe
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant

/**
 * # El Inicio contesta «¿cómo voy en este período?»
 *
 * Todo lo de este archivo es **puro**: recibe lo que el Inicio ya tenía cargado y devuelve lo que
 * hay que pintar. Sin red, sin reloj propio, sin Compose — para que las decisiones sobre la plata
 * del dueño se puedan probar sin abrir una pantalla.
 *
 * El pedido, con sus palabras: *«que la home sea tipo un resumen del periodo … en qué categorías
 * hice movimientos y en dónde se me fue la plata, qué me falta por pagar y qué ya pagué tipo
 * checklist … que también tire insights de qué cosas debería revisar»*.
 *
 * Las tres respuestas viven acá: [categoriasDelPeriodo], [checklistDelPeriodo] y
 * [cosasParaRevisar].
 */

// ── En qué se fue la plata ───────────────────────────────────────────────────

/**
 * Una categoría del período, con lo que pesa dentro del gasto total.
 *
 * @param fraccion de 0 a 1 sobre el gasto TOTAL del período, no sobre la categoría más grande. Una
 *   barra que llena el ancho porque es la mayor de tres categorías chicas diría algo falso.
 */
data class CategoriaDelPeriodo(
    val nombre: String,
    val gastado: Long,
    val fraccion: Float,
    val limite: Long? = null,
) {
    /** ¿Se pasó del presupuesto que el dueño le puso? Sin presupuesto no hay nada que decir. */
    val superada: Boolean get() = limite != null && limite > 0 && gastado > limite
}

/**
 * Las categorías del período, de mayor a menor, con el resto agrupado.
 *
 * **Agrupar la cola importa más que mostrarla.** Con veinte categorías, las quince últimas son una
 * lista que nadie lee y que empuja el resto del Inicio fuera de la pantalla; pero borrarlas haría
 * que las barras no sumen el gasto del período y el dueño no podría cuadrar la cifra de arriba con
 * lo de abajo. Van juntas en «Otras N categorías».
 *
 * Los montos llegan ya acotados al período: los calcula el server en `GET /api/dashboard/summary`,
 * con todo lo que sabe (todos los dispositivos, SMS, importaciones, anulados afuera).
 *
 * @param cuantas cuántas se muestran sueltas antes de agrupar.
 */
fun categoriasDelPeriodo(
    gastoPorCategoria: Map<String, Long>,
    presupuestos: List<Budget> = emptyList(),
    cuantas: Int = 5,
): List<CategoriaDelPeriodo> {
    val positivas = gastoPorCategoria.filterValues { it > 0 }
    val total = positivas.values.sum()
    if (total <= 0L) return emptyList()
    val limitePorCategoria = presupuestos.associate { it.category to it.monthlyLimit }

    val ordenadas = positivas.entries.sortedWith(
        // Por monto, y a igual monto por nombre: sin el desempate, dos categorías con la misma
        // cifra se intercambian de lugar entre recargas sin que nada haya cambiado.
        compareByDescending<Map.Entry<String, Long>> { it.value }.thenBy { it.key.lowercase() },
    )
    val sueltas = ordenadas.take(cuantas).map { (nombre, gastado) ->
        CategoriaDelPeriodo(
            nombre = nombre,
            gastado = gastado,
            fraccion = gastado.toFloat() / total,
            limite = limitePorCategoria[nombre],
        )
    }
    val cola = ordenadas.drop(cuantas)
    if (cola.isEmpty()) return sueltas
    val restante = cola.sumOf { it.value }
    return sueltas + CategoriaDelPeriodo(
        nombre = if (cola.size == 1) cola.first().key else "Otras ${cola.size} categorías",
        gastado = restante,
        fraccion = restante.toFloat() / total,
        limite = if (cola.size == 1) limitePorCategoria[cola.first().key] else null,
    )
}

// ── Qué pagué y qué me falta ─────────────────────────────────────────────────

/** Una obligación del período, ya pagada o todavía pendiente. */
data class PagoDelPeriodo(
    val ruleId: String,
    val nombre: String,
    val monto: Long,
    /** `true` = el dueño ya lo dio por ocurrido en ESTE período. */
    val pagado: Boolean,
    /** Días para el vencimiento; negativo = ya venció y sigue sin pagarse. */
    val diasParaVencer: Int,
    /** Su monto es el SALDO de una deuda, no lo que va a salir de la cuenta. */
    val montoEsSaldo: Boolean = false,
) {
    val vencido: Boolean get() = !pagado && diasParaVencer < 0
}

/**
 * El checklist del período: **todo lo que se paga en este período**, con lo hecho tildado.
 *
 * Tres decisiones:
 *
 * 1. **Es del PERÍODO, no de los próximos siete días.** «Próximos pagos» contestaba «¿qué se viene
 *    ya?»; esto contesta «¿cuánto me falta de lo de este mes?», que es lo que el dueño pidió. Una
 *    cuota que vence pasado el corte no entra: es del período siguiente.
 * 2. **Lo pagado no desaparece.** Un checklist sin lo tildado no deja ver el avance, que es la
 *    mitad de para qué sirve.
 * 3. **Primero lo que falta, y dentro de eso lo vencido.** Lo que ya está hecho no compite por la
 *    atención, así que va al final aunque venza antes.
 *
 * El sello de «ya ocurrió» lo pone el dueño en Movimientos y viaja en [ocurrencias]; acá solo se
 * lee. Una regla sin ocurrencia conocida cuenta como pendiente: es el lado seguro de equivocarse
 * —recuerda algo que quizá ya pagó— contra dar por pagado algo que no.
 */
fun checklistDelPeriodo(
    upcoming: List<UpcomingPayment>,
    ocurrencias: List<OccurrenceState>,
    periodo: PeriodoFinanciero,
    settings: PeriodSettings,
): List<PagoDelPeriodo> {
    val ventana = ventanaDe(periodo, settings)
    val selladas = ocurrencias.filter { it.occurred }.map { it.ruleId }.toSet()
    return upcoming
        .filter { it.epochDelVencimiento() in ventana }
        .map { pago ->
            PagoDelPeriodo(
                ruleId = pago.rule.id,
                nombre = pago.rule.name,
                monto = pago.rule.amount,
                pagado = pago.rule.id in selladas,
                diasParaVencer = pago.daysUntil,
                montoEsSaldo = pago.rule.montoEsSaldo,
            )
        }
        .sortedWith(
            compareBy<PagoDelPeriodo> { it.pagado }
                .thenBy { it.diasParaVencer }
                .thenBy { it.nombre.lowercase() },
        )
}

/**
 * El vencimiento de un pago, en epoch ms, para poder preguntarle si cae en la ventana del período.
 *
 * `dueDate` viene del server como `"2026-09-16"` y se arma el mediodía de Bogotá, igual que el
 * resto de la app: la medianoche exacta cae justo en el borde de la ventana y un pago del día del
 * corte podía quedar afuera por un milisegundo.
 */
internal fun UpcomingPayment.epochDelVencimiento(): Long {
    val partes = dueDate.split("-")
    if (partes.size != 3) return 0L
    val anio = partes[0].toIntOrNull() ?: return 0L
    val mes = partes[1].toIntOrNull() ?: return 0L
    val dia = partes[2].toIntOrNull() ?: return 0L
    return LocalDateTime(anio, mes, dia, 12, 0)
        .toInstant(TimeZone.of("America/Bogota"))
        .toEpochMilliseconds()
}

/** Cuánto falta por pagar de este período, sin contar lo que es un saldo y no una cuota. */
fun faltaPorPagar(checklist: List<PagoDelPeriodo>): Long =
    checklist.filter { !it.pagado && !it.montoEsSaldo }.sumOf { it.monto }

// ── Qué debería revisar ──────────────────────────────────────────────────────

/** Algo que el Inicio sugiere mirar, con la pantalla donde se resuelve. */
data class CosaParaRevisar(
    val texto: String,
    val detalle: String,
    val destino: DestinoDeRevision,
    /** Las urgentes van primero y en ámbar; el resto es una sugerencia, no una alarma. */
    val urgente: Boolean = false,
)

/** A dónde lleva tocar una sugerencia. Un enum y no una `Screen` para que esto siga siendo puro. */
enum class DestinoDeRevision { MOVIMIENTOS, RECURRENTES, PRESUPUESTOS, CREDITOS, SMS, SUSCRIPCIONES }

/**
 * **Lo que el Inicio recomienda mirar hoy**, de lo más urgente a lo más opcional.
 *
 * La regla que ordena todo esto: **cada sugerencia tiene que poder accionarse**. «Gastaste más que
 * el mes pasado» no es una sugerencia, es una observación; «la cuota del vehículo venció y sigue
 * sin marcarse» sí lo es, porque hay algo que hacer y una pantalla donde hacerlo.
 *
 * Por eso ninguna sale de una corazonada: todas salen de un dato que ya está cargado y llevan a la
 * pantalla donde se resuelve. Y son **como mucho [cuantas]**: una lista larga de consejos es ruido,
 * y el ruido enseña a ignorar la sección entera.
 */
fun cosasParaRevisar(
    checklist: List<PagoDelPeriodo>,
    categorias: List<CategoriaDelPeriodo>,
    flujoDelPeriodo: Long,
    smsPorConfirmar: Int,
    candidatosAPagoDeTarjeta: Int,
    cuantas: Int = 4,
): List<CosaParaRevisar> {
    val todas = buildList {
        val vencidos = checklist.filter { it.vencido }
        if (vencidos.isNotEmpty()) {
            add(
                CosaParaRevisar(
                    texto = if (vencidos.size == 1) "«${vencidos.first().nombre}» venció y no está marcado"
                    else "${vencidos.size} pagos vencidos sin marcar",
                    detalle = "Si ya lo pagaste, márcalo para que deje de avisarte.",
                    destino = DestinoDeRevision.RECURRENTES,
                    urgente = true,
                ),
            )
        }
        if (smsPorConfirmar > 0) {
            add(
                CosaParaRevisar(
                    texto = "$smsPorConfirmar ${if (smsPorConfirmar == 1) "mensaje" else "mensajes"} del banco sin confirmar",
                    detalle = "Hasta confirmarlos no cuentan en el gasto del período.",
                    destino = DestinoDeRevision.SMS,
                    urgente = smsPorConfirmar >= 10,
                ),
            )
        }
        val superadas = categorias.filter { it.superada }
        if (superadas.isNotEmpty()) {
            add(
                CosaParaRevisar(
                    texto = if (superadas.size == 1) "Te pasaste del presupuesto de ${superadas.first().nombre}"
                    else "Te pasaste en ${superadas.size} presupuestos",
                    detalle = "Todavía estás en el período: puedes ajustar el límite o frenar el gasto.",
                    destino = DestinoDeRevision.PRESUPUESTOS,
                ),
            )
        }
        if (candidatosAPagoDeTarjeta > 0) {
            add(
                CosaParaRevisar(
                    texto = "$candidatosAPagoDeTarjeta ${if (candidatosAPagoDeTarjeta == 1) "movimiento parece" else "movimientos parecen"} pago de tarjeta",
                    detalle = "Marcarlos evita contarlos dos veces: como gasto y como menos deuda.",
                    destino = DestinoDeRevision.MOVIMIENTOS,
                ),
            )
        }
        if (flujoDelPeriodo < 0) {
            add(
                CosaParaRevisar(
                    texto = "Vas gastando más de lo que entró este período",
                    detalle = "La categoría más pesada es ${categorias.firstOrNull()?.nombre ?: "la primera de la lista"}.",
                    destino = DestinoDeRevision.MOVIMIENTOS,
                ),
            )
        }
    }
    return todas.sortedByDescending { it.urgente }.take(cuantas)
}

/** El monto que el checklist dice que ya se pagó, para el rótulo de avance. */
fun yaPagado(checklist: List<PagoDelPeriodo>): Long =
    checklist.filter { it.pagado && !it.montoEsSaldo }.sumOf { it.monto }

/** Un pago de [checklist] que sirva de ejemplo de lo que urge, o `null` si no falta nada. */
fun loQueUrge(checklist: List<PagoDelPeriodo>): PagoDelPeriodo? =
    checklist.firstOrNull { !it.pagado }

/** El estado del checklist en una línea: «3 de 7 pagados». */
fun avanceDelChecklist(checklist: List<PagoDelPeriodo>): Pair<Int, Int> =
    checklist.count { it.pagado } to checklist.size
