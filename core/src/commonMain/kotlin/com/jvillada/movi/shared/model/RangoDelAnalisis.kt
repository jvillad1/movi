package com.jvillada.movi.shared.model

import com.jvillada.movi.shared.time.epochMillisToAppDate
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus

/**
 * # «En qué se va»: más que el período en curso (vuelta 140, issue #696)
 *
 * Caro abrió «En qué se va» con 6 gastos del período (llegados por SMS) y vio 4 categorías, cuando
 * tenía ~560 movimientos de julio a septiembre que entraron con sus extractos. La pantalla funcionaba;
 * lo que faltaba era poder mirar más tiempo y que la pantalla dijera que hay más.
 *
 * **Sin reglas de plata nuevas**: un rango es la suma de sus períodos con la regla de «Gastos» de
 * siempre ([esGastoDelPeriodo] + [montoContado]); el motor es el mismo de [analisisDelPeriodo]
 * ([analisisEntre]). Todo es puro y se calcula en el cliente sobre la historia que `/api/events/by-day`
 * ya manda entera (y en el teléfono, la base local): no hay lectura ni parámetro nuevos.
 */

/**
 * **Cuánto tiempo mira «En qué se va»**: el selector «Este período · 3 meses · 12 meses».
 *
 * Son **períodos del dueño**, no meses de calendario: el rótulo dice «meses» porque es lo que la gente
 * entiende, pero los bordes salen de [inicioDelPeriodo]. En la quincena, un mes son dos quincenas
 * ([cuantosPeriodosDelRango]): «3 meses» son 6 quincenas, no 3 (que serían mes y medio).
 */
enum class RangoDelAnalisis(val rotulo: String, val meses: Int) {
    PERIODO("Este período", 1),
    TRES_MESES("3 meses", 3),
    DOCE_MESES("12 meses", 12),
}

/** Cuántos períodos hacen un mes con estos ajustes: 1, o 2 quincenas. */
fun periodosPorMes(settings: PeriodSettings): Int = if (settings.esQuincenal) 2 else 1

/** Cuántos períodos junta [rango] con estos ajustes. */
fun cuantosPeriodosDelRango(rango: RangoDelAnalisis, settings: PeriodSettings): Int =
    rango.meses * periodosPorMes(settings)

/** Los períodos de [rango] que terminan en [ultimo] (incluido), el más viejo primero. */
fun periodosDelRango(ultimo: PeriodoFinanciero, rango: RangoDelAnalisis, settings: PeriodSettings): List<PeriodoFinanciero> =
    periodosAntesDe(ultimo, cuantosPeriodosDelRango(rango, settings) - 1, settings) + ultimo

/** Los [cuantos] períodos inmediatamente anteriores a [periodo], el más viejo primero. */
private fun periodosAntesDe(periodo: PeriodoFinanciero, cuantos: Int, settings: PeriodSettings): List<PeriodoFinanciero> {
    val antes = ArrayList<PeriodoFinanciero>(cuantos)
    var p = periodo
    repeat(cuantos) {
        p = periodoAnterior(p, settings)
        antes += p
    }
    return antes.reversed()
}

/**
 * **El análisis de [rango]** que termina en [ultimo]: con [RangoDelAnalisis.PERIODO], exactamente
 * [analisisDelPeriodo]. Con un rango, el gasto de todos sus períodos (la lista suma el rango entero),
 * por semana o por mes ([DIAS_MAXIMOS_POR_SEMANA]), contra **el rango anterior del mismo largo** a la
 * misma altura —y solo si Movi lo cubrió entero, la guarda de siempre—.
 */
fun analisisDelRango(
    eventos: List<FinancialEvent>,
    ultimo: PeriodoFinanciero,
    rango: RangoDelAnalisis,
    settings: PeriodSettings,
    hoy: LocalDate,
    tiposDeCuenta: Map<String, AccountType> = emptyMap(),
): AnalisisDelPeriodo {
    if (rango == RangoDelAnalisis.PERIODO) return analisisDelPeriodo(eventos, ultimo, settings, hoy, tiposDeCuenta)
    val periodos = periodosDelRango(ultimo, rango, settings)
    val anteriores = periodosAntesDe(periodos.first(), periodos.size, settings)
    return analisisEntre(eventos, periodos, anteriores, settings, hoy, tiposDeCuenta, periodosPorTramo = periodosPorMes(settings))
}

/**
 * «28 ago – 29 nov»: el nombre de un rango, con el día de arranque de su primer período y el último
 * día del último. Si el rango cruza de año, el año va en el primer borde («28 nov 2025 – 29 nov»).
 */
fun nombreDelRango(desde: LocalDate, hasta: LocalDate): String {
    if (desde.year == hasta.year) return rangoCortoEntre(desde, hasta)
    return "${desde.dayOfMonth} ${MESES_CORTOS_DEL_RANGO[desde.monthNumber - 1]} ${desde.year} – " +
        "${hasta.dayOfMonth} ${MESES_CORTOS_DEL_RANGO[hasta.monthNumber - 1]}"
}

