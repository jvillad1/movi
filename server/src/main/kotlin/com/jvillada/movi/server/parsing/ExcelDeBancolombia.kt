package com.jvillada.movi.server.parsing

import com.jvillada.movi.shared.model.ParsedTransaction
import org.apache.poi.ss.usermodel.Cell
import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.ss.usermodel.DataFormatter
import org.apache.poi.ss.usermodel.DateUtil
import org.apache.poi.ss.usermodel.Row
import org.apache.poi.ss.usermodel.WorkbookFactory
import java.io.ByteArrayInputStream
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.math.roundToLong

/**
 * # Vuelta 107 · los movimientos de una cuenta de Bancolombia en Excel, leídos sin IA
 *
 * La Sucursal Virtual Personas deja bajar los movimientos de una cuenta en PDF **o en Excel** (pestaña
 * «Movimientos» → «Descargar» → «Selecciona el formato: PDF / Excel», verificado en el portal el 2-nov).
 * El PDF ya se lee sin IA ([leerLosMovimientosDeBancolombia]); esto lee el Excel **con las mismas
 * reglas**: la misma fila ([filaDeBancolombia]: la referencia fuera de la descripción, el prefijo del
 * banco fuera del comercio, los centavos al peso, lo de menos de un peso no se propone, el signo dice el
 * tipo) y, después, el camino de siempre (conciliar, «Cargos y abonos del banco»).
 *
 * **No hay un Excel real en el repo.** Las columnas se supusieron iguales a las del PDF —«Fecha»,
 * «Descripción», «Referencia», «Valor»— y por eso el detector es **estricto**: una fila de encabezados
 * con esas cuatro y nada más, en ese orden (sin tildes ni mayúsculas). Si no aparece, o si una fila no
 * trae una fecha o un valor que se lea, devuelve `null` y el archivo sigue el camino de siempre (el texto
 * de la hoja va a Claude). Cuando el dueño suba un Excel real, se ajusta acá.
 *
 * Lee `.xlsx` y `.xls` con Apache POI (`poi-ooxml`, que ya estaba en el server para el presupuesto de
 * Famirios: no se agregó ninguna dependencia). La fecha puede venir como fecha de Excel o como texto
 * («07 sept 2026», «07/09/2026», «2026/09/07», «2026-09-07»); el valor, como número o como texto
 * («-$ 1.000.000,00»).
 */
fun leerElExcelDeBancolombia(bytes: ByteArray): MovimientosDeBancolombia? {
    val libro = runCatching { WorkbookFactory.create(ByteArrayInputStream(bytes)) }.getOrNull() ?: return null
    // Revisión: una celda rara (un NaN, una fórmula rota, una hoja corrupta) no tumba la subida: esa hoja
    // no se lee y el archivo sigue el camino de siempre.
    return runCatching {
        libro.use { wb ->
            (0 until wb.numberOfSheets).asSequence()
                .mapNotNull { i -> runCatching { leerLaHoja(wb.getSheetAt(i)) }.getOrNull() }
                .firstOrNull()
        }
    }.getOrNull()
}

/** Revisión de la vuelta 107: el tope de una entrada de un `.xlsx` (un zip) al inflarse. */
internal const val TOPE_DE_UNA_ENTRADA_DE_EXCEL: Long = 50L * 1024 * 1024

/** Revisión de la vuelta 107: la menor razón comprimido/inflado que se acepta (la de POI por defecto). */
internal const val RAZON_MINIMA_DE_INFLADO_DE_EXCEL: Double = 0.01

/**
 * **Los topes de POI contra un Excel bomba** (un zip que se infla a gigas), para todo uso de POI en el
 * server: el lector de Bancolombia y `StatementParser.extractText`. Se llama una vez al arrancar.
 */
fun configurarLosTopesDePoi() {
    org.apache.poi.openxml4j.util.ZipSecureFile.setMaxEntrySize(TOPE_DE_UNA_ENTRADA_DE_EXCEL)
    org.apache.poi.openxml4j.util.ZipSecureFile.setMinInflateRatio(RAZON_MINIMA_DE_INFLADO_DE_EXCEL)
}

/** Los encabezados que se esperan, en orden: los del PDF de «Movimientos: Cuentas». */
internal val ENCABEZADOS_DEL_EXCEL_DE_BANCOLOMBIA = listOf("fecha", "descripcion", "referencia", "valor")

private val formato = DataFormatter(java.util.Locale.ROOT)

private fun textoDe(celda: Cell?): String =
    celda?.let { runCatching { formato.formatCellValue(it) }.getOrDefault("") }?.replace(' ', ' ')?.trim().orEmpty()

/** Las celdas con algo escrito de una fila, con su columna. */
private fun celdasConTexto(fila: Row): List<Pair<Int, String>> =
    (0 until maxOf(0, fila.lastCellNum.toInt())).mapNotNull { c -> textoDe(fila.getCell(c)).takeIf { it.isNotEmpty() }?.let { c to it } }

