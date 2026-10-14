package com.jvillada.movi.shared.model

import kotlinx.datetime.plus
import kotlinx.serialization.Serializable

/**
 * # «Cerró tu período»: el resumen de cierre, sin IA
 *
 * El día siguiente al corte Movi cuenta cómo le fue al dueño en el período que acaba de cerrar, por
 * tres lados: una tarjeta en Hoy los primeros días, un aviso del teléfono y un correo. **Es
 * determinista**: no lo escribe un modelo —Anthropic puede no tener crédito y el resumen tiene que
 * salir igual—, lo arma [cierreDelPeriodo] con cifras que ya calculan las pantallas.
 *
 * ## De dónde sale cada cifra (ninguna se suma a mano)
 *
 * - **Entró / salió**: las de «Tus períodos» ([ResumenDePeriodo.entradas] y [ResumenDePeriodo.salidas],
 *   la regla de «Ingresos» y «Gastos»).
 * - **Contra el período anterior**: el [ResumenDePeriodo] del anterior, con la misma regla. Solo si
 *   Movi lo cubrió entero: contra un período a medio anotar todo parecería «más».
 * - **Las 3 categorías de más gasto**: el [DetalleDePeriodo.porCategoria] del período.
 * - **Los pagos del período**: la lista «Pagos del período» de Plan ([checklistDelPeriodo]) tal como
 *   estaba el último día del período: cuántos se pagaron y cuáles no. Los ingresos (el sueldo) no son
 *   pagos y no cuentan.
 * - **Lo que se salió de lo normal**: los avisos de [Anomalia] de ese período, sin los que el dueño ya
 *   descartó con «Está bien».
 * - **Cuánto quedó del Disponible**: la columna «Período» de la tarjeta Disponible
 *   ([disponibleDelPeriodo]) el último día del período — lo que habría dicho Hoy esa noche.
 *
 * Todo el texto sale de acá ([tituloDelCierre], [fraseDelCierre], [lineasDelCierre]): la tarjeta, el
 * aviso y el correo dicen lo mismo con las mismas palabras.
 */
@Serializable
data class CierreDelPeriodo(
    /** El prefijo del período que cerró, `"2026-09"` (ver [PeriodoFinanciero.prefijo]). */
    val periodo: String,
    /** «Septiembre 2026». */
    val nombre: String,
    /** Primer y último día del período, ISO. */
    val desde: String = "",
    val hasta: String = "",
    val entro: Long = 0,
    val salio: Long = 0,
    /** El período anterior, o `null` si Movi no lo cubrió entero (o no hay). */
    val anterior: CifrasDelAnterior? = null,
    /** Las [CATEGORIAS_DEL_CIERRE] categorías de más gasto, de mayor a menor. */
    val categorias: List<GastoDeCategoria> = emptyList(),
    /** Cuántos pagos del período se pagaron, de [pagosEsperados]. */
    val pagosCumplidos: Int = 0,
    val pagosEsperados: Int = 0,
    /** Los nombres de los pagos que no se pagaron, por fecha de vencimiento. */
    val pagosQueFaltaron: List<String> = emptyList(),
    /** Los títulos de lo que se salió de lo normal («¿Cobro duplicado en Rappi?»). */
    val fueraDeLoNormal: List<String> = emptyList(),
    /** Lo que quedó del Disponible el último día: positivo = sobró, negativo = te pasaste. `null` = no se pudo afirmar. */
    val quedoDelDisponible: Long? = null,
    /** Qué día del período NUEVO es hoy, contando el primero como 1. Decide cuándo se muestra y se avisa. */
    val diaDelPeriodoNuevo: Int = 1,
    /** El dueño ya cerró la tarjeta de Hoy de este período. */
    val descartado: Boolean = false,
)

/** Lo que entró y salió en el período anterior al que cerró. */
@Serializable
data class CifrasDelAnterior(val periodo: String, val nombre: String, val entro: Long, val salio: Long)

