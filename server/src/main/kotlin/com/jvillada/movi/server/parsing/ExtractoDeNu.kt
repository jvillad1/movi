package com.jvillada.movi.server.parsing

import com.jvillada.movi.server.routes.SIN_CATEGORIA
import com.jvillada.movi.shared.model.ParsedTransaction
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.categoriaProbablePorElNombre
import java.time.LocalDate
import java.util.UUID
import kotlin.math.abs

/**
 * # El extracto de la tarjeta de Nu, leído sin IA (vuelta 94)
 *
 * Los cargos de una tarjeta (intereses, intereses de mora, la comisión por cambio de moneda) no los
 * avisa ningún SMS: solo los dice el extracto. El de Nu pasaba por Claude; este lector lo lee con
 * reglas, antes de `detectDocumentType` y de cualquier llamada a la IA, igual que
 * [leerLosMovimientosDeBancolombia]. Lo de después (conciliar contra lo anotado, «Cargos y abonos del
 * banco», la cuenta por moneda) es el camino de siempre.
 *
 * La forma sale del PDF real, con el texto que saca PDFBox (cifras, comercios y fechas inventados):
 *
 * ```
 * Nu Financiera
 * Fecha de corte
 * 12 OCT 2026
 * Periodo facturado
 * 11 SEP 2026 - 11 OCT 2026
 * PAGO MÍNIMO $96.004,50
 * Fecha Descripción Valor Cuotas Valor
 * …
 * 04 OCT
 * 2026
 * Libreria Central $120.000,00 1 de 12 $10.000,00 2.09%        $0,00 $10.000,00 $110.000,00
 * ↪ Comisión por cambio de moneda $540,00
 * 27 SEP
 * 2026
 * Pago $50.000,00 $0,00 $0,00
 * ↪ A capital $50.000,00
 * 18 JUL 2026 Tienda Vieja
 * Intereses en
 * mora
 * $60.000,00 $0,00 2.10%        $1.500,00
 * 2.10%        $100,00
 * $1.600,00 $0,00
 * ```
 *
 * - **Se reconoce** por «Nu Financiera», «Periodo facturado», «Fecha de corte» y «Pago mínimo».
 * - **Una fila** empieza en su fecha («04 OCT» con el año en el renglón siguiente, o «18 JUL 2026» con
 *   la descripción pegada) y termina cuando ya tiene todas sus cifras: el valor, las cuotas («1 de
 *   12», si las trae), el valor del mes, uno o dos intereses con su porcentaje, el total a pagar y lo
 *   que queda por pagar. Un pago tiene tres: el valor, lo que aplicó al mes y lo restante.
 * - **Lo que va con «↪»** cuelga de la fila de arriba: el desglose de un pago («A capital», «A
 *   intereses», «A cargos y comisiones») o un cargo de esa compra («Comisión por cambio de moneda»).
 * - **Lo que se propone**: las compras y los pagos **del período facturado** (entre su primer día y la
 *   fecha de corte) con su valor entero y sus cuotas —como la tarjeta de Bancolombia—; los intereses
 *   del extracto, sumados en «Intereses corrientes» y en «Intereses de mora» con la fecha de corte; y
 *   cada cargo «↪» con la fecha de su compra. Una compra de un período anterior (una cuota «2 de 12»)
 *   ya se anotó cuando se hizo: de ella solo cuentan sus intereses.
 * - **Tiene que cuadrar**, en centavos, o se devuelve `null` y el extracto sigue el camino de siempre:
 *   en cada fila, el valor del mes más sus intereses da el total; en una compra del período, el valor
 *   del mes más lo restante da el valor; el desglose de un pago suma el pago; y **la suma de los
 *   totales de las filas, lo que los pagos aplicaron al mes y los cargos «↪» da el pago mínimo** del
 *   extracto. Una fila que no se leyó rompe esa suma. Un movimiento equivocado es peor que ninguno.
 */
