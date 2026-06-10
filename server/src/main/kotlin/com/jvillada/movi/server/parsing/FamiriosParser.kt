package com.jvillada.movi.server.parsing

import com.jvillada.movi.shared.model.ParsedTransaction
import com.jvillada.movi.shared.model.TransactionType
import org.apache.poi.ss.usermodel.Cell
import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.ss.usermodel.DateUtil
import org.apache.poi.ss.usermodel.Row
import org.apache.poi.ss.usermodel.Sheet
import org.apache.poi.ss.usermodel.Workbook
import java.text.Normalizer
import java.time.LocalDate
import java.util.UUID
import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * Deterministic parser for the user's "Famirios" budget spreadsheet
 * (one tab per year; rows = categories, columns = months). Each non-zero
 * (category × month) cell becomes one monthly-aggregate [ParsedTransaction].
 * Months after [today] are excluded — Famirios pre-fills the whole year with
 * projections. See docs/superpowers/specs/2026-06-10-famirios-import-design.md.
 */
object FamiriosParser {

    private val MONTHS = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun",
                                "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
    private val MONTH_NAMES_ES = listOf("enero", "febrero", "marzo", "abril", "mayo", "junio",
                                        "julio", "agosto", "septiembre", "octubre", "noviembre", "diciembre")

    private val NOISE_EXACT = setOf(
        "resumen", "dineros iniciales", "ingresos", "gastos fijos",
        "gastos extraordinarios", "gastos", "tipo de ingreso", "saldo",
    )
    private val NOISE_CONTAINS = listOf("ytd", "promedio", "presupuesto", "total", "gastos (%)")

    private enum class Section { NONE, INCOME, EXPENSE }

    fun parse(workbook: Workbook, today: LocalDate): List<ParsedTransaction> {
        val out = mutableListOf<ParsedTransaction>()
        for (i in 0 until workbook.numberOfSheets) {
            val sheet = workbook.getSheetAt(i)
            val year = sheet.sheetName.trim().toIntOrNull()?.takeIf { it in 2000..2099 } ?: continue
            parseYearSheet(sheet, year, today, out)
        }
        return out
    }

    private fun parseYearSheet(sheet: Sheet, year: Int, today: LocalDate, out: MutableList<ParsedTransaction>) {
        var monthCols: Map<Int, Int> = emptyMap()  // column index -> month (1..12)
        var section = Section.NONE

        for (row in sheet) {
            val headerCols = monthColumns(row)
            if (headerCols.size >= 6) monthCols = headerCols

            if (rowHasErrorLabel(row)) continue              // #REF! label
            val labelPair = labelOf(row)
            val label = labelPair?.first
            val labelCol = labelPair?.second ?: -1
            val norm = label?.let(::normalize)

            when (norm) {
                "tipo de ingreso" -> { section = Section.INCOME; continue }
                "gastos", "gastos extraordinarios" -> { section = Section.EXPENSE; continue }
                "resumen" -> { section = Section.NONE; continue }
            }
            if (norm != null && norm.contains("gastos (%)")) { section = Section.NONE; continue }

            if (section == Section.NONE || label == null || monthCols.isEmpty()) continue
            if (norm in NOISE_EXACT || NOISE_CONTAINS.any { norm!!.contains(it) }) continue

            for ((col, month) in monthCols) {
                if (col == labelCol) continue                // never read the label cell as a value
                if (year > today.year || (year == today.year && month > today.monthValue)) continue
                val raw = numericAt(row, col) ?: continue
                if (raw == 0.0) continue
                val lastDay = LocalDate.of(year, month, 1).plusMonths(1).minusDays(1)
                out += ParsedTransaction(
                    id = UUID.randomUUID().toString(),
                    date = lastDay.toString(),
                    merchant = label,
                    amount = abs(raw).roundToLong(),
                    currency = "COP",
                    type = if (section == Section.INCOME) TransactionType.INCOME else TransactionType.EXPENSE,
                    category = categoryFor(label, section),
                    description = "Famirios · $label · ${MONTH_NAMES_ES[month - 1]} $year",
                    rawText = "${sheet.sheetName}!$label:${MONTHS[month - 1]}=$raw",
                )
            }
        }
    }

    private fun monthColumns(row: Row): Map<Int, Int> {
        val map = mutableMapOf<Int, Int>()
        for (cell in row) {
            when (effectiveType(cell)) {
                CellType.STRING -> {
                    val m = MONTHS.indexOf(cell.stringCellValue.trim())
                    if (m >= 0) map[cell.columnIndex] = m + 1
                }
                CellType.NUMERIC -> {
                    if (DateUtil.isCellDateFormatted(cell)) {
                        map[cell.columnIndex] = cell.localDateTimeCellValue.monthValue
                    }
                }
                else -> {}
            }
        }
        return map
    }

    /** Returns (labelText, columnIndex) of the first non-blank STRING cell in columns 0..2. */
    private fun labelOf(row: Row): Pair<String, Int>? {
        for (col in 0..2) {
            val text = row.getCell(col)?.let { stringAt(it) }?.trim()
            if (!text.isNullOrBlank()) return text to col
        }
        return null
    }

    private fun rowHasErrorLabel(row: Row): Boolean {
        for (col in 0..2) {
            val cell = row.getCell(col) ?: continue
            when (effectiveType(cell)) {
                CellType.ERROR -> return true
                CellType.STRING -> return false   // real label found first
                else -> {}
            }
        }
        return false
    }

    private fun stringAt(cell: Cell): String? = when (effectiveType(cell)) {
        CellType.STRING -> cell.stringCellValue
        else -> null
    }

    private fun numericAt(row: Row, col: Int): Double? {
        val cell = row.getCell(col) ?: return null
        return when (effectiveType(cell)) {
            CellType.NUMERIC -> cell.numericCellValue
            else -> null
        }
    }

    /** Resolves FORMULA cells to their cached result type. */
    private fun effectiveType(cell: Cell): CellType =
        if (cell.cellType == CellType.FORMULA) cell.cachedFormulaResultType else cell.cellType

    private fun normalize(s: String): String =
        Normalizer.normalize(s.lowercase().trim(), Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")

    /** First match wins; ordering is deliberate (crédito before vehículo). */
    private fun categoryFor(label: String, section: Section): String {
        val n = normalize(label)
        if (section == Section.INCOME) {
            return if (listOf("pay", "salario", "nomina", "cesantias").any { n.contains(it) }) "Salario"
                   else "Otros ingresos"
        }
        return when {
            listOf("credito", "tarjeta", "deuda", "nubank").any { n.contains(it) } -> "Otros"
            listOf("arriendo", "apartamento", "hogar", "casa", "administr").any { n.contains(it) } -> "Vivienda"
            listOf("mercado", "rappi").any { n.contains(it) } -> "Comida"
            listOf("gasolina", "vehiculo", "carro", "soat", "seguro del carro").any { n.contains(it) } -> "Transporte"
            listOf("celular", "internet", "servicios").any { n.contains(it) } -> "Servicios"
            listOf("gym", "coomeva", "prepagada", "salud", "eps").any { n.contains(it) } -> "Salud"
            listOf("diversion", "entreten", "happy").any { n.contains(it) } -> "Entretenimiento"
            else -> "Otros"
        }
    }
}
