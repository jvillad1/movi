package com.jvillada.movi.server.parsing

import org.apache.pdfbox.Loader
import org.apache.pdfbox.text.PDFTextStripper
import org.apache.poi.ss.usermodel.WorkbookFactory
import java.io.ByteArrayInputStream

object StatementParser {

    fun extractText(bytes: ByteArray, fileName: String): String {
        val ext = fileName.substringAfterLast('.').lowercase()
        return when (ext) {
            "pdf"         -> extractPdf(bytes)
            "csv"         -> bytes.toString(Charsets.UTF_8)
            "xls", "xlsx" -> extractSpreadsheet(bytes)
            else          -> bytes.toString(Charsets.UTF_8)
        }
    }

    fun detectBankName(fileName: String): String {
        val base = fileName.substringBeforeLast('.')
        val firstSegment = base.split('_', '-', ' ').firstOrNull { it.isNotBlank() } ?: base
        // Strip any trailing digit run (e.g. "BBVA2025" → "BBVA")
        val firstWord = firstSegment.trimEnd { it.isDigit() }.ifEmpty { firstSegment }
        return firstWord.replaceFirstChar { it.uppercaseChar() }
    }

    private fun extractPdf(bytes: ByteArray): String =
        Loader.loadPDF(bytes).use { doc -> PDFTextStripper().getText(doc) }

    private fun extractSpreadsheet(bytes: ByteArray): String {
        val wb = WorkbookFactory.create(ByteArrayInputStream(bytes))
        return wb.use { workbook ->
            buildString {
                repeat(workbook.numberOfSheets) { sheetIdx ->
                    val sheet = workbook.getSheetAt(sheetIdx)
                    sheet.forEach { row ->
                        appendLine(row.joinToString("\t") { cell ->
                            cell.toString().trim()
                        })
                    }
                }
            }
        }
    }
}