/** `POST /api/cierre-del-periodo/descartar`: no volver a mostrar en Hoy la tarjeta de [periodo]. */
@Serializable
data class DescartarCierreRequest(val periodo: String)

/** Cuántas categorías nombra el resumen. */
const val CATEGORIAS_DEL_CIERRE: Int = 3

/**
 * Cuántos días del período nuevo se ve la tarjeta en Hoy («los primeros días»). Elegido, no medido:
 * una semana de trabajo; después el resumen sigue en «Tus períodos».
 */
const val DIAS_DE_LA_TARJETA_DE_CIERRE: Int = 5

/**
 * Hasta qué día del período nuevo se manda el aviso o el correo si no salió el primero (el teléfono
 * apagado, el server reiniciando). Pasado eso, un «cerró tu período» llega tarde para servir.
 */
const val DIAS_PARA_AVISAR_EL_CIERRE: Int = 3

/**
 * **El resumen de cierre**, con las cifras que ya calcularon las pantallas (ver el KDoc del archivo).
 *
 * @param resumen el período que cerró, de «Tus períodos».
 * @param porCategoria su gasto por categoría ([DetalleDePeriodo.porCategoria]).
 * @param anterior el período anterior, solo si Movi lo cubrió entero.
 * @param pagos la lista «Pagos del período» el último día del período ([checklistDelPeriodo]).
 * @param anomalias lo que se salió de lo normal en ese período, sin lo descartado.
 * @param disponible la tarjeta Disponible el último día del período, o `null` si no se pudo afirmar.
 * @param diaDelPeriodoNuevo qué día del período en curso es hoy (el primero es 1).
 */
fun cierreDelPeriodo(
    resumen: ResumenDePeriodo,
    porCategoria: List<GastoDeCategoria>,
    anterior: ResumenDePeriodo?,
    pagos: List<PagoDelPeriodo>,
    anomalias: List<Anomalia>,
    disponible: DisponibleDelPeriodo?,
    diaDelPeriodoNuevo: Int,
    descartado: Boolean = false,
): CierreDelPeriodo {
    val obligaciones = pagos.filter { !it.esIngreso }
    return CierreDelPeriodo(
        periodo = resumen.id,
        nombre = resumen.nombre,
        desde = resumen.desde,
        hasta = resumen.hasta,
        entro = resumen.entradas,
        salio = resumen.salidas,
        anterior = anterior?.let { CifrasDelAnterior(it.id, it.nombre, it.entradas, it.salidas) },
        categorias = porCategoria
            .filter { it.monto > 0 }
            .sortedWith(compareByDescending<GastoDeCategoria> { it.monto }.thenBy { it.category.lowercase() })
            .take(CATEGORIAS_DEL_CIERRE),
        pagosCumplidos = obligaciones.count { it.pagado },
        pagosEsperados = obligaciones.size,
        pagosQueFaltaron = obligaciones.filterNot { it.pagado }.sortedBy { it.vence }.map { it.nombre },
        fueraDeLoNormal = anomalias.map { it.titulo },
        quedoDelDisponible = disponible?.periodo?.teQuedan,
        diaDelPeriodoNuevo = diaDelPeriodoNuevo,
        descartado = descartado,
    )
}

/**
 * **La tarjeta Disponible del último día de un período**, con fechas ISO (el server no ve
 * `kotlinx-datetime`): la MISMA [disponibleDelPeriodo] que pinta Hoy, como si «hoy» fuera [hasta].
 * `null` si no hay nada honesto que decir (ver [disponibleDelPeriodo]) o si las fechas no se entienden.
 *
 * @param desde el primer día del período, ISO.
 * @param hasta el último día INCLUIDO, ISO.
 */
fun disponibleAlCerrar(
    ingresosRecibidos: Long,
    pagos: List<PagoDelPeriodo>,
    gastoVariablePorDia: Map<String, Long>,
    desde: String,
    hasta: String,
    plata: PlataDelDisponible?,
): DisponibleDelPeriodo? = disponibleAlDia(ingresosRecibidos, pagos, gastoVariablePorDia, desde, hasta, hoy = hasta, plata = plata)

