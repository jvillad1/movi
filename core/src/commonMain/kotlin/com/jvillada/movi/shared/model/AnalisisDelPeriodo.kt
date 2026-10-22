package com.jvillada.movi.shared.model

import com.jvillada.movi.shared.time.AppTimeZone
import com.jvillada.movi.shared.time.epochMillisToAppDate
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.daysUntil
import kotlinx.datetime.minus
import kotlinx.datetime.plus

/**
 * # «En qué se va»: el análisis de un período (Ola 4 · Ver la plata)
 *
 * La pantalla que se abre desde «En qué se va» de Hoy: el gasto del período día por día (o semana
 * por semana, si el período es largo), contra el período anterior a la misma altura, y abajo la
 * lista por categoría, comercio o cuenta.
 *
 * **No hay una regla nueva de qué es gasto.** Es la de «Gastos» de siempre —la de las barras de «En
 * qué se va» del Inicio, de Presupuestos y de `monthCashFlow` del server—: en pesos, que cuente como
 * flujo ([isCashFlow]: sin traspasos, pagos de tarjeta, aperturas, ajustes ni lo que pasa en una
 * cuenta de deuda) y que no espere en «Por confirmar». Ver [esGastoDelPeriodo].
 *
 * **«A la misma altura»** es la misma idea que [anteriorAMismaAltura]: en el período en curso se
 * compara contra los mismos días transcurridos del anterior, no contra el anterior entero. En un
 * período ya cerrado, la altura es el período entero. Y si Movi no cubrió entero el período anterior
 * —el primer movimiento del dueño es posterior a su arranque— no hay comparación: un período a medio
 * anotar haría ver todo «por encima» (la misma guarda que `gastoDelAnteriorAMismaAltura` del server).
 *
 * Puro: los movimientos, el período y «hoy» entran por parámetro. Se calcula en el cliente sobre la
 * historia que Movimientos ya leyó (`/api/events/by-day`, sin anulados), sin una lectura nueva.
 */

/**
 * Desde cuántos días un período se dibuja por semanas y no por días. Un período normal (28 a 31
 * días) va por días; uno que el dueño alargó con un inicio propio puede pasar de 35, y 40 barras de
 * 6 dp en un teléfono no se leen.
 */
const val DIAS_MAXIMOS_POR_DIA: Int = 35

/** Cuántos días junta una barra en un período largo. */
private const val DIAS_POR_SEMANA = 7

/**
 * **¿Este movimiento es gasto del período?** La regla de «Gastos»: un egreso en pesos que cuenta
 * como flujo y no espera en «Por confirmar».
 *
 * [tipoDeCuenta] es el de la cuenta del movimiento, si se conoce. Sin él igual se aplican las
 * exclusiones por categoría de [isCashFlow] (traspaso, pago de tarjeta, ajuste…): se le pregunta
 * como si fuera una cuenta de activo, que es donde esas categorías viven.
 */
fun esGastoDelPeriodo(evento: FinancialEvent, tipoDeCuenta: AccountType?): Boolean =
    evento.type == TransactionType.EXPENSE &&
        evento.currency == "COP" &&
        cuentaEnGastosEIngresos(evento) &&
        isCashFlow(tipoDeCuenta ?: AccountType.CHECKING, evento.type, evento.category)

/**
 * Una barra del gráfico: un día (o una semana) del período.
 *
 * @property gasto lo gastado en este tramo.
 * @property acumulado lo gastado desde el primer día del período hasta el último de este tramo.
 * @property anterior lo gastado en el mismo tramo del período anterior (sus mismos días contados
 *   desde su arranque), o `null` si no hay contra qué comparar o el anterior era más corto.
 * @property acumuladoAnterior el acumulado del anterior hasta el mismo día, con la misma regla.
 * @property porVenir el tramo empieza después de hoy: todavía no pasó.
 */
data class TramoDelAnalisis(
    val desde: LocalDate,
    val hasta: LocalDate,
    val gasto: Long,
    val acumulado: Long,
    val anterior: Long?,
    val acumuladoAnterior: Long?,
    val porVenir: Boolean,
)

