package com.jvillada.movi.server.parsing

import com.jvillada.movi.shared.model.TransactionType
import org.apache.poi.ss.usermodel.FormulaError
import org.apache.poi.ss.usermodel.Workbook
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FamiriosParserTest {

    private val MONTHS = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun",
                                "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

    /** Build a minimal Famirios-shaped workbook: a noise tab + two year tabs. */
    private fun famiriosWorkbook(): Workbook {
        val wb = XSSFWorkbook()

        wb.createSheet("How to Guide").createRow(0).createCell(0)
            .setCellValue("This spreadsheet is used to track expenses")

        // ---- 2024 tab: header offset by one extra leading column ("Death") ----
        val s24 = wb.createSheet("2024")
        var r = 0
        s24.createRow(r).also { row ->                      // header row with extra col
            row.createCell(0).setCellValue("Resumén")
            row.createCell(1).setCellValue("Death")
            MONTHS.forEachIndexed { i, m -> row.createCell(i + 2).setCellValue(m) }
        }; r++
        s24.createRow(r).also { it.createCell(0).setCellValue("Ingresos"); it.createCell(2).setCellValue(999.0) }; r++  // summary noise
        s24.createRow(r).also { it.createCell(0).setCellValue("Tipo de ingreso") }; r++
        s24.createRow(r).also { row ->
            row.createCell(0).setCellValue("Income Biweekly Pay 1")
            row.createCell(2).setCellValue(1_000_000.0)      // Jan
            row.createCell(3).setCellValue(2_000_000.0)      // Feb
        }; r++
        s24.createRow(r).also { it.createCell(0).setCellValue("Gastos") }; r++
        s24.createRow(r).also { row ->
            row.createCell(0).setCellValue("Arriendo")
            row.createCell(2).setCellValue(500_000.0)        // Jan
        }; r++
        s24.createRow(r).also { row ->
            row.createCell(0).setCellValue("Crédito Vehículo")
            row.createCell(2).setCellValue(300_000.49)       // Jan — rounds to 300000
        }; r++
        s24.createRow(r).also { row ->                       // #REF! label row -> skipped
            row.createCell(0).setCellErrorValue(FormulaError.REF.code)
            row.createCell(2).setCellValue(123.0)
        }; r++
        s24.createRow(r).also { row ->                       // YTD noise row -> skipped
            row.createCell(0).setCellValue("YTD TOTAL")
            row.createCell(2).setCellValue(456.0)
        }

        // ---- 2026 tab: section row first, then header, then data ----
        val s26 = wb.createSheet("2026")
        var q = 0
        s26.createRow(q).also { it.createCell(0).setCellValue("Gastos") }; q++
        s26.createRow(q).also { row ->
            row.createCell(0).setCellValue("")
            MONTHS.forEachIndexed { i, m -> row.createCell(i + 1).setCellValue(m) }
        }; q++
        s26.createRow(q).also { row ->
            row.createCell(0).setCellValue("Mercado")
            row.createCell(5).setCellValue(2_000_000.0)      // May 2026 (header col 5 = May)
            row.createCell(9).setCellValue(3_000_000.0)      // Sep 2026 — FUTURE, excluded
        }

        // ---- 2025 tab: REAL export layout — spacer col 0, labels in col 1,
        // month headers as DATE-FORMATTED NUMERIC cells (Sheets export artifact) ----
        val s25 = wb.createSheet("2025")
        val dateStyle = wb.createCellStyle().apply {
            dataFormat = wb.creationHelper.createDataFormat().getFormat("mmm")
        }
        var p = 0
        s25.createRow(p).also { row ->
            row.createCell(1).setCellValue("Resumén")
            for (m in 1..12) {
                val cell = row.createCell(m + 1)                 // months start at col 2
                cell.setCellValue(java.sql.Date.valueOf(java.time.LocalDate.of(2020, m, 1)))
                cell.cellStyle = dateStyle
            }
        }; p++
        s25.createRow(p).also { it.createCell(1).setCellValue("Saldo"); it.createCell(2).setCellValue(777.0) }; p++  // summary noise
        s25.createRow(p).also { it.createCell(1).setCellValue("Gastos") }; p++
        s25.createRow(p).also { row ->
            row.createCell(1).setCellValue("Gasolina")
            row.createCell(4).setCellValue(450_000.0)            // col 4 = Mar 2025
        }; p++
        s25.createRow(p).also { it.createCell(1).setCellValue("Gastos extraordinarios") }; p++
        s25.createRow(p).also { row ->
            row.createCell(1).setCellValue("Imprevistos")
            row.createCell(3).setCellValue(800_000.0)            // col 3 = Feb 2025
        }

        return wb
    }

    @Test
    fun `parses sections, maps categories, skips noise and future months`() {
        val today = LocalDate.of(2026, 6, 15)
        val txs = FamiriosParser.parse(famiriosWorkbook(), today)

        // 2024: 2 income + 2 expense; 2025: 1 gasto + 1 extraordinario; 2026: 1 (Sep excluded).
        assertEquals(7, txs.size)

        val imprevistos = txs.first { it.merchant == "Imprevistos" }
        assertEquals(TransactionType.EXPENSE, imprevistos.type)   // Gastos extraordinarios section
        assertEquals("2025-02-28", imprevistos.date)

        val income = txs.filter { it.type == TransactionType.INCOME }
        assertEquals(2, income.size)
        assertEquals("Salario", income.first().category)
        assertEquals("2024-01-31", income.first { it.amount == 1_000_000L }.date)
        assertEquals("2024-02-29", income.first { it.amount == 2_000_000L }.date) // leap year

        val arriendo = txs.first { it.merchant == "Arriendo" }
        assertEquals("Vivienda", arriendo.category)
        assertEquals(TransactionType.EXPENSE, arriendo.type)

        val vehiculo = txs.first { it.merchant == "Crédito Vehículo" }
        assertEquals("Otros", vehiculo.category)              // crédito wins over vehículo
        assertEquals(300_000L, vehiculo.amount)               // rounded

        val mercado = txs.first { it.merchant == "Mercado" }
        assertEquals("Comida", mercado.category)
        assertEquals("2026-05-31", mercado.date)
        assertTrue(txs.none { it.date.startsWith("2026-09") }) // future month excluded

        val gasolina = txs.first { it.merchant == "Gasolina" }
        assertEquals("Transporte", gasolina.category)
        assertEquals("2025-03-31", gasolina.date)
        assertTrue(txs.none { it.merchant == "Saldo" })          // summary row skipped

        assertTrue(txs.all { it.currency == "COP" })
        assertTrue(txs.all { it.rawText.isNotBlank() })
    }

    @Test
    fun `returns empty for a workbook with no year tabs`() {
        val wb = XSSFWorkbook()
        wb.createSheet("Notas").createRow(0).createCell(0).setCellValue("hola")
        assertTrue(FamiriosParser.parse(wb, LocalDate.of(2026, 6, 15)).isEmpty())
    }
}