/**
 * **La tarjeta Disponible de [hoy]**, con fechas ISO (el server no ve `kotlinx-datetime`): la MISMA
 * [disponibleDelPeriodo] que pinta Hoy. La usan el resumen de cierre (con [hoy] = el último día) y
 * Movi AI al contestar «¿Cómo voy este período?» sin el modelo (con [hoy] = hoy). `null` si no hay
 * nada honesto que decir o si las fechas no se entienden.
 *
 * @param desde el primer día del período, ISO.
 * @param hasta el último día INCLUIDO, ISO.
 * @param hoy el día que se mira, ISO; tiene que caer dentro del período.
 */
fun disponibleAlDia(
    ingresosRecibidos: Long,
    pagos: List<PagoDelPeriodo>,
    gastoVariablePorDia: Map<String, Long>,
    desde: String,
    hasta: String,
    hoy: String,
    plata: PlataDelDisponible?,
): DisponibleDelPeriodo? {
    val inicio = runCatching { kotlinx.datetime.LocalDate.parse(desde) }.getOrNull() ?: return null
    val ultimo = runCatching { kotlinx.datetime.LocalDate.parse(hasta) }.getOrNull() ?: return null
    val dia = runCatching { kotlinx.datetime.LocalDate.parse(hoy) }.getOrNull() ?: return null
    return disponibleDelPeriodo(
        ingresosRecibidos = ingresosRecibidos,
        checklist = pagos,
        gastoVariablePorDia = gastoVariablePorDia,
        inicio = inicio,
        finExclusivo = ultimo.plus(1, kotlinx.datetime.DateTimeUnit.DAY),
        hoy = dia,
        plata = plata,
    )
}

// ── Cuándo se muestra y se avisa ─────────────────────────────────────────────

/** ¿Se pinta la tarjeta en Hoy? Los primeros [DIAS_DE_LA_TARJETA_DE_CIERRE] días y si no se descartó. */
fun seMuestraEnHoy(cierre: CierreDelPeriodo): Boolean =
    !cierre.descartado && cierre.diaDelPeriodoNuevo in 1..DIAS_DE_LA_TARJETA_DE_CIERRE

/** ¿Todavía es hora de avisar o mandar el correo? Los primeros [DIAS_PARA_AVISAR_EL_CIERRE] días. */
fun esHoraDeAvisarElCierre(diaDelPeriodoNuevo: Int): Boolean = diaDelPeriodoNuevo in 1..DIAS_PARA_AVISAR_EL_CIERRE

// ── Lo que dice ──────────────────────────────────────────────────────────────
//
// En tuteo y en pesos enteros («$1.234.567»): el correo y el aviso no tienen la cifra compacta de la
// app, y una cifra de cierre se lee entera.

/** «$1.234.567», con el signo menos tipográfico si es negativa. */
fun pesosDelCierre(monto: Long): String {
    val digitos = (if (monto < 0) -monto else monto).toString()
    val conPuntos = digitos.reversed().chunked(3).joinToString(".").reversed()
    return (if (monto < 0) "−" else "") + "$" + conPuntos
}

/** «septiembre», del prefijo; el prefijo tal cual si no se entiende. */
private fun mesDe(prefijo: String, nombre: String): String =
    periodoDelPrefijo(prefijo)?.let { tituloDelPeriodo(it).substringBefore(" ").lowercase() }
        ?: nombre.substringBefore(" ").lowercase()

/** «Cerró tu período de septiembre». */
fun tituloDelCierre(c: CierreDelPeriodo): String = "Cerró tu período de ${mesDe(c.periodo, c.nombre)}"

/** «Cerró tu período: entró $22.500.000, salió $18.200.000». La frase de la tarjeta y del aviso. */
fun fraseDelCierre(c: CierreDelPeriodo): String =
    "Cerró tu período: entró ${pesosDelCierre(c.entro)}, salió ${pesosDelCierre(c.salio)}"