/**
 * El período analizado.
 *
 * @property total lo gastado en el período (hasta hoy, en el en curso).
 * @property diasTranscurridos los días del período que ya pasaron, contando hoy (la «altura»).
 * @property anteriorAMismaAltura lo gastado en el anterior en esos mismos días, o `null` si Movi no
 *   cubrió entero el período anterior.
 * @property diasComparados cuántos días del anterior entran en [anteriorAMismaAltura]: los
 *   transcurridos, o menos si el anterior era más corto.
 * @property gastos los movimientos que suman en [total], para la lista de abajo.
 * @property gastosDelAnterior los del anterior que suman en [anteriorAMismaAltura] (vacía sin comparación).
 */
data class AnalisisDelPeriodo(
    val periodo: PeriodoFinanciero,
    val desde: LocalDate,
    val hasta: LocalDate,
    val porSemana: Boolean,
    val tramos: List<TramoDelAnalisis>,
    val total: Long,
    val diasTranscurridos: Int,
    val anteriorAMismaAltura: Long?,
    val diasComparados: Int,
    val gastos: List<FinancialEvent>,
    val gastosDelAnterior: List<FinancialEvent>,
) {
    /** ¿Hay contra qué comparar? */
    val conComparacion: Boolean get() = anteriorAMismaAltura != null
}

/**
 * **El análisis de [periodo]** sobre [eventos] (sin anulados, como los manda `/api/events/by-day`).
 * Ver el KDoc del archivo.
 *
 * @param tiposDeCuenta el tipo de cada cuenta, por id, para [esGastoDelPeriodo].
 * @param hoy el día de hoy en la zona de la app.
 */
fun analisisDelPeriodo(
    eventos: List<FinancialEvent>,
    periodo: PeriodoFinanciero,
    settings: PeriodSettings,
    hoy: LocalDate,
    tiposDeCuenta: Map<String, AccountType> = emptyMap(),
): AnalisisDelPeriodo {
    val inicio = inicioDelPeriodo(periodo, settings)
    val siguiente = inicioDelPeriodo(periodoSiguiente(periodo), settings)
    val dias = inicio.daysUntil(siguiente).coerceAtLeast(1)
    val transcurridos = when {
        hoy < inicio -> 0
        hoy >= siguiente -> dias
        else -> inicio.daysUntil(hoy) + 1
    }
    val inicioDelAnterior = inicioDelPeriodo(periodoAnterior(periodo), settings)
    val diasDelAnterior = inicioDelAnterior.daysUntil(inicio).coerceAtLeast(0)

    // La guarda de siempre: el primer movimiento del dueño (cualquiera, no solo un gasto) tiene que
    // ser anterior al arranque del período anterior. Si no, ese período está a medio anotar.
    val primerMovimiento = eventos.minOfOrNull { it.timestamp }
    val cubierto = primerMovimiento != null && primerMovimiento <= enEpoch(inicioDelAnterior)

    val gastosDelPeriodo = mutableListOf<FinancialEvent>()
    val gastosDelAnteriorEnteros = mutableListOf<Pair<Int, FinancialEvent>>()
    val porDia = LongArray(dias)
    val porDiaDelAnterior = LongArray(diasDelAnterior)
    eventos.forEach { e ->
        if (!esGastoDelPeriodo(e, tiposDeCuenta[e.accountId])) return@forEach
        val dia = epochMillisToAppDate(e.timestamp)
        when {
            dia >= inicio && dia < siguiente -> {
                porDia[inicio.daysUntil(dia)] += e.amount
                gastosDelPeriodo += e
            }
            cubierto && dia >= inicioDelAnterior && dia < inicio -> {
                val i = inicioDelAnterior.daysUntil(dia)
                porDiaDelAnterior[i] += e.amount
                gastosDelAnteriorEnteros += i to e
            }
        }
    }

    val porSemana = dias > DIAS_MAXIMOS_POR_DIA
    val largo = if (porSemana) DIAS_POR_SEMANA else 1
    var acumulado = 0L
    var acumuladoAnterior = 0L
    val tramos = (0 until dias step largo).map { primero ->
        val ultimo = minOf(primero + largo, dias) - 1
        val gasto = (primero..ultimo).sumOf { porDia[it] }
        acumulado += gasto
        // El anterior solo cuenta en los días que tuvo: un tramo que empieza después de su último
        // día no tiene contra qué compararse (la línea termina ahí, no se queda plana inventando).
        val anterior = if (cubierto && primero < diasDelAnterior) {
            (primero..minOf(ultimo, diasDelAnterior - 1)).sumOf { porDiaDelAnterior[it] }
        } else {
            null
        }
        if (anterior != null) acumuladoAnterior += anterior
        TramoDelAnalisis(
            desde = inicio.plus(primero, DateTimeUnit.DAY),
            hasta = inicio.plus(ultimo, DateTimeUnit.DAY),
            gasto = gasto,
            acumulado = acumulado,
            anterior = anterior,
            acumuladoAnterior = if (anterior != null) acumuladoAnterior else null,
            porVenir = inicio.plus(primero, DateTimeUnit.DAY) > hoy,
        )
    }

    // La altura: los días transcurridos de este período, en los días del anterior (si era más
    // corto, se toma entero: no hay más días que comparar). Igual que [anteriorAMismaAltura].
    val altura = minOf(transcurridos, diasDelAnterior)
    val delAnterior = gastosDelAnteriorEnteros.filter { (i, _) -> i < altura }.map { it.second }
    return AnalisisDelPeriodo(
        periodo = periodo,
        desde = inicio,
        hasta = siguiente.minus(1, DateTimeUnit.DAY),
        porSemana = porSemana,
        tramos = tramos,
        total = gastosDelPeriodo.sumOf { it.amount },
        diasTranscurridos = transcurridos,
        anteriorAMismaAltura = if (cubierto) delAnterior.sumOf { it.amount } else null,
        diasComparados = altura,
        gastos = gastosDelPeriodo,
        gastosDelAnterior = delAnterior,
    )
}

