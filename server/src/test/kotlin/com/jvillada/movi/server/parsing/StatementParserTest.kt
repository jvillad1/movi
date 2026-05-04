package com.jvillada.movi.server.parsing

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StatementParserTest {

    @Test
    fun `extractText returns raw UTF-8 for CSV files`() {
        val csv = "Fecha,Descripcion,Valor\n2025-05-28,Rappi,-48900\n"
        val bytes = csv.toByteArray(Charsets.UTF_8)
        val result = StatementParser.extractText(bytes, "extracto.csv")
        assertEquals(csv, result)
    }

    @Test
    fun `detectBankName extracts first word of filename`() {
        assertEquals("Bancolombia", StatementParser.detectBankName("Bancolombia_Mayo2025.pdf"))
        assertEquals("Davivienda", StatementParser.detectBankName("davivienda_extracto.csv"))
        assertEquals("BBVA", StatementParser.detectBankName("BBVA2025.xls"))
    }

    @Test
    fun `extractText handles empty CSV`() {
        val result = StatementParser.extractText(ByteArray(0), "empty.csv")
        assertTrue(result.isEmpty())
    }
}
