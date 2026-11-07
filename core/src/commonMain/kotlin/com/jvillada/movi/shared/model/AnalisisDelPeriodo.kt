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
 *
 * Vuelta 50: es [comoSePresenta] —la misma función con la que la fila decide «−», «+» o nada— más la
 * moneda y «Por confirmar». Así la fila de la cuota dice «−$980.000» y el «Gastaste» del día la suma,
 * con una sola regla.
 */
fun esGastoDelPeriodo(evento: FinancialEvent, tipoDeCuenta: AccountType?): Boolean =
    comoSePresenta(evento, tipoDeCuenta) == PresentacionDelMovimiento.GASTO &&
        evento.currency == "COP" &&
        !esperaEnPorConfirmar(evento.reconciliationStatus)

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
 * El período analizado (o el rango de períodos, vuelta 140: «3 meses», «12 meses»).
 *
 * @property periodo el período analizado; en un rango, el último (el más reciente).
 * @property total lo gastado en el período (hasta hoy, en el en curso).
 * @property diasTranscurridos los días del período que ya pasaron, contando hoy (la «altura»).
 * @property anteriorAMismaAltura lo gastado en el anterior en esos mismos días, o `null` si Movi no
 *   cubrió entero el período anterior.
 * @property diasComparados cuántos días del anterior entran en [anteriorAMismaAltura]: los
 *   transcurridos, o menos si el anterior era más corto.
 * @property gastos los movimientos que suman en [total], para la lista de abajo.
 * @property gastosDelAnterior los del anterior que suman en [anteriorAMismaAltura] (vacía sin comparación).
 * @property cuantosPeriodos cuántos períodos junta el análisis: 1, o los de un rango.
 * @property porMes las barras van de a un mes (un período, o dos quincenas): un rango de más de
 *   [DIAS_MAXIMOS_POR_SEMANA] días.
 * @property desdeDelAnterior,hastaDelAnterior el período (o el rango) con el que se compara, entero.
 * @property anteriorConHueco no hay comparación porque el anterior (o uno de los períodos del rango
 *   anterior) no tiene ni un movimiento: un hueco en la historia, no un período sin gastos.
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
    val cuantosPeriodos: Int = 1,
    val porMes: Boolean = false,
    val desdeDelAnterior: LocalDate? = null,
    val hastaDelAnterior: LocalDate? = null,
    val anteriorConHueco: Boolean = false,
) {
    /** ¿Hay contra qué comparar? */
    val conComparacion: Boolean get() = anteriorAMismaAltura != null

    /** ¿Es un rango de varios períodos («3 meses», «12 meses») y no un período solo? */
    val esRango: Boolean get() = cuantosPeriodos > 1
}

/**
 * Desde cuántos días un rango se dibuja por meses y no por semanas (vuelta 140). «3 meses» (unos 90
 * días) va por semanas, 13 barras; «12 meses» (unos 365) va por meses, 12. Elegido, no medido: 120
 * días son unas 17 semanas, lo último que todavía se lee como barras en un teléfono.
 */
const val DIAS_MAXIMOS_POR_SEMANA: Int = 120

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
): AnalisisDelPeriodo =
    // Vuelta 105 · «el anterior» es el homólogo (D6): en la quincena, la misma quincena del mes pasado.
    // En el mensual es el inmediato y termina donde arranca este: lo de siempre.
    analisisEntre(eventos, listOf(periodo), listOf(periodoHomologo(periodo, settings)), settings, hoy, tiposDeCuenta, periodosPorTramo = 1)

/**
 * **El motor de [analisisDelPeriodo] y de `analisisDelRango`** (vuelta 140): el gasto de [periodos]
 * (seguidos, el más viejo primero) contra el de [anteriores] (los mismos tantos, también seguidos), a
 * la misma altura. Con un solo período es exactamente lo de siempre. [periodosPorTramo] es cuántos
 * períodos junta una barra cuando el rango va por meses (2 en la quincena).
 */