private fun enEpoch(fecha: LocalDate): Long = fecha.atStartOfDayIn(AppTimeZone.zone).toEpochMilliseconds()

// ── La lista de abajo ────────────────────────────────────────────────────────

/** Cómo se agrupa la lista: el selector Categoría · Comercio · Cuenta. */
enum class VistaDelAnalisis(val rotulo: String) {
    CATEGORIA("Categoría"),
    COMERCIO("Comercio"),
    CUENTA("Cuenta"),
}

/** La clave de un movimiento sin comercio reconocible: todos juntos, en «Otros». */
const val SIN_COMERCIO: String = ""

/**
 * Una fila de la lista.
 *
 * @property clave la categoría, la clave del comercio ([claveDeComercio], o [SIN_COMERCIO]) o el id
 *   de la cuenta: lo que filtra Movimientos al tocarla.
 * @property fraccion sobre el total del período (no sobre la fila mayor), como «En qué se va».
 * @property anterior lo de esa fila en el anterior a la misma altura; `null` sin comparación.
 * @property muestra un movimiento de la fila, para pintar su avatar (la marca o la categoría).
 */
data class FilaDelAnalisis(
    val clave: String,
    val nombre: String,
    val gasto: Long,
    val fraccion: Float,
    val veces: Int,
    val anterior: Long?,
    val contraElAnterior: ContraElAnterior?,
    val muestra: FinancialEvent,
)

/**
 * La clave de [evento] en [vista]. Es la misma que usa el filtro de Movimientos
 * ([FiltroDeMovimientos]), así que una fila y la lista que abre no pueden contar cosas distintas.
 */
fun claveDelAnalisis(evento: FinancialEvent, vista: VistaDelAnalisis): String = when (vista) {
    VistaDelAnalisis.CATEGORIA -> evento.category
    VistaDelAnalisis.COMERCIO -> claveDeComercio(evento) ?: SIN_COMERCIO
    VistaDelAnalisis.CUENTA -> evento.accountId
}

/**
 * **La lista por [vista]**, de mayor a menor gasto, con la flecha contra el anterior a la misma
 * altura cuando hay comparación.
 *
 * @param nombreDeCuenta el nombre de una cuenta por id; si no se conoce, «Cuenta».
 */
