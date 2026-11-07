package com.jvillada.movi.server.ai

import com.jvillada.movi.server.time.epochMillisToAppDateString
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.PeriodSettings
import com.jvillada.movi.shared.model.PeriodoFinanciero
import com.jvillada.movi.shared.model.PresentacionDelMovimiento
import com.jvillada.movi.shared.model.claveDelComercio
import com.jvillada.movi.shared.model.comoSePresenta
import com.jvillada.movi.shared.model.esGastoDelPeriodo
import com.jvillada.movi.shared.model.esTuParteDeOtro
import com.jvillada.movi.shared.model.esperaEnPorConfirmar
import com.jvillada.movi.shared.model.marcaDe
import com.jvillada.movi.shared.model.marcaDelTexto
import com.jvillada.movi.shared.model.montoContado
import com.jvillada.movi.shared.model.normalizarParaBuscar
import com.jvillada.movi.shared.model.palabrasDeMarca
import com.jvillada.movi.shared.model.periodoDe
import com.jvillada.movi.shared.model.periodoSiguiente

/**
 * # «¿Cuánto me he gastado en Flypass?»: el total de un comercio (vuelta 142, issue #697)
 *
 * El 6-nov Movi AI encontró 43 pagos a un comercio y contestó «no tengo el total exacto». No era el
 * modelo: **ninguna herramienta sumaba por comercio**. `buscar_movimientos` lista hasta
 * [TOPE_DE_RESULTADOS] y, al cortar, mandaba a sumar con `totales_por_categoria`, que no filtra por
 * texto. Sin una cifra hecha, el modelo no puede sumar 43 renglones (ni debe: el verificador de
 * cifras no acepta una suma de más de dos), así que lo honesto era decir que no sabía.
 *
 * Esta es la cuenta que faltaba, pura (sin base) para poder fijarla en pruebas:
 *
 * - **Qué casa con lo que escribió la persona** ([casaConElComercio]): la marca del catálogo
 *   ([marcaDelTexto] de lo escrito contra [marcaDe] del movimiento: «rappi» casa con «RAPPI*LAS
 *   DOCE») o, si no, lo escrito contenido en el comercio o la descripción, sin tildes ni mayúsculas
 *   ([normalizarParaBuscar], la misma comparación de `buscar_movimientos`). Ninguna normalización
 *   nueva. **Un nombre corto** (hasta [LARGO_DE_UN_NOMBRE_CORTO] letras) tiene que ser una palabra
 *   entera ([palabrasDeMarca], las palabras del catálogo): «zt» no casa con «PIZZTERIA», ni «d1» con
 *   «D1SOFT» (revisión de la vuelta 142).
 * - **Qué es «el mismo comercio»** es [claveDelComercio] (la marca primero, después
 *   `claveDeComercio`): si lo escrito casa con varios, cada uno sale con su total.
 * - **Qué suma** es la regla de «Gastos»: [esGastoDelPeriodo] (un gasto en pesos que cuenta como
 *   flujo y no espera en «Por confirmar») y [montoContado] (de un gasto compartido, tu parte). Los
 *   anulados no llegan (los saca quien carga los movimientos). Un gasto en otra moneda que casa va
 *   **aparte**, por moneda: nunca se suma a los pesos.
 */

/** Cuántos pagos recientes se nombran: los tres que le sirven para reconocer el cobro. */
internal const val RECIENTES_DE_UN_COMERCIO = 3

/** Cuántos comercios distintos se listan cuando lo escrito casa con varios. */
internal const val TOPE_DE_COMERCIOS_QUE_CASAN = 8

/** Lo escrito tiene que tener al menos esto para buscar por texto («D1» sí, «d» no). */
internal const val LARGO_MINIMO_DEL_COMERCIO = 2

/** Hasta este largo, lo escrito casa solo como palabra entera: dos o tres letras están dentro de cualquier cosa. */
internal const val LARGO_DE_UN_NOMBRE_CORTO = 3

internal data class PagoEnElComercio(val fecha: String, val nombre: String, val monto: Long, val cuenta: String)

internal data class ComercioQueCasa(
    val nombre: String,
    val total: Long,
    val pagos: Int,
    val primerPago: String,
    val ultimoPago: String,
)

internal data class GastoEnOtraMoneda(val moneda: String, val total: Long, val pagos: Int)

internal data class LoGastadoEnUnComercio(
    val consulta: String,
    /** En pesos, con la regla de «Gastos»: la suma de todos los [comercios]. */
    val total: Long,
    val pagos: Int,
    val primerPago: String?,
    /** Los [RECIENTES_DE_UN_COMERCIO] más recientes, del más nuevo al más viejo. */
    val recientes: List<PagoEnElComercio>,
    /** Los comercios distintos que casan (en pesos), del que más suma al que menos. */
    val comercios: List<ComercioQueCasa>,
    val enOtraMoneda: List<GastoEnOtraMoneda>,
    /**
     * Los períodos con los que se promedia: desde el del **primer pago que casa** hasta el último del
     * rango. Los de antes no cuentan: el comercio no existía para el dueño (revisión de la vuelta 142).
     */
    val periodos: List<PeriodoFinanciero>,
    /** [total] ÷ cuántos [periodos]; `null` sin gasto. */
    val promedioPorPeriodo: Long?,
)

/**
 * ¿[evento] es de lo que [consulta] nombra? La marca del catálogo, o lo escrito dentro del comercio o
 * la descripción. Ver el KDoc del archivo.
 */
