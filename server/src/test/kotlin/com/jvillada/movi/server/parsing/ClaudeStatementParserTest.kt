package com.jvillada.movi.server.parsing

import com.jvillada.movi.shared.model.TransactionType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ClaudeStatementParserTest {

    @Test
    fun `parseJson extracts transactions from clean JSON array`() {
        val json = """[{"date":"2025-05-28","merchant":"Rappi","amount":48900,"type":"EXPENSE","category":"Restaurantes","description":"Domicilio","rawText":"COMPRA RAPPI"}]"""
        val result = ClaudeStatementParser.parseJson(json)
        assertEquals(1, result.size)
        assertEquals("Rappi", result[0].merchant)
        assertEquals(48900L, result[0].amount)
        assertEquals(TransactionType.EXPENSE, result[0].type)
    }

    @Test
    fun `parseJson extracts JSON array embedded in prose`() {
        val json = """Here are the transactions: [{"date":"2025-05-25","merchant":"Globant","amount":4500000,"type":"INCOME","category":"Salario","description":"Nomina","rawText":"ABONO NOMINA"}] end."""
        val result = ClaudeStatementParser.parseJson(json)
        assertEquals(1, result.size)
        assertEquals("Globant", result[0].merchant)
        assertEquals(TransactionType.INCOME, result[0].type)
    }

    @Test
    fun `parseJson returns empty list for invalid JSON`() {
        val result = ClaudeStatementParser.parseJson("no json here")
        assertTrue(result.isEmpty())
    }

    @Test
    fun `parseJson assigns unique IDs to each transaction`() {
        val json = """[
          {"date":"2025-05-28","merchant":"A","amount":100,"type":"EXPENSE","category":"Otro","description":"","rawText":""},
          {"date":"2025-05-27","merchant":"B","amount":200,"type":"EXPENSE","category":"Otro","description":"","rawText":""}
        ]"""
        val result = ClaudeStatementParser.parseJson(json)
        assertEquals(2, result.size)
        assertTrue(result[0].id != result[1].id)
        assertTrue(result[0].id.isNotBlank())
    }
}