fun leerElExtractoDeNu(texto: String): ExtractoDeNu? {
    val plano = sinTildes(texto)
    if (!plano.contains("nu financiera") || !plano.contains("periodo facturado") ||
        !plano.contains("fecha de corte") || !plano.contains("pago minimo")
    ) return null

    val lineas = texto.replace(' ', ' ').lines().map { it.trim() }.filter { it.isNotEmpty() }
    val corte = lineas.indexOfFirst { sinTildes(it) == "fecha de corte" }
        .takeIf { it >= 0 }?.let { lineas.getOrNull(it + 1) }
        ?.let { FECHA_COMPLETA.matchEntire(it) }?.let(::fechaDe) ?: return null
    val inicio = lineas.indexOfFirst { sinTildes(it) == "periodo facturado" }
        .takeIf { it >= 0 }?.let { lineas.getOrNull(it + 1) }
        ?.let { INICIO_DEL_PERIODO.find(it) }?.let(::fechaDe) ?: return null
    if (inicio > corte) return null
    val pagoMinimo = lineas.firstNotNullOfOrNull { PAGO_MINIMO.find(sinTildes(it))?.groupValues?.get(1) }
        ?.let(::centavosDelExtracto) ?: return null
    val numeros = TARJETA.findAll(texto).map { it.groupValues[1] }.distinct().toList()

    val encabezado = lineas.indexOfFirst { sinTildes(it).startsWith("fecha descripcion valor") }
    if (encabezado < 0) return null

    val filas = mutableListOf<FilaDeNu>()
    var actual: FilaEnArmado? = null
    var diaSinAnio: Pair<Int, Int>? = null
    for (linea in lineas.drop(encabezado + 1)) {
        if (sinTildes(linea).startsWith("¿tienes preguntas") || sinTildes(linea).startsWith("tienes preguntas")) break
        // «05 SEP» y en el renglón siguiente «2026».
        val pendiente = diaSinAnio
        if (pendiente != null) {
            val anio = ANIO.matchEntire(linea)?.value?.toInt() ?: return null
            val fecha = runCatching { LocalDate.of(anio, pendiente.second, pendiente.first) }.getOrNull() ?: return null
            actual = FilaEnArmado(fecha)
            diaSinAnio = null
            continue
        }
        val sola = FECHA_SIN_ANIO.matchEntire(linea)
        val completa = if (sola == null) FECHA_CON_DESCRIPCION.matchEntire(linea) else null
        if (sola != null || completa != null) {
            // Una fila nueva con la anterior todavía sin sus cifras: la forma no es la esperada.
            val anterior = actual
            if (anterior != null) filas += anterior.cerrada ?: return null
            if (sola != null) {
                val mes = mesDelExtracto(sola.groupValues[2]) ?: return null
                diaSinAnio = sola.groupValues[1].toInt() to mes
                actual = null
            } else {
                val nueva = FilaEnArmado(fechaDe(completa!!) ?: return null)
                nueva.renglones += completa.groupValues[4].trim()
                if (nueva.intentarCerrar() != null) return null
                actual = nueva
            }
            continue
        }
        val fila = actual ?: continue
        if (linea.startsWith(FLECHA)) {
            // Lo que cuelga de la fila de arriba: solo después de que la fila tiene sus cifras.
            val cerrada = fila.cerrada ?: return null
            val sub = SUBRENGLON.matchEntire(linea.removePrefix(FLECHA).trim()) ?: return null
            val centavos = centavosDelExtracto(sub.groupValues[2]) ?: return null
            val rotulo = sub.groupValues[1].trim()
            fila.cerrada = if (DESGLOSE_DEL_PAGO.containsMatchIn(sinTildes(rotulo))) {
                cerrada.copy(desglose = (cerrada.desglose ?: 0L) + centavos)
            } else {
                cerrada.copy(cargos = cerrada.cargos + (rotulo to centavos))
            }
            continue
        }
        // Después de sus cifras, lo que no es «↪» ni otra fila es el pie de la página.
        if (fila.cerrada != null) continue
        fila.renglones += linea
        if (fila.intentarCerrar() != null) return null
    }
    if (diaSinAnio != null) return null
    val ultima = actual
    if (ultima != null) filas += ultima.cerrada ?: return null
    if (filas.isEmpty()) return null

    // Las cuentas de cada fila, y la de todas contra el pago mínimo.
    var suma = 0L
    for (f in filas) {
        if (f.esPago) {
            if (f.desglose != null && f.desglose != f.valor) return null
            suma += f.delMes
        } else {
            if (f.delMes + f.intereses.sum() != f.total) return null
            if (f.fecha in inicio..corte && f.delMes + f.restante != f.valor) return null
            suma += f.total
        }
        suma += f.cargos.sumOf { it.second }
    }
    if (suma != pagoMinimo) return null

    val propuestas = mutableListOf<ParsedTransaction>()
    for (f in filas) {
        if (f.fecha in inicio..corte && (f.cuotaNumero ?: 1) == 1) {
            movimiento(f.fecha, f.descripcion, f.valor, cuotas = f.cuotasTotales.takeIf { !f.esPago })?.let { propuestas += it }
        }
        f.cargos.forEach { (rotulo, centavos) -> movimiento(f.fecha, rotulo, centavos)?.let { propuestas += it } }
    }
    val corrientes = filas.sumOf { f -> if (f.conMora) f.intereses.dropLast(1).sum() else f.intereses.sum() }
    val mora = filas.sumOf { f -> if (f.conMora) f.intereses.lastOrNull() ?: 0L else 0L }
    movimiento(corte, INTERESES_CORRIENTES, corrientes)?.let { propuestas += it }
    movimiento(corte, INTERESES_DE_MORA, mora)?.let { propuestas += it }
    if (propuestas.isEmpty()) return null
    return ExtractoDeNu(numeros = numeros, corte = corte, inicio = inicio, filas = propuestas)
}

