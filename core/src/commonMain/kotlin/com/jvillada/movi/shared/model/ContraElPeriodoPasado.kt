package com.jvillada.movi.shared.model

import com.jvillada.movi.shared.time.AppTimeZone
import com.jvillada.movi.shared.time.epochMillisToAppDate
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.daysUntil
import kotlinx.datetime.plus
import kotlinx.serialization.Serializable

/**
 * # «vs. período pasado»: contra los MISMOS días del anterior
 *
 * «¿En qué se va?» dice cuánto va cada categoría en el período. La pregunta que sigue es «¿y eso es
 * mucho?», y la vara más honesta que Movi tiene es el período anterior del mismo dueño.
 *
 * **El período en curso se compara contra los mismos días transcurridos del anterior, no contra el
 * anterior entero.** El 3 de un período, «Comida $180.000» contra los $1,4M de TODO el período
 * pasado diría «vas $1,2M por debajo» —y todos los días, hasta el último, el dueño leería que gasta
 * menos que antes—. Contra los primeros 3 días del anterior la comparación significa algo desde el
 * primer día.
 *
 * ## Cómo se cuentan los días
 *
 * - Hoy es el día N del período en curso **contando el primero y hoy** (el primer día del período es
 *   el día 1). Hoy cuenta entero aunque no haya terminado: lo gastado hoy ya está en la cifra del
 *   período en curso.
 * - Del anterior se toman sus primeros N días, **desde su propio arranque** (el del corte, o el que el
 *   dueño declaró a mano: ver [inicioDelPeriodo]). Un período que arrancó el 24 en vez del 25 se mide
 *   desde el 24.
 * - Si el anterior tiene menos de N días (un febrero, o uno que el siguiente acortó al arrancar antes),
 *   se toma entero: no hay más días que comparar.
 * - «Hoy» se decide en la zona de la app ([AppTimeZone]): las 11 p. m. del 24 en Bogotá son el 25 en
 *   UTC, y con corte 25 eso sería el día 1 del período siguiente si se mirara en la zona del server.
 *
 * Puro: el instante y los ajustes entran por parámetro.
 */

/**
 * La franja del período anterior que se compara, y cuántos días tiene.
 *
 * @property ventana `[inicio, fin)` en epoch ms, como [ventanaDe] (el último milisegundo incluido).
 * @property dias los días de esa franja: los N de hoy, o menos si el anterior era más corto.
 */
data class AnteriorAMismaAltura(
    val ventana: LongRange,
    val dias: Int,
    /** Vuelta 140: el período anterior **entero** (no solo sus primeros días), para [sePuedeCompararContraElAnterior]. */
    val entero: LongRange = ventana,
)

/**
 * **La franja del período anterior a la misma altura que hoy** (ver el KDoc del archivo).
 *
 * @param ahora epoch ms; el día se decide en la zona de la app.
 */
fun anteriorAMismaAltura(ahora: Long, settings: PeriodSettings): AnteriorAMismaAltura {
    val hoy = epochMillisToAppDate(ahora)
    val enCurso = periodoDe(ahora, settings)
    val inicio = inicioDelPeriodo(enCurso, settings)
    // Contando el primero y hoy: el primer día del período es el día 1, nunca el 0.
    val transcurridos = (inicio.daysUntil(hoy) + 1).coerceAtLeast(1)
    // Vuelta 105 · el anterior es el homólogo (D6): en la quincena, la misma quincena del mes pasado.
    val homologo = periodoHomologo(enCurso, settings)
    val inicioDelAnterior = inicioDelPeriodo(homologo, settings)
    // El anterior termina donde arranca el que le sigue (ver [ventanaDe]; en el mensual, el en curso):
    // nunca se pasa de ahí.
    val finDelAnterior = inicioDelPeriodo(periodoSiguiente(homologo, settings), settings)
    val corte = minOf(inicioDelAnterior.plus(transcurridos, DateTimeUnit.DAY), finDelAnterior)
    return AnteriorAMismaAltura(
        ventana = enEpoch(inicioDelAnterior) until enEpoch(corte),
        dias = inicioDelAnterior.daysUntil(corte),
        entero = enEpoch(inicioDelAnterior) until enEpoch(finDelAnterior),
    )
}

/**
 * **¿Se puede comparar contra el período (o los períodos) de antes?** (vuelta 140, issue #696) Una sola
 * regla para «En qué se va», el panel de Actividad y el «vs. período pasado» del Inicio (server):
 *
 * - Movi tiene que haberlo visto entero: el primer movimiento del dueño ([primerMovimiento], cualquiera)
 *   es anterior o igual a su arranque ([inicioDelAnterior], epoch ms). La guarda de siempre.
 * - Y **ninguno de esos períodos puede estar vacío** ([movimientosPorPeriodo]: cuántos movimientos,
 *   de cualquier tipo y sin anulados, tiene cada uno). Un período sin un solo movimiento entre dos con
 *   movimientos es un hueco en la historia (el octubre de Caro, cuyos extractos no subió), no un mes
 *   en que no gastó nada: compararle encima decía «$4,7M más que el período pasado ($0)» y ponía
 *   flechas de subida en todo.
 */
fun sePuedeCompararContraElAnterior(primerMovimiento: Long?, inicioDelAnterior: Long, movimientosPorPeriodo: List<Int>): Boolean =
    primerMovimiento != null && primerMovimiento <= inicioDelAnterior && movimientosPorPeriodo.all { it > 0 }

private fun enEpoch(fecha: LocalDate): Long = fecha.atStartOfDayIn(AppTimeZone.zone).toEpochMilliseconds()

// ── Lo que se le dice al dueño ───────────────────────────────────────────────

/** Hacia dónde fue una categoría contra el período pasado. */
@Serializable
enum class Tendencia { SUBE, BAJA, IGUAL }

/**
 * Una categoría contra el período pasado a la misma altura.
 *
 * @property diferencia siempre positiva (o cero): la [tendencia] ya dice de qué lado.
 */
data class ContraElAnterior(val tendencia: Tendencia, val diferencia: Long)

/**
 * Por debajo de esto, una diferencia se dice «igual»: $800 más en Comida no es una noticia. Elegido,
 * no medido (como el 20 % del semáforo de presupuestos).
 */
const val DIFERENCIA_MINIMA_CONTRA_EL_ANTERIOR: Long = 1_000L

/**
 * Y una diferencia de menos del 5 % de lo del período pasado, también: $1,02M contra $1M es lo mismo
 * gastado. Elegido, no medido.
 */
const val PORCIENTO_MINIMO_CONTRA_EL_ANTERIOR: Long = 5L

/**
 * [gastado] contra [anterior], o `null` si no hay contra qué comparar (el server no lo mandó, o el
 * período pasado Movi no lo cubrió entero). Una categoría sin gasto el período pasado y con gasto
 * ahora SUBE por todo lo gastado.
 */
fun contraElAnterior(gastado: Long, anterior: Long?): ContraElAnterior? {
    if (anterior == null) return null
    val diferencia = gastado - anterior
    val absoluta = if (diferencia < 0) -diferencia else diferencia
    val chica = absoluta < DIFERENCIA_MINIMA_CONTRA_EL_ANTERIOR ||
        absoluta * 100 < anterior * PORCIENTO_MINIMO_CONTRA_EL_ANTERIOR
    return when {
        chica -> ContraElAnterior(Tendencia.IGUAL, absoluta)
        diferencia > 0 -> ContraElAnterior(Tendencia.SUBE, absoluta)
        else -> ContraElAnterior(Tendencia.BAJA, absoluta)
    }
}
