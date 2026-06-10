package com.jvillada.movi.server.parsing

import org.apache.pdfbox.Loader
import org.apache.pdfbox.text.PDFTextStripper
import org.apache.poi.ss.usermodel.WorkbookFactory
import java.io.ByteArrayInputStream

enum class StatementDocumentType { TRANSACTION_STATEMENT, LOAN_SUMMARY, INVESTMENT_FUND, FAMIRIOS }

object StatementParser {

    private val KNOWN_BANKS = listOf(
        "Bancolombia", "Davivienda", "BBVA", "Nequi", "Falabella",
        "Colpatria", "Bogotá", "Itaú", "AV Villas", "Nu", "Cibest",
    )

    fun extractText(bytes: ByteArray, fileName: String): String {
        val ext = fileName.substringAfterLast('.').lowercase()
        return when (ext) {
            "pdf"         -> extractPdf(bytes)
            "csv"         -> bytes.toString(Charsets.UTF_8)
            "xls", "xlsx" -> extractSpreadsheet(bytes)
            else          -> bytes.toString(Charsets.UTF_8)
        }
    }

    fun detectBankName(fileName: String, text: String = ""): String {
        val base = fileName.substringBeforeLast('.')
        val firstSegment = base.split('_', '-', ' ').firstOrNull { it.isNotBlank() } ?: base
        val firstWord = firstSegment.trimEnd { it.isDigit() }
        if (firstWord.any { it.isLetter() }) {
            return firstWord.replaceFirstChar { it.uppercaseChar() }
        }
        // Filename starts with digits — scan document content for known bank names
        if (text.isNotBlank()) {
            for (bank in KNOWN_BANKS) {
                if (text.contains(bank, ignoreCase = true)) return bank
            }
        }
        return firstWord.ifEmpty { base }
    }

    fun detectDocumentType(text: String): StatementDocumentType {
        val upper = text.uppercase()
        if (upper.contains("RESUMÉN") && upper.contains("TIPO DE INGRESO") && upper.contains("GASTOS FIJOS"))
            return StatementDocumentType.FAMIRIOS
        if ((upper.contains("LÍNEA DE CRÉDITO") || upper.contains("LINEA DE CREDITO") ||
             upper.contains("CREDIÁGIL") || upper.contains("CREDITO PREAPROBADO")) &&
            upper.contains("ABONO A CAPITAL")
        ) return StatementDocumentType.LOAN_SUMMARY
        if (upper.contains("FONDO DE INVERSIÓN COLECTIVA") || upper.contains("FONDO DE INVERSION COLECTIVA") ||
            upper.contains("VALOR EN UNIDADES")
        ) return StatementDocumentType.INVESTMENT_FUND
        return StatementDocumentType.TRANSACTION_STATEMENT
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