/**
 * Lo que se leyó: los últimos cuatro de las tarjetas que nombra («• • • • 5237»: la virtual y la
 * física), el período facturado y los movimientos que se proponen.
 */
data class ExtractoDeNu(
    val numeros: List<String>,
    val corte: LocalDate,
    val inicio: LocalDate,
    val filas: List<ParsedTransaction>,
)

/** El rótulo con que se proponen los intereses del extracto (los reconoce `cargoDelBanco`). */
internal const val INTERESES_CORRIENTES: String = "Intereses corrientes"

/** El de los intereses de mora. */
internal const val INTERESES_DE_MORA: String = "Intereses de mora"

/**
 * Una fila con sus cifras, en centavos. [valor] con el signo de la tarjeta: positivo es un cobro (una
 * compra), negativo un abono (una devolución). Un pago se guarda en positivo y [esPago] lo dice.
 */
private data class FilaDeNu(
    val fecha: LocalDate,
    val descripcion: String,
    val esPago: Boolean,
    val valor: Long,
    val delMes: Long,
    val intereses: List<Long> = emptyList(),
    val total: Long = 0L,
    val restante: Long = 0L,
    val cuotaNumero: Int? = null,
    val cuotasTotales: Int? = null,
    val conMora: Boolean = false,
    /** La suma del desglose «A capital / A intereses / A cargos» de un pago; `null` sin desglose. */
    val desglose: Long? = null,
    val cargos: List<Pair<String, Long>> = emptyList(),
)

/** Una fila mientras llegan sus renglones. [cerrada] queda puesta cuando ya tiene todas sus cifras. */
private class FilaEnArmado(val fecha: LocalDate) {
    val renglones = mutableListOf<String>()
    var cerrada: FilaDeNu? = null

    /**
     * Intenta leer la fila con lo que llegó. Devuelve un motivo si la fila ya no puede tener la forma
     * esperada (más cifras de las que caben); `null` si quedó cerrada o le faltan renglones.
     */
    fun intentarCerrar(): String? {
        val junto = renglones.joinToString(" ").trim()
        val primera = MONTO.find(junto) ?: return null
        var descripcion = junto.substring(0, primera.range.first).trim()
        val conMora = MORA_AL_FINAL.containsMatchIn(descripcion)
        descripcion = MORA_AL_FINAL.replace(descripcion, "").trim()
        if (descripcion.isEmpty()) return "sin descripción"
        val resto = junto.substring(primera.range.first)
        val montos = MONTO.findAll(resto).map { centavosDelExtracto(it.value.replace(" ", "")) }.toList()
        if (montos.any { it == null }) return "un monto ilegible"
        val m = montos.filterNotNull()
        val porcentajes = PORCENTAJE.findAll(resto).count()
        val cuotas = CUOTAS.find(resto)
        val esPago = sinTildes(descripcion) == "pago"
        if (esPago) {
            if (porcentajes > 0) return "un pago con intereses"
            if (m.size < 3) return null
            if (m.size > 3) return "un pago con más cifras"
            cerrada = FilaDeNu(fecha, descripcion, esPago = true, valor = m[0], delMes = m[1], restante = m[2])
            return null
        }
        if (porcentajes == 0 || m.size < 4 + porcentajes) return null
        if (m.size > 4 + porcentajes) return "una compra con más cifras"
        cerrada = FilaDeNu(
            fecha = fecha,
            descripcion = descripcion,
            esPago = false,
            valor = m[0],
            delMes = m[1],
            intereses = m.subList(2, 2 + porcentajes),
            total = m[2 + porcentajes],
            restante = m[3 + porcentajes],
            cuotaNumero = cuotas?.groupValues?.get(1)?.toInt(),
            cuotasTotales = cuotas?.groupValues?.get(2)?.toInt(),
            conMora = conMora,
        )
        return null
    }
}