private fun leerLaHoja(hoja: org.apache.poi.ss.usermodel.Sheet): MovimientosDeBancolombia? {
    val filas = (hoja.firstRowNum..hoja.lastRowNum).mapNotNull { hoja.getRow(it) }
    val iEncabezado = filas.indexOfFirst { esElEncabezado(it) }
    if (iEncabezado < 0) return null
    val columnas = celdasConTexto(filas[iEncabezado]).map { it.first }
    val (cFecha, cDescripcion, cReferencia, cValor) = columnas

    // El número de la cuenta, si la hoja lo escribe arriba como el PDF («Ahorros 012 - 345678 - 90»).
    val numero = filas.take(iEncabezado).asSequence()
        .flatMap { celdasConTexto(it).map { c -> c.second } }
        .firstNotNullOfOrNull { CUENTA_DE_BANCOLOMBIA.find(it)?.groupValues?.get(2) }
        ?.filter { it.isDigit() }?.takeLast(4)

    val movimientos = mutableListOf<ParsedTransaction>()
    for (fila in filas.drop(iEncabezado + 1)) {
        val conTexto = celdasConTexto(fila)
        if (conTexto.isEmpty()) continue
        // Un encabezado repetido (una hoja que lo repite por página) no es un movimiento.
        if (esElEncabezado(fila)) continue
        val fecha = fechaDe(fila.getCell(cFecha)) ?: return null
        val centavos = centavosDe(fila.getCell(cValor)) ?: return null
        val descripcion = textoDe(fila.getCell(cDescripcion))
        val referencia = textoDe(fila.getCell(cReferencia))
        // La referencia va pegada a la descripción, como en el PDF: [filaDeBancolombia] la saca.
        val crudo = listOf(descripcion, referencia).filter { it.isNotEmpty() }.joinToString(" ")
        filaDeBancolombia(fecha, crudo, centavos, conTexto.joinToString(" ") { it.second })?.let { movimientos += it }
    }
    if (movimientos.isEmpty()) return null
    return MovimientosDeBancolombia(numero = numero, filas = movimientos)
}

/** ¿Es la fila de encabezados, exacta? Ver [ENCABEZADOS_DEL_EXCEL_DE_BANCOLOMBIA]. */
private fun esElEncabezado(fila: Row): Boolean =
    celdasConTexto(fila).map { sinTildes(it.second).trim() } == ENCABEZADOS_DEL_EXCEL_DE_BANCOLOMBIA

private val FECHA_CON_MES = Regex("""^(\d{1,2})\s+([A-Za-zÁÉÍÓÚáéíóú]{3,10})\.?\s+(\d{4})$""")
private val FECHA_DMY = Regex("""^(\d{1,2})/(\d{1,2})/(\d{4})$""")
private val FECHA_YMD = Regex("""^(\d{4})[/-](\d{1,2})[/-](\d{1,2})$""")

/** La fecha de una celda: una fecha de Excel o un texto con una de las formas de arriba. */
private fun fechaDe(celda: Cell?): LocalDate? {
    celda ?: return null
    if (celda.cellType == CellType.NUMERIC && DateUtil.isCellDateFormatted(celda)) {
        return runCatching { celda.localDateTimeCellValue.toLocalDate() }.getOrNull()
            ?: runCatching { celda.dateCellValue.toInstant().atOffset(ZoneOffset.UTC).toLocalDate() }.getOrNull()
    }
    val texto = textoDe(celda)
    return runCatching {
        FECHA_CON_MES.matchEntire(texto)?.let { m ->
            val mes = mesDelExtracto(m.groupValues[2]) ?: return null
            return LocalDate.of(m.groupValues[3].toInt(), mes, m.groupValues[1].toInt())
        }
        FECHA_DMY.matchEntire(texto)?.let { m -> return LocalDate.of(m.groupValues[3].toInt(), m.groupValues[2].toInt(), m.groupValues[1].toInt()) }
        FECHA_YMD.matchEntire(texto)?.let { m -> return LocalDate.of(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt()) }
        null
    }.getOrNull()
}

/** «-$ 1.000.000,00» o «$ 15,08»: el valor como lo escribe el PDF. */
private val VALOR_EN_TEXTO = Regex("""^(-?)\s*\$?\s*(\d{1,3}(?:\.\d{3})*,\d{2}|\d+(?:,\d{1,2})?)$""")

/** El valor de una celda en centavos, con su signo: un número de Excel o un texto como el del PDF. */
private fun centavosDe(celda: Cell?): Long? {
    celda ?: return null
    if (celda.cellType == CellType.NUMERIC || (celda.cellType == CellType.FORMULA && celda.cachedFormulaResultType == CellType.NUMERIC)) {
        return celda.numericCellValue.takeIf { it.isFinite() }?.let { (it * 100).roundToLong() }
    }
    val m = VALOR_EN_TEXTO.matchEntire(textoDe(celda)) ?: return null
    return centavosDelExtracto(m.groupValues[1] + m.groupValues[2])
}