internal fun analisisEntre(
    eventos: List<FinancialEvent>,
    periodos: List<PeriodoFinanciero>,
    anteriores: List<PeriodoFinanciero>,
    settings: PeriodSettings,
    hoy: LocalDate,
    tiposDeCuenta: Map<String, AccountType>,
    periodosPorTramo: Int,
): AnalisisDelPeriodo {
    val inicio = inicioDelPeriodo(periodos.first(), settings)
    val siguiente = inicioDelPeriodo(periodoSiguiente(periodos.last(), settings), settings)
    val dias = inicio.daysUntil(siguiente).coerceAtLeast(1)
    val transcurridos = when {
        hoy < inicio -> 0
        hoy >= siguiente -> dias
        else -> inicio.daysUntil(hoy) + 1
    }
    val inicioDelAnterior = inicioDelPeriodo(anteriores.first(), settings)
    val finDelAnterior = inicioDelPeriodo(periodoSiguiente(anteriores.last(), settings), settings)
    val diasDelAnterior = inicioDelAnterior.daysUntil(finDelAnterior).coerceAtLeast(0)

    // La guarda de siempre: el primer movimiento del dueño (cualquiera, no solo un gasto) tiene que
    // ser anterior al arranque del período anterior. Si no, ese período está a medio anotar. En un
    // rango, el arranque del rango anterior entero: un rango anterior a medio anotar no compara.
    // Vuelta 140: y ninguno de los períodos de antes puede ser un hueco (sin un solo movimiento):
    // [sePuedeCompararContraElAnterior], la misma regla del «vs. período pasado» del server.
    val primerMovimiento = eventos.minOfOrNull { it.timestamp }
    val vistoEntero = primerMovimiento != null && primerMovimiento <= enEpoch(inicioDelAnterior)
    val movimientosPorPeriodo = anteriores.map { a ->
        val desde = enEpoch(inicioDelPeriodo(a, settings))
        val hasta = enEpoch(inicioDelPeriodo(periodoSiguiente(a, settings), settings))
        eventos.count { it.timestamp in desde until hasta }
    }
    val cubierto = sePuedeCompararContraElAnterior(primerMovimiento, enEpoch(inicioDelAnterior), movimientosPorPeriodo)

    val gastosDelPeriodo = mutableListOf<FinancialEvent>()
    val gastosDelAnteriorEnteros = mutableListOf<Pair<Int, FinancialEvent>>()
    val porDia = LongArray(dias)
    val porDiaDelAnterior = LongArray(diasDelAnterior)
    eventos.forEach { e ->
        if (!esGastoDelPeriodo(e, tiposDeCuenta[e.accountId])) return@forEach
        val dia = epochMillisToAppDate(e.timestamp)
        when {
            dia >= inicio && dia < siguiente -> {
                porDia[inicio.daysUntil(dia)] += montoContado(e)
                gastosDelPeriodo += e
            }
            cubierto && dia >= inicioDelAnterior && dia < finDelAnterior -> {
                val i = inicioDelAnterior.daysUntil(dia)
                porDiaDelAnterior[i] += montoContado(e)
                gastosDelAnteriorEnteros += i to e
            }
        }
    }

    val porMes = periodos.size > 1 && dias > DIAS_MAXIMOS_POR_SEMANA
    val porSemana = !porMes && dias > DIAS_MAXIMOS_POR_DIA
    val tramos = if (porMes) {
        tramosPorMes(periodos, anteriores, settings, inicio, inicioDelAnterior, porDia, porDiaDelAnterior, cubierto, periodosPorTramo, hoy)
    } else {
        tramosPorDias(dias, if (porSemana) DIAS_POR_SEMANA else 1, inicio, porDia, porDiaDelAnterior, diasDelAnterior, cubierto, hoy)
    }

    // La altura: los días transcurridos de este período, en los días del anterior (si era más
    // corto, se toma entero: no hay más días que comparar). Igual que [anteriorAMismaAltura].
    val altura = minOf(transcurridos, diasDelAnterior)
    val delAnterior = gastosDelAnteriorEnteros.filter { (i, _) -> i < altura }.map { it.second }
    return AnalisisDelPeriodo(
        periodo = periodos.last(),
        desde = inicio,
        hasta = siguiente.minus(1, DateTimeUnit.DAY),
        porSemana = porSemana,
        tramos = tramos,
        total = gastosDelPeriodo.sumOf { montoContado(it) },
        diasTranscurridos = transcurridos,
        anteriorAMismaAltura = if (cubierto) delAnterior.sumOf { montoContado(it) } else null,
        diasComparados = altura,
        gastos = gastosDelPeriodo,
        gastosDelAnterior = delAnterior,
        cuantosPeriodos = periodos.size,
        porMes = porMes,
        desdeDelAnterior = inicioDelAnterior,
        hastaDelAnterior = finDelAnterior.minus(1, DateTimeUnit.DAY),
        anteriorConHueco = vistoEntero && !cubierto,
    )
}