/** «más», «menos» o «lo mismo» con la diferencia. */
private fun diferenciaDicha(verbo: String, ahora: Long, antes: Long): String {
    val d = ahora - antes
    return when {
        d > 0 -> "$verbo ${pesosDelCierre(d)} más"
        d < 0 -> "$verbo ${pesosDelCierre(-d)} menos"
        else -> "$verbo lo mismo"
    }
}

/** «Contra agosto: entró $1.000.000 más y salió $500.000 menos», o `null` sin anterior. */
fun comparacionDelCierre(c: CierreDelPeriodo): String? {
    val a = c.anterior ?: return null
    return "Contra ${mesDe(a.periodo, a.nombre)}: ${diferenciaDicha("entró", c.entro, a.entro)} y " +
        diferenciaDicha("salió", c.salio, a.salio)
}

/** «Lo que más pesó: Vivienda $3.100.000 · Comida $900.000 · Transporte $400.000», o `null` sin gasto. */
fun categoriasDelCierre(c: CierreDelPeriodo): String? =
    c.categorias.takeIf { it.isNotEmpty() }
        ?.joinToString(" · ", prefix = "Lo que más pesó: ") { "${it.category} ${pesosDelCierre(it.monto)}" }

/** «Gimnasio», «Gimnasio y Celular», «Gimnasio, Celular y 2 más». */
private fun nombresCortos(nombres: List<String>): String = when (nombres.size) {
    1 -> nombres[0]
    2 -> "${nombres[0]} y ${nombres[1]}"
    3 -> "${nombres[0]}, ${nombres[1]} y ${nombres[2]}"
    else -> "${nombres[0]}, ${nombres[1]} y ${nombres.size - 2} más"
}

/**
 * «Pagos: cumpliste 6 de 7 · faltó Gimnasio», «Pagos: cumpliste los 7», o `null` sin pagos en la
 * lista.
 */
fun pagosDelCierre(c: CierreDelPeriodo): String? {
    if (c.pagosEsperados == 0) return null
    if (c.pagosQueFaltaron.isEmpty()) {
        return if (c.pagosEsperados == 1) "Pagos: cumpliste el único del período" else "Pagos: cumpliste los ${c.pagosEsperados}"
    }
    val falto = if (c.pagosQueFaltaron.size == 1) "faltó" else "faltaron"
    return "Pagos: cumpliste ${c.pagosCumplidos} de ${c.pagosEsperados} · $falto ${nombresCortos(c.pagosQueFaltaron)}"
}

/** «Fuera de lo normal: ¿Cobro duplicado en Rappi?» (y «… y 2 más»), o `null` si no hubo. */
fun fueraDeLoNormalDelCierre(c: CierreDelPeriodo): String? {
    val primero = c.fueraDeLoNormal.firstOrNull() ?: return null
    val resto = c.fueraDeLoNormal.size - 1
    return "Fuera de lo normal: $primero" + if (resto > 0) " y $resto más" else ""
}

/** «Te quedaron $420.000 del disponible», «Te pasaste del disponible por $180.000», o `null`. */
fun disponibleDelCierre(c: CierreDelPeriodo): String? {
    val quedo = c.quedoDelDisponible ?: return null
    return when {
        quedo > 0 -> "Te quedaron ${pesosDelCierre(quedo)} del disponible"
        quedo < 0 -> "Te pasaste del disponible por ${pesosDelCierre(-quedo)}"
        else -> "Usaste todo el disponible, ni un peso más"
    }
}

/**
 * **Todo el resumen, un renglón por cosa**, en el orden en que se lee: lo que entró y salió, contra el
 * anterior, en qué se fue, los pagos, lo raro y lo que quedó. Lo que no aplica no aparece.
 */
fun lineasDelCierre(c: CierreDelPeriodo): List<String> = listOfNotNull(
    "Entró ${pesosDelCierre(c.entro)} · salió ${pesosDelCierre(c.salio)}",
    comparacionDelCierre(c),
    categoriasDelCierre(c),
    pagosDelCierre(c),
    fueraDeLoNormalDelCierre(c),
    disponibleDelCierre(c),
)