/**
 * **El pie de las flechas en un rango**: contra qué se compara, o por qué no hay flechas. En un
 * período solo, el pie de siempre (lo arma la pantalla).
 */
fun pieDeLasFlechasDelRango(analisis: AnalisisDelPeriodo, rango: RangoDelAnalisis): String {
    val desde = analisis.desdeDelAnterior
    val hasta = analisis.hastaDelAnterior
    val cuales = if (desde != null && hasta != null) " (${nombreDelRango(desde, hasta)})" else ""
    return when {
        analisis.conComparacion -> "Las flechas comparan con los ${rango.rotulo} anteriores$cuales, a la misma altura."
        analisis.anteriorConHueco -> "Sin flechas: en los ${rango.rotulo} anteriores$cuales hay un período sin movimientos."
        else -> "Sin flechas: Movi no tiene completos los ${rango.rotulo} anteriores$cuales para comparar."
    }
}

/**
 * Vuelta 140 · el pie de un período solo **cuando no hay flechas porque el anterior es un hueco**:
 * «Sin flechas: el período pasado (30 sep – 29 oct) no tiene movimientos.» `null` en otro caso (con
 * flechas, el pie de siempre; sin historia, no hace falta explicar).
 */
fun pieSinFlechasDelPeriodo(analisis: AnalisisDelPeriodo): String? {
    if (analisis.esRango || !analisis.anteriorConHueco) return null
    val desde = analisis.desdeDelAnterior ?: return null
    val hasta = analisis.hastaDelAnterior ?: return null
    return "Sin flechas: el período pasado (${nombreDelRango(desde, hasta)}) no tiene movimientos."
}

// ── «Este período va empezando» ─────────────────────────────────────────────

/**
 * Debajo de cuántos gastos un período «va empezando». Elegido, no medido: con 6 gastos (el caso de
 * Caro) la lista no dice en qué se va la plata de nadie.
 */
const val GASTOS_DE_UN_PERIODO_QUE_YA_DICE_ALGO: Int = 10

/** Debajo de cuántas categorías un período «va empezando». Elegido, no medido. */
const val CATEGORIAS_DE_UN_PERIODO_QUE_YA_DICE_ALGO: Int = 5

/**
 * Hasta cuántos días del período (contando hoy) puede «ir empezando» (revisión de la vuelta 140). Pasado
 * eso, un período con pocas categorías no va empezando: es así. Elegido, no medido.
 */
const val DIAS_DE_UN_PERIODO_QUE_VA_EMPEZANDO: Int = 10

/**
 * La línea «Este período va empezando…» con su acción.
 *
 * @property gastos cuántos gastos lleva el período.
 * @property dias cuántos días lleva el período, contando hoy.
 * @property historiaDesde el día del primer gasto anterior al período.
 * @property rango el rango que la acción elige: el más corto que trae algo de esa historia.
 */
data class PeriodoQueVaEmpezando(val gastos: Int, val historiaDesde: LocalDate, val rango: RangoDelAnalisis, val dias: Int = 0)

/**
 * **¿El período en curso va empezando y hay historia antes?** Lleva hasta
 * [DIAS_DE_UN_PERIODO_QUE_VA_EMPEZANDO] días **y** tiene poco: menos de
 * [GASTOS_DE_UN_PERIODO_QUE_YA_DICE_ALGO] gastos o menos de [CATEGORIAS_DE_UN_PERIODO_QUE_YA_DICE_ALGO]
 * categorías. Solo en el período en curso mirado solo ([analisis] de un período que contiene [hoy]) y
 * solo si un rango de «En qué se va» trae algo de esa historia: «3 meses» si alcanza, si no «12
 * meses»; si la historia es más vieja que eso, nada (la acción no mostraría nada nuevo).
 */
fun periodoQueVaEmpezando(
    analisis: AnalisisDelPeriodo,
    eventos: List<FinancialEvent>,
    settings: PeriodSettings,
    hoy: LocalDate,
    tiposDeCuenta: Map<String, AccountType> = emptyMap(),
): PeriodoQueVaEmpezando? {
    if (analisis.esRango || hoy < analisis.desde || hoy > analisis.hasta) return null
    if (analisis.diasTranscurridos > DIAS_DE_UN_PERIODO_QUE_VA_EMPEZANDO) return null
    val propios = analisis.gastos.filter { montoContado(it) > 0L }
    val categorias = propios.map { it.category }.distinct().size
    if (propios.size >= GASTOS_DE_UN_PERIODO_QUE_YA_DICE_ALGO && categorias >= CATEGORIAS_DE_UN_PERIODO_QUE_YA_DICE_ALGO) return null
    val antes = eventos
        .filter { esGastoDelPeriodo(it, tiposDeCuenta[it.accountId]) && montoContado(it) > 0L }
        .map { epochMillisToAppDate(it.timestamp) }
        .filter { it < analisis.desde }
    val historiaDesde = antes.minOrNull() ?: return null
    val ultimoAntes = antes.max()
    val rango = listOf(RangoDelAnalisis.TRES_MESES, RangoDelAnalisis.DOCE_MESES).firstOrNull { r ->
        ultimoAntes >= inicioDelPeriodo(periodosDelRango(analisis.periodo, r, settings).first(), settings)
    } ?: return null
    return PeriodoQueVaEmpezando(propios.size, historiaDesde, rango, analisis.diasTranscurridos)
}