internal fun casaConElComercio(evento: FinancialEvent, consulta: String): Boolean {
    val marcaDeLoEscrito = marcaDelTexto(consulta)
    if (marcaDeLoEscrito != null && marcaDe(evento)?.id == marcaDeLoEscrito.id) return true
    val aguja = normalizarParaBuscar(consulta)
    if (aguja.length < LARGO_MINIMO_DEL_COMERCIO) return false
    val textos = listOf(evento.merchant.orEmpty(), evento.description)
    if (aguja.length <= LARGO_DE_UN_NOMBRE_CORTO) {
        val palabras = palabrasDeMarca(consulta).joinToString(" ").takeIf { it.isNotEmpty() } ?: return false
        return textos.any { " $palabras " in " " + palabrasDeMarca(it).joinToString(" ") + " " }
    }
    return textos.any { aguja in normalizarParaBuscar(it) }
}

/** El orden de «los más recientes»: la fecha, y en el mismo instante el que se anotó después. */
private val DEL_MAS_NUEVO = compareByDescending<FinancialEvent> { it.timestamp }
    .thenByDescending { it.createdAt ?: 0L }
    .thenByDescending { it.id }

/**
 * **Lo gastado en lo que [consulta] nombra**, entre [desde] y [hastaExclusivo] (epoch-ms). [eventos]
 * ya vienen sin anulados. Los períodos del promedio son los de [settings] desde el del primer pago.
 */
internal fun loGastadoEnUnComercio(
    eventos: List<FinancialEvent>,
    consulta: String,
    tiposDeCuenta: Map<String, AccountType>,
    nombreDeCuenta: Map<String, String>,
    settings: PeriodSettings,
    desde: Long,
    hastaExclusivo: Long,
): LoGastadoEnUnComercio {
    val casan = eventos
        .filter { it.timestamp >= desde && it.timestamp < hastaExclusivo }
        .filter { casaConElComercio(it, consulta) }
        .distinctBy { it.id }
    val enPesos = casan.filter { esGastoDelPeriodo(it, tiposDeCuenta[it.accountId]) && montoContado(it) > 0L }
    // La misma regla, en otra moneda: un gasto que cuenta, que no espera en «Por confirmar».
    val otraMoneda = casan.filter {
        it.currency != "COP" &&
            comoSePresenta(it, tiposDeCuenta[it.accountId]) == PresentacionDelMovimiento.GASTO &&
            !esperaEnPorConfirmar(it.reconciliationStatus) &&
            montoContado(it) > 0L
    }

    val total = enPesos.sumOf { montoContado(it) }
    val ordenados = enPesos.sortedWith(DEL_MAS_NUEVO)
    fun fecha(e: FinancialEvent) = epochMillisToAppDateString(e.timestamp)

    val comercios = enPesos
        .groupBy { claveDelComercio(it) ?: "texto:${normalizarParaBuscar(it.description)}" }
        .values
        .map { delComercio ->
            val porFecha = delComercio.sortedWith(DEL_MAS_NUEVO)
            val muestra = porFecha.first()
            ComercioQueCasa(
                nombre = marcaDe(muestra)?.nombre ?: muestra.merchant?.takeIf { it.isNotBlank() } ?: muestra.description,
                total = delComercio.sumOf { montoContado(it) },
                pagos = delComercio.size,
                primerPago = fecha(porFecha.last()),
                ultimoPago = fecha(muestra),
            )
        }
        .sortedWith(compareByDescending<ComercioQueCasa> { it.total }.thenBy { it.nombre })

    val periodos = ordenados.lastOrNull()?.let { periodosEntre(it.timestamp, hastaExclusivo, settings) }.orEmpty()
    return LoGastadoEnUnComercio(
        consulta = consulta,
        total = total,
        pagos = enPesos.size,
        primerPago = ordenados.lastOrNull()?.let(::fecha),
        recientes = ordenados.take(RECIENTES_DE_UN_COMERCIO).map {
            // Tu parte de lo que pagó otro no salió de ninguna cuenta tuya: se dice así, como en los totales.
            val cuenta = if (esTuParteDeOtro(it)) TU_PARTE_DE_UN_GASTO_COMPARTIDO else nombreDeCuenta[it.accountId] ?: "otra cuenta"
            PagoEnElComercio(fecha(it), it.description, montoContado(it), cuenta)
        },
        comercios = comercios,
        enOtraMoneda = otraMoneda.groupBy { it.currency }.toSortedMap().map { (moneda, deEsa) ->
            GastoEnOtraMoneda(moneda, deEsa.sumOf { montoContado(it) }, deEsa.size)
        },
        periodos = periodos,
        promedioPorPeriodo = if (total > 0L && periodos.isNotEmpty()) total / periodos.size else null,
    )
}

/** Los períodos de [settings] que tocan `[desde, hastaExclusivo)`, del primero al último. */
internal fun periodosEntre(desde: Long, hastaExclusivo: Long, settings: PeriodSettings): List<PeriodoFinanciero> {
    if (hastaExclusivo <= desde) return emptyList()
    val ultimo = periodoDe(hastaExclusivo - 1, settings)
    val lista = mutableListOf(periodoDe(desde, settings))
    // Con tope: un rango absurdo (un movimiento de 1970) no puede colgar la consulta.
    while (lista.last() < ultimo && lista.size < 1_000) lista += periodoSiguiente(lista.last(), settings)
    return lista
}
