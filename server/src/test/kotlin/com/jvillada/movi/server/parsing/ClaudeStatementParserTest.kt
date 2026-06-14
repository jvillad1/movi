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

    @Test
    fun `parseJson maps currency and defaults to COP`() {
        val json = """[
          {"date":"2026-06-04","merchant":"Anthropic","amount":100,"currency":"USD","type":"EXPENSE","category":"Tecnología","description":"","rawText":""},
          {"date":"2026-05-31","merchant":"YouTube","amount":79000,"type":"EXPENSE","category":"Entretenimiento","description":"","rawText":""}
        ]"""
        val result = ClaudeStatementParser.parseJson(json)
        assertEquals(2, result.size)
        assertEquals("USD", result[0].currency)
        assertEquals("COP", result[1].currency) // absent -> default
    }

    @Test
    fun `parseJson normalizes currency casing and blanks`() {
        val json = """[
          {"date":"2026-06-04","merchant":"A","amount":100,"currency":"usd","type":"EXPENSE","category":"Tecnología","description":"","rawText":""},
          {"date":"2026-06-04","merchant":"B","amount":50,"currency":" ","type":"EXPENSE","category":"Otros","description":"","rawText":""}
        ]"""
        val r = ClaudeStatementParser.parseJson(json)
        assertEquals("USD", r[0].currency)
        assertEquals("COP", r[1].currency)
    }

    // image-branch offline tests — no network calls, exercise the mime/extension helpers only

    @Test
    fun `isImageMime detects image mime types correctly`() {
        assertTrue(ClaudeStatementParser.isImageMime("image/png"))
        assertTrue(ClaudeStatementParser.isImageMime("image/jpeg"))
        assertTrue(ClaudeStatementParser.isImageMime("image/webp"))
        assertTrue(ClaudeStatementParser.isImageMime("image/gif"))
        assertTrue(ClaudeStatementParser.isImageMime("image/heic"))
    }

    @Test
    fun `isImageMime rejects non-image types`() {
        assertTrue(!ClaudeStatementParser.isImageMime("application/pdf"))
        assertTrue(!ClaudeStatementParser.isImageMime("text/csv"))
        assertTrue(!ClaudeStatementParser.isImageMime("application/vnd.ms-excel"))
        assertTrue(!ClaudeStatementParser.isImageMime(""))
    }

    @Test
    fun `supportedImageMime maps supported types and rejects unsupported`() {
        // direct mime
        assertEquals("image/png", ClaudeStatementParser.supportedImageMime("image/png", "x.png"))
        assertEquals("image/jpeg", ClaudeStatementParser.supportedImageMime("image/jpeg", "x.jpg"))
        assertEquals("image/jpeg", ClaudeStatementParser.supportedImageMime("image/jpg", "x.jpg")) // jpg -> jpeg
        // blank mime falls back to filename extension
        assertEquals("image/png", ClaudeStatementParser.supportedImageMime("", "captura.png"))
        assertEquals("image/jpeg", ClaudeStatementParser.supportedImageMime("application/octet-stream", "foto.JPEG"))
        // unsupported (HEIC) -> null so the route can 422 instead of crashing
        assertEquals(null, ClaudeStatementParser.supportedImageMime("image/heic", "foto.heic"))
        assertEquals(null, ClaudeStatementParser.supportedImageMime("", "foto.heic"))
    }
}
