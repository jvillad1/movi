package com.jvillada.movi.shared.model

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.datetime.plus
import kotlinx.serialization.Serializable

/**
 * # La caja proyectada día a día (Ola 4 · «Movi mira adelante»)
 *
 * La pregunta: *¿cuánta plata voy a tener cada día hasta el cierre del período, y cuál es el día
 * más justo?* Hasta acá Movi decía bien cómo iba el período, pero no lo que venía.
 *
 * ## De dónde sale cada peso (y nada más)
 *
 * - **El punto de partida es «Tu plata» de hoy**, la de [patrimonioDe] — nunca una suma a mano.
 * - **Lo que entra y sale con fecha son los pagos del período de Plan**, la lista de
 *   [checklistDelPeriodo], **con su estado de pagado tal cual**: lo pagado ya está adentro de Tu
 *   plata y no se vuelve a restar; lo pendiente sale el día que vence (o hoy, si ya venció). Los
 *   ingresos que faltan entran el día que se esperan.
 * - **Una tarjeta paga su mínimo**, con la misma regla que el «Flujo libre» de Plan: su monto es el
 *   SALDO ([RecurringRule.montoEsSaldo]) y lo que hay que pagar es [PagoDelPeriodo.pagoMinimo]. Sin
 *   el mínimo cargado no se inventa un porcentaje: la tarjeta queda en [CajaProyectada.sinContar]
 *   y la pantalla dice que falta.
 * - **El gasto del día a día es un supuesto, y se dice**: el promedio diario de gasto variable de
 *   los últimos períodos cerrados (ver [gastoDelDiaADia]), restado desde mañana hasta el cierre.
 *   Sin períodos con qué estimarlo no se resta nada y [CajaProyectada.gastoDiario] viene en `null`.
 *
 * Lo que NO está y por qué: los pagos en otra moneda (no hay una cifra en pesos honesta), las
 * cuotas que paga la nómina o un tercero (no salen de tu plata: ya no están en la lista), y las
 * compras con tarjeta de este período (se pagan en el próximo; su mínimo de ESTE período sí está).
 *
 * Puro: el «hoy», el cierre y las cifras entran por parámetro.
 */

/** Un movimiento con fecha dentro de la proyección. [monto] con signo: positivo entra, negativo sale. */
@Serializable
data class MovimientoDeLaCaja(
    val nombre: String,
    val monto: Long,
)

/** Un día de la proyección: cómo queda Tu plata al cerrar ese día, y lo que pasó en él. */
@Serializable
data class DiaDeLaCaja(
    /** ISO, `"2026-10-17"`. */
    val fecha: String,
    /** Tu plata al cerrar el día, con lo que vence ese día y el gasto estimado del día ya restados. */
    val saldo: Long,
    /** Los pagos e ingresos de ese día (sin el gasto del día a día, que es igual todos los días). */
    val movimientos: List<MovimientoDeLaCaja> = emptyList(),
) {
    /** El pago más grande del día, o `null` si ese día no sale ningún pago. */
    val pagoMasGrande: MovimientoDeLaCaja? get() = movimientos.filter { it.monto < 0 }.minByOrNull { it.monto }
}

/**
 * Toda la proyección.
 *
 * @property sinContar lo que estaba en la lista y NO entró a la cuenta, con el porqué en palabras
 *   («Master Black: falta el pago mínimo»). Vacía cuando entró todo.
 */
@Serializable
data class CajaProyectada(
    val tuPlataHoy: Long,
    /** El gasto variable que se resta cada día desde mañana; `null` = no hay con qué estimarlo. */
    val gastoDiario: Long?,
    val dias: List<DiaDeLaCaja>,
    val sinContar: List<String> = emptyList(),
) {
    /** El día en que Tu plata llega más abajo; ante un empate, el primero. */
    val diaMasBajo: DiaDeLaCaja get() = dias.minWith(compareBy<DiaDeLaCaja> { it.saldo }.thenBy { it.fecha })

    /** El primer día que queda en negativo, o `null` si ninguno. */
    val primerDiaEnRojo: DiaDeLaCaja? get() = dias.firstOrNull { it.saldo < 0L }

    /** Cómo cierra el período. */
    val alCierre: DiaDeLaCaja get() = dias.last()
}

/** Por qué una fila de la lista no entra a la cuenta, en palabras. `null` = entra. */
private fun porQueNoEntra(pago: PagoDelPeriodo): String? = when {
    pago.pagado -> null
    pago.montoEsSaldo && pago.pagoMinimo == null -> "${pago.nombre}: falta el pago mínimo"
    pago.montoEsSaldo -> null // el mínimo ya está en pesos (ver RecurringRule.pagoMinimoCop)
    pago.moneda != "COP" -> "${pago.nombre}: está en ${pago.moneda}"
    else -> null
}

/** Lo que mueve esta fila, con signo, o `null` si no mueve nada (pagada, o no entra). */
private fun montoDeLaFila(pago: PagoDelPeriodo): Long? {
    if (pago.pagado || porQueNoEntra(pago) != null) return null
    val monto = if (pago.montoEsSaldo) pago.pagoMinimo ?: return null else pago.monto
    if (monto <= 0L) return null
    return if (pago.esIngreso) monto else -monto
}