/**
 * Un movimiento para la revisión. [centavos] con el signo de la tarjeta: positivo es un cobro (un
 * gasto de la tarjeta); negativo, un abono. Un pago llega en positivo y se propone como entrada.
 */
private fun movimiento(fecha: LocalDate, descripcion: String, centavos: Long, cuotas: Int? = null): ParsedTransaction? {
    val pesos = pesosDe(abs(centavos))
    if (pesos == 0L) return null
    val esPago = sinTildes(descripcion) == "pago"
    val tipo = if (esPago || centavos < 0) TransactionType.INCOME else TransactionType.EXPENSE
    return ParsedTransaction(
        id = UUID.randomUUID().toString(),
        date = fecha.toString(),
        merchant = descripcion,
        amount = pesos,
        currency = "COP",
        type = tipo,
        category = categoriaProbablePorElNombre(descripcion) ?: SIN_CATEGORIA,
        description = descripcion,
        rawText = descripcion,
        cuotas = cuotas,
    )
}

private fun fechaDe(m: MatchResult): LocalDate? {
    val mes = mesDelExtracto(m.groupValues[2]) ?: return null
    return runCatching { LocalDate.of(m.groupValues[3].toInt(), mes, m.groupValues[1].toInt()) }.getOrNull()
}

private const val FLECHA = "↪"

/** «12 OCT 2026». */
private val FECHA_COMPLETA = Regex("""^(\d{1,2})\s+([A-Za-zÁÉÍÓÚáéíóú]{3,10})\.?\s+(\d{4})$""")

/** «11 SEP 2026 - 11 OCT 2026» (o el final cortado: «12 AGO 2026 - 10 SEP»): el primer día. */
private val INICIO_DEL_PERIODO = Regex("""^(\d{1,2})\s+([A-Za-zÁÉÍÓÚáéíóú]{3,10})\.?\s+(\d{4})\s*-""")

/** «PAGO MÍNIMO $1.998,96», ya sin tildes y en minúsculas. */
private val PAGO_MINIMO = Regex("""^pago minimo\s+(-?\$\s?\d{1,3}(?:\.\d{3})*,\d{2})$""")

/** «05 SEP»: la fecha de una fila, con el año en el renglón siguiente. */
private val FECHA_SIN_ANIO = Regex("""^(\d{1,2})\s+([A-Z]{3})$""")

/** «18 JUL 2026 Tienda Vieja»: la fecha de una fila con el año y el comienzo de la descripción. */
private val FECHA_CON_DESCRIPCION = Regex("""^(\d{1,2})\s+([A-Z]{3})\s+(\d{4})\s+(\S.*)$""")

private val ANIO = Regex("""^\d{4}$""")

/** Una cifra de la tabla: «$1.998,96», «-$58.100,00». */
private val MONTO = Regex("""-?\$\s?\d{1,3}(?:\.\d{3})*,\d{2}""")

/** La tasa de un interés: «2.09%». */
private val PORCENTAJE = Regex("""\d+[.,]\d+\s?%""")

/** «1 de 24»: la cuota de esta compra y cuántas son. */
private val CUOTAS = Regex("""\b(\d{1,3})\s+de\s+(\d{1,3})\b""")

/** El rótulo de la columna de intereses que PDFBox pega a la descripción: «Intereses en mora». */
private val MORA_AL_FINAL = Regex("""\s*Intereses\s+en\s+mora\s*$""", RegexOption.IGNORE_CASE)

/** «Comisión por cambio de moneda $1.488,48» (sin la flecha). */
private val SUBRENGLON = Regex("""^(.+?)\s+(-?\$\s?\d{1,3}(?:\.\d{3})*,\d{2})$""")

/** El desglose de un pago: «A capital», «A intereses», «A cargos y comisiones». */
private val DESGLOSE_DEL_PAGO = Regex("""^a\s+(capital|intereses|cargos)""")

/** «• • • • 5237»: los últimos cuatro de una tarjeta. */
private val TARJETA = Regex("""(?:•\s*){4}(\d{4})""")
