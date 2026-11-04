package com.jvillada.movi.server.parsing

import org.apache.pdfbox.Loader
import org.apache.pdfbox.io.IOUtils
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

    /**
     * Los últimos cuatro dígitos que nombra un extracto. Primero el **nombre del archivo** —la
     * convención del dueño lo trae (`TC_Master_3684_09_2026.pdf`)—, sin los años; después los
     * números **enmascarados** del texto («**** 3684», «XXXX3684», «terminada en 3684»). Un número
     * suelto del texto no cuenta: en un extracto hay montos, fechas y teléfonos de cuatro cifras.
     */
    fun numerosDeCuenta(fileName: String, text: String = ""): List<String> {
        val delArchivo = Regex("""(?<!\d)\d{4}(?!\d)""").findAll(fileName.substringBeforeLast('.'))
            .map { it.value }
            .filterNot { it.toInt() in 1990..2100 }
        val enmascarados = Regex("""(?:[*xX•]{2,}[\s-]*|terminad[ao]\s+en\s+)(\d{4})(?!\d)""", RegexOption.IGNORE_CASE)
            .findAll(text).map { it.groupValues[1] }
        return (delArchivo + enmascarados).distinct().toList()
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

    /**
     * Revisión de lanzamiento (nov-2026): un PDF de 10 MB puede inflarse al leerse (flujos comprimidos
     * con una razón enorme, miles de páginas) y llenar el heap del único proceso. Tres límites: los flujos
     * se guardan en archivos temporales y no en la memoria (`createTempFileOnlyStreamCache`), se leen como
     * mucho [MAX_PAGINAS_DEL_PDF] páginas y el texto se corta en [MAX_TEXTO_DEL_PDF] caracteres: pasado,
     * [PdfDemasiadoGrande], que quien llama trata como un PDF que no se pudo leer. Un extracto real tiene
     * unas pocas páginas y unas decenas de miles de caracteres. Umbrales **elegidos, no medidos**.
     */
    private fun extractPdf(bytes: ByteArray): String =
        Loader.loadPDF(bytes, "", null, null, IOUtils.createTempFileOnlyStreamCache()).use { doc ->
            val stripper = PDFTextStripper().apply { endPage = minOf(doc.numberOfPages, MAX_PAGINAS_DEL_PDF) }
            val texto = TextoConTope(MAX_TEXTO_DEL_PDF)
            stripper.writeText(doc, texto)
            texto.toString()
        }

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

/** Cuántas páginas de un PDF se leen como mucho (ver `extractPdf`). */
internal const val MAX_PAGINAS_DEL_PDF: Int = 200

/** Cuántos caracteres de texto se sacan de un PDF como mucho (ver `extractPdf`). */
internal const val MAX_TEXTO_DEL_PDF: Int = 5_000_000

/** El PDF da más texto del que Movi lee: se trata como uno que no se pudo leer. */
class PdfDemasiadoGrande : RuntimeException("El PDF tiene más texto del que Movi lee")

/** Un `Writer` que junta texto y lanza [PdfDemasiadoGrande] al pasar de [tope] caracteres. */
internal class TextoConTope(private val tope: Int) : java.io.Writer() {
    private val sb = StringBuilder()
    override fun write(cbuf: CharArray, off: Int, len: Int) {
        if (sb.length + len > tope) throw PdfDemasiadoGrande()
        sb.append(cbuf, off, len)
    }
    override fun flush() = Unit
    override fun close() = Unit
    override fun toString(): String = sb.toString()
}