fun filasDelAnalisis(
    analisis: AnalisisDelPeriodo,
    vista: VistaDelAnalisis,
    nombreDeCuenta: Map<String, String> = emptyMap(),
): List<FilaDelAnalisis> {
    val total = analisis.total
    val delAnterior = analisis.gastosDelAnterior.groupBy { claveDelAnalisis(it, vista) }
        .mapValues { (_, l) -> l.sumOf { it.amount } }
    return analisis.gastos.groupBy { claveDelAnalisis(it, vista) }
        .map { (clave, movimientos) ->
            val gasto = movimientos.sumOf { it.amount }
            val anterior = if (analisis.conComparacion) delAnterior[clave] ?: 0L else null
            // El más grande representa la fila: su marca es la que más pesa ahí.
            val muestra = movimientos.maxBy { it.amount }
            FilaDelAnalisis(
                clave = clave,
                nombre = nombreDeLaFila(clave, vista, muestra, nombreDeCuenta),
                gasto = gasto,
                fraccion = if (total > 0) gasto.toFloat() / total else 0f,
                veces = movimientos.size,
                anterior = anterior,
                contraElAnterior = contraElAnterior(gasto, anterior),
                muestra = muestra,
            )
        }
        .sortedWith(compareByDescending<FilaDelAnalisis> { it.gasto }.thenBy { it.nombre })
}

private fun nombreDeLaFila(
    clave: String,
    vista: VistaDelAnalisis,
    muestra: FinancialEvent,
    nombreDeCuenta: Map<String, String>,
): String = when (vista) {
    VistaDelAnalisis.CATEGORIA -> clave
    VistaDelAnalisis.CUENTA -> nombreDeCuenta[clave] ?: "Cuenta"
    VistaDelAnalisis.COMERCIO -> if (clave == SIN_COMERCIO) {
        "Otros"
    } else {
        marcaDe(muestra)?.nombre
            ?: muestra.merchant?.takeIf { it.isNotBlank() }
            ?: muestra.description
    }
}

// ── Movimientos, filtrado ────────────────────────────────────────────────────

/**
 * **Lo que «En qué se va» le pide a Movimientos** al tocar una barra o una fila: los movimientos de
 * esa categoría, ese comercio, esa cuenta o esos días. Cada campo `null` no filtra.
 *
 * @property rotulo lo que dice el chip del filtro en Movimientos («Comida», «Rappi», «12 oct»).
 * @property comercio la clave de [claveDelAnalisis] en la vista de comercio.
 * @property desde,hasta días ISO, los dos incluidos.
 */
data class FiltroDeMovimientos(
    val rotulo: String,
    val categoria: String? = null,
    val comercio: String? = null,
    val cuentaId: String? = null,
    val desde: String? = null,
    val hasta: String? = null,
) {
    /** ¿[evento] pasa el filtro? */
    fun deja(evento: FinancialEvent): Boolean {
        if (categoria != null && evento.category != categoria) return false
        if (cuentaId != null && evento.accountId != cuentaId) return false
        if (comercio != null && claveDelAnalisis(evento, VistaDelAnalisis.COMERCIO) != comercio) return false
        if (desde != null || hasta != null) {
            val dia = epochMillisToAppDate(evento.timestamp).toString()
            if (desde != null && dia < desde) return false
            if (hasta != null && dia > hasta) return false
        }
        return true
    }

    companion object {
        /** El filtro de una fila de la lista. */
        fun deLaFila(fila: FilaDelAnalisis, vista: VistaDelAnalisis): FiltroDeMovimientos = when (vista) {
            VistaDelAnalisis.CATEGORIA -> FiltroDeMovimientos(fila.nombre, categoria = fila.clave)
            VistaDelAnalisis.COMERCIO -> FiltroDeMovimientos(fila.nombre, comercio = fila.clave)
            VistaDelAnalisis.CUENTA -> FiltroDeMovimientos(fila.nombre, cuentaId = fila.clave)
        }

        /** El filtro de una barra: sus días. */
        fun delTramo(tramo: TramoDelAnalisis, rotulo: String): FiltroDeMovimientos =
            FiltroDeMovimientos(rotulo, desde = tramo.desde.toString(), hasta = tramo.hasta.toString())
    }
}