/**
 * La caja proyectada de [hoy] a [ultimoDia] (los dos incluidos), o `null` si no hay días que
 * proyectar o las fechas no se entienden.
 *
 * @param tuPlataHoy «Tu plata» de [patrimonioDe], ahora.
 * @param pagos la lista «Pagos del período» de Plan, tal cual ([checklistDelPeriodo]).
 * @param gastoDiario el gasto variable diario estimado, o `null` si no se pudo estimar.
 * @param hoy ISO, la fecha de hoy en la zona de la app.
 * @param ultimoDia ISO, el último día del período (el anterior al arranque del siguiente).
 */
fun cajaProyectada(
    tuPlataHoy: Long,
    pagos: List<PagoDelPeriodo>,
    gastoDiario: Long?,
    hoy: String,
    ultimoDia: String,
): CajaProyectada? {
    val desde = runCatching { LocalDate.parse(hoy) }.getOrNull() ?: return null
    val hasta = runCatching { LocalDate.parse(ultimoDia) }.getOrNull() ?: return null
    if (hasta < desde) return null

    // Cada fila que mueve plata, en el día en que la mueve: lo vencido y sin pagar sale HOY (se
    // sigue debiendo), lo demás el día de su vencimiento. Una fila sin fecha legible también hoy:
    // es el lado prudente.
    val porDia = mutableMapOf<LocalDate, MutableList<MovimientoDeLaCaja>>()
    pagos.forEach { pago ->
        val monto = montoDeLaFila(pago) ?: return@forEach
        val vence = runCatching { LocalDate.parse(pago.vence) }.getOrNull() ?: desde
        val dia = if (vence < desde) desde else vence
        // Una fila que vence pasado el cierre no es de este período (la lista no debería traerla).
        if (dia > hasta) return@forEach
        porDia.getOrPut(dia) { mutableListOf() } += MovimientoDeLaCaja(pago.nombre, monto)
    }

    val gasto = gastoDiario?.coerceAtLeast(0L)
    var saldo = tuPlataHoy
    val dias = (0..desde.daysUntil(hasta)).map { i ->
        val dia = desde.plus(i, DateTimeUnit.DAY)
        val movimientos = porDia[dia].orEmpty().sortedBy { it.monto }
        saldo += movimientos.sumOf { it.monto }
        // El gasto del día a día desde MAÑANA: lo de hoy ya está, en parte, adentro de Tu plata, y
        // restarle un día entero encima lo contaría dos veces.
        if (i > 0 && gasto != null) saldo -= gasto
        DiaDeLaCaja(fecha = dia.toString(), saldo = saldo, movimientos = movimientos)
    }
    return CajaProyectada(
        tuPlataHoy = tuPlataHoy,
        gastoDiario = gasto,
        dias = dias,
        sinContar = pagos.mapNotNull { porQueNoEntra(it) },
    )
}

// ── El gasto del día a día ───────────────────────────────────────────────────

/**
 * El gasto variable de un período cerrado (ver [gastoVariablePorDia]): cuánto fue y en cuántos días.
 *
 * @property periodo el prefijo del período, `"2026-09"` (ver [PeriodoFinanciero.prefijo]).
 */
@Serializable
data class GastoVariableDeUnPeriodo(
    val periodo: String,
    val total: Long,
    val dias: Int,
)

/**
 * **El supuesto del gasto del día a día**, con lo que lo respalda.
 *
 * @property porDia el promedio diario, o `null` si no hay ni un período cerrado con qué estimarlo.
 * @property periodos los períodos con los que se calculó, del más reciente al más viejo.
 */
@Serializable
data class GastoDelDiaADia(
    val porDia: Long? = null,
    val periodos: List<GastoVariableDeUnPeriodo> = emptyList(),
)

/** Cuántos períodos cerrados se promedian como mucho: los últimos tres. */
const val PERIODOS_PARA_EL_GASTO_DIARIO = 3

/**
 * El promedio diario del gasto variable de los últimos períodos cerrados.
 *
 * **Total sobre días, no promedio de promedios**: un período de 31 días pesa más que uno de 28,
 * que es lo que pasó en la vida real. Solo cuentan los que [cerrados] trae —el server deja afuera
 * un período en el que Movi todavía no llevaba las cuentas del dueño, porque su gasto saldría
 * bajísimo sin serlo—, como mucho [PERIODOS_PARA_EL_GASTO_DIARIO], los más recientes primero.
 *
 * @param cerrados del más reciente al más viejo.
 */
fun gastoDelDiaADia(cerrados: List<GastoVariableDeUnPeriodo>): GastoDelDiaADia {
    val usados = cerrados.filter { it.dias > 0 }.take(PERIODOS_PARA_EL_GASTO_DIARIO)
    val dias = usados.sumOf { it.dias }
    if (dias <= 0) return GastoDelDiaADia()
    return GastoDelDiaADia(porDia = usados.sumOf { it.total.coerceAtLeast(0L) } / dias, periodos = usados)
}