/**
 * «Este período va empezando: llevas 6 movimientos. Tienes historia desde julio.» El año del mes va
 * solo si no es el de [anioDeHoy]. Con [GASTOS_DE_UN_PERIODO_QUE_YA_DICE_ALGO] o más gastos (va
 * empezando por las pocas categorías) dice los días, nunca «llevas 50 movimientos».
 */
fun textoDelPeriodoQueVaEmpezando(v: PeriodoQueVaEmpezando, anioDeHoy: Int): String {
    val llevas = when {
        v.gastos == 0 -> "todavía no tienes gastos"
        v.gastos == 1 -> "llevas 1 movimiento"
        v.gastos < GASTOS_DE_UN_PERIODO_QUE_YA_DICE_ALGO -> "llevas ${v.gastos} movimientos"
        v.dias == 1 -> "llevas 1 día"
        else -> "llevas ${v.dias} días"
    }
    val mes = MESES_DEL_RANGO[v.historiaDesde.monthNumber - 1] +
        (if (v.historiaDesde.year != anioDeHoy) " de ${v.historiaDesde.year}" else "")
    return "Este período va empezando: $llevas. Tienes historia desde $mes."
}

/** «Ver 3 meses»: la acción de la línea. */
fun accionDelPeriodoQueVaEmpezando(v: PeriodoQueVaEmpezando): String = "Ver ${v.rango.rotulo}"

// ── Un hueco en la historia ─────────────────────────────────────────────────

/** Un tramo de períodos enteros, seguidos, sin ningún movimiento. Los dos días incluidos. */
data class HuecoEnLaHistoria(val desde: LocalDate, val hasta: LocalDate)

/**
 * **Los huecos de [periodos]** (seguidos, el más viejo primero): los períodos enteros sin **ningún**
 * movimiento (no solo gastos) que tienen algún movimiento antes y alguno después —en el rango o fuera
 * de él—. Antes del primer movimiento del dueño no hay hueco (todavía no usaba Movi), y el período en
 * curso vacío tampoco (no hay nada después). Períodos vacíos seguidos se juntan en un solo hueco.
 */
fun huecosEnLaHistoria(
    eventos: List<FinancialEvent>,
    periodos: List<PeriodoFinanciero>,
    settings: PeriodSettings,
): List<HuecoEnLaHistoria> {
    if (eventos.isEmpty() || periodos.isEmpty()) return emptyList()
    val dias = eventos.map { epochMillisToAppDate(it.timestamp) }
    val primero = dias.min()
    val ultimo = dias.max()
    val huecos = mutableListOf<HuecoEnLaHistoria>()
    var abierto: HuecoEnLaHistoria? = null
    for (p in periodos) {
        val desde = inicioDelPeriodo(p, settings)
        val siguiente = inicioDelPeriodo(periodoSiguiente(p, settings), settings)
        val vacio = primero < desde && ultimo >= siguiente && dias.none { it >= desde && it < siguiente }
        val hasta = siguiente.minus(1, DateTimeUnit.DAY)
        abierto = if (vacio) {
            abierto?.copy(hasta = hasta) ?: HuecoEnLaHistoria(desde, hasta)
        } else {
            abierto?.let { huecos += it }
            null
        }
    }
    abierto?.let { huecos += it }
    return huecos
}

/** «No hay movimientos del 30 sep al 29 oct. Si tienes el extracto, súbelo y aparecen aquí.» */
fun textoDelHueco(hueco: HuecoEnLaHistoria): String =
    "No hay movimientos del ${diaCorto(hueco.desde)} al ${diaCorto(hueco.hasta)}. " +
        "Si tienes el extracto, súbelo y aparecen aquí."

/** La acción de la línea del hueco. */
const val ACCION_DEL_HUECO: String = "Subir extracto"

private fun diaCorto(fecha: LocalDate): String =
    "${fecha.dayOfMonth} ${MESES_CORTOS_DEL_RANGO[fecha.monthNumber - 1]}"

private val MESES_CORTOS_DEL_RANGO = listOf("ene", "feb", "mar", "abr", "may", "jun", "jul", "ago", "sep", "oct", "nov", "dic")
private val MESES_DEL_RANGO = listOf(
    "enero", "febrero", "marzo", "abril", "mayo", "junio",
    "julio", "agosto", "septiembre", "octubre", "noviembre", "diciembre",
)
