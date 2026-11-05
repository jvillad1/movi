package com.jvillada.movi.server.parsing

import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.apache.pdfbox.pdmodel.font.Standard14Fonts
import java.io.ByteArrayOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** Revisión de lanzamiento: la lectura de un PDF tiene topes de páginas y de texto (ver `extractPdf`). */
class ElPdfConTopeTest {

    private fun pdf(paginas: Int, texto: String = "Compra en Rappi 27.500"): ByteArray {
        val out = ByteArrayOutputStream()
        PDDocument().use { doc ->
            repeat(paginas) { i ->
                val pagina = PDPage()
                doc.addPage(pagina)
                PDPageContentStream(doc, pagina).use { cs ->
                    cs.beginText()
                    cs.setFont(PDType1Font(Standard14Fonts.FontName.HELVETICA), 11f)
                    cs.newLineAtOffset(50f, 700f)
                    cs.showText("$texto pagina ${i + 1}")
                    cs.endText()
                }
            }
            doc.save(out)
        }
        return out.toByteArray()
    }

    @Test
    fun `un pdf normal se lee igual`() {
        val texto = StatementParser.extractText(pdf(2), "extracto.pdf")
        assertTrue("Compra en Rappi 27.500 pagina 1" in texto, texto)
        assertTrue("pagina 2" in texto, texto)
    }

    @Test
    fun `se leen como mucho las primeras paginas`() {
        val texto = StatementParser.extractText(pdf(MAX_PAGINAS_DEL_PDF + 5), "largo.pdf")
        assertTrue("pagina $MAX_PAGINAS_DEL_PDF\n" in texto || texto.trimEnd().endsWith("pagina $MAX_PAGINAS_DEL_PDF"), "no llegó a la última que se lee")
        assertTrue("pagina ${MAX_PAGINAS_DEL_PDF + 1}" !in texto, "leyó de más")
    }

    @Test
    fun `el texto tiene tope`() {
        val w = TextoConTope(10)
        w.write("0123456789")
        assertEquals("0123456789", w.toString())
        assertFailsWith<PdfDemasiadoGrande> { w.write("x") }
    }
}