/** Las barras de a [largo] días (1, o una semana), con el anterior por los mismos días contados desde su arranque. */
private fun tramosPorDias(
    dias: Int,
    largo: Int,
    inicio: LocalDate,
    porDia: LongArray,
    porDiaDelAnterior: LongArray,
    diasDelAnterior: Int,
    cubierto: Boolean,
    hoy: LocalDate,
): List<TramoDelAnalisis> {
    var acumulado = 0L
    var acumuladoAnterior = 0L
    return (0 until dias step largo).map { primero ->
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
}

/**
 * Las barras de a un mes (vuelta 140): [periodosPorTramo] períodos por barra, y el anterior es el
 * mismo grupo de [anteriores] (el i-ésimo mes contra el i-ésimo), no los mismos días contados: los
 * meses no tienen todos el mismo largo.
 */
private fun tramosPorMes(
    periodos: List<PeriodoFinanciero>,
    anteriores: List<PeriodoFinanciero>,
    settings: PeriodSettings,
    inicio: LocalDate,
    inicioDelAnterior: LocalDate,
    porDia: LongArray,
    porDiaDelAnterior: LongArray,
    cubierto: Boolean,
    periodosPorTramo: Int,
    hoy: LocalDate,
): List<TramoDelAnalisis> {
    fun bordes(grupo: List<PeriodoFinanciero>): Pair<LocalDate, LocalDate> =
        inicioDelPeriodo(grupo.first(), settings) to inicioDelPeriodo(periodoSiguiente(grupo.last(), settings), settings)
    val delAnterior = anteriores.chunked(periodosPorTramo.coerceAtLeast(1))
    var acumulado = 0L
    var acumuladoAnterior = 0L
    return periodos.chunked(periodosPorTramo.coerceAtLeast(1)).mapIndexed { j, grupo ->
        val (desde, fin) = bordes(grupo)
        val gasto = (inicio.daysUntil(desde) until inicio.daysUntil(fin)).sumOf { porDia[it] }
        acumulado += gasto
        val anterior = delAnterior.getOrNull(j)?.takeIf { cubierto }?.let { g ->
            val (d, f) = bordes(g)
            // El mes en curso contra los mismos días del suyo de antes, como la cifra de arriba: si no,
            // el 6 del mes la barra se compararía con el mes de antes entero.
            val dias = if (hoy >= desde && hoy < fin) minOf(desde.daysUntil(hoy) + 1, d.daysUntil(f)) else d.daysUntil(f)
            val primero = inicioDelAnterior.daysUntil(d)
            (primero until primero + dias).sumOf { porDiaDelAnterior[it] }
        }
        if (anterior != null) acumuladoAnterior += anterior
        TramoDelAnalisis(
            desde = desde,
            hasta = fin.minus(1, DateTimeUnit.DAY),
            gasto = gasto,
            acumulado = acumulado,
            anterior = anterior,
            acumuladoAnterior = if (anterior != null) acumuladoAnterior else null,
            porVenir = desde > hoy,
        )
    }
}

private fun enEpoch(fecha: LocalDate): Long = fecha.atStartOfDayIn(AppTimeZone.zone).toEpochMilliseconds()

/**
 * **Los movimientos que analiza «En qué se va»**: la historia de Movimientos (`/api/events/by-day`,
 * sin anulados) más **tu parte de lo que pagaron otros** en un espacio (R3, `getTuParteDeOtros`,
 * ver [tuParteComoEventos]): es gasto tuyo el día del gasto, como en «Salió» y en el widget del
 * Inicio, aunque no esté en tus cuentas. Una sola función para «En qué se va» y el panel de
 * Movimientos (vuelta de diseño 9), así las dos pantallas no pueden contar distinto.
 */
fun movimientosDelAnalisis(dias: List<EventDay>, tuParteDeOtros: List<FinancialEvent>): List<FinancialEvent> =
    // Vuelta 66: Actividad ya trae tu parte de otros como filas de sus días ([conTuParteDeOtros]);
    // si además la pasa aparte, no cuenta dos veces.
    (dias.flatMap { it.items } + tuParteDeOtros).distinctBy { it.id }

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
        .mapValues { (_, l) -> l.sumOf { montoContado(it) } }
    // Vuelta 97: un gasto que no te toca nada («Solo de Caro», `montoContado` en 0) no es una vez tuya: no
    // suma veces ni arma una fila de $0.
    return analisis.gastos.filter { montoContado(it) > 0L }.groupBy { claveDelAnalisis(it, vista) }
        .map { (clave, movimientos) ->
            val gasto = movimientos.sumOf { montoContado(it) }
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

    /**
     * **¿Este filtro pide días fuera de [periodo]?** (vuelta 140) Una fila o una barra de un rango
     * («3 meses») trae sus bordes, que pasan del período que Movimientos muestra: entonces la lista no
     * se recorta al período (como la búsqueda, que también atraviesa los períodos) y muestra todo lo
     * que el filtro deja pasar. Una barra de un período solo cae adentro: lo de siempre.
     */
    fun cruzaElPeriodo(periodo: PeriodoFinanciero, settings: PeriodSettings): Boolean {
        if (desde == null && hasta == null) return false
        val inicio = inicioDelPeriodo(periodo, settings).toString()
        val fin = inicioDelPeriodo(periodoSiguiente(periodo, settings), settings).minus(1, DateTimeUnit.DAY).toString()
        return (desde != null && desde < inicio) || (hasta != null && hasta > fin)
    }

    companion object {
        /** El filtro de una fila de la lista. */
        fun deLaFila(fila: FilaDelAnalisis, vista: VistaDelAnalisis): FiltroDeMovimientos = when (vista) {
            VistaDelAnalisis.CATEGORIA -> FiltroDeMovimientos(fila.nombre, categoria = fila.clave)
            VistaDelAnalisis.COMERCIO -> FiltroDeMovimientos(fila.nombre, comercio = fila.clave)
            VistaDelAnalisis.CUENTA -> FiltroDeMovimientos(fila.nombre, cuentaId = fila.clave)
        }

        /**
         * El filtro de una fila de un rango (vuelta 140): lo de [deLaFila] más los días del rango, y
         * el rango en el rótulo («Comida · 28 ago – 29 nov»), así el chip de Movimientos dice qué se ve.
         */
        fun deLaFilaEnElRango(fila: FilaDelAnalisis, vista: VistaDelAnalisis, analisis: AnalisisDelPeriodo): FiltroDeMovimientos =
            deLaFila(fila, vista).copy(
                rotulo = "${fila.nombre} · ${nombreDelRango(analisis.desde, analisis.hasta)}",
                desde = analisis.desde.toString(),
                hasta = analisis.hasta.toString(),
            )

        /** El filtro de una barra: sus días. */
        fun delTramo(tramo: TramoDelAnalisis, rotulo: String): FiltroDeMovimientos =
            FiltroDeMovimientos(rotulo, desde = tramo.desde.toString(), hasta = tramo.hasta.toString())
    }
}
