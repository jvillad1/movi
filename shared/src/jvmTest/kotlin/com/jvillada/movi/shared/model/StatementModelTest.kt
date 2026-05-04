package com.jvillada.movi.shared.model

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class StatementModelTest {

    @Test
    fun `StatementImport round-trips through JSON`() {
        val original = StatementImport(
            id = "si_abc",
            accountId = "acc_1",
            bankName = "Bancolombia",
            period = "Mayo 2025",
            importedAt = 1_700_000_000_000L,
            importedCount = 21,
            reconciledCount = 2,
        )
        val json = Json.encodeToString(original)
        val decoded = Json.decodeFromString<StatementImport>(json)
        assertEquals(original, decoded)
    }

    @Test
    fun `StatementImportDetail round-trips through JSON`() {
        val imp = StatementImport("si_1", "acc_1", "BBVA", "Abril 2025", 1_000L, 5, 1)
        val detail = StatementImportDetail(imp, emptyList())
        val json = Json.encodeToString(detail)
        val decoded = Json.decodeFromString<StatementImportDetail>(json)
        assertEquals(detail, decoded)
    }

    @Test
    fun `ImportDecision includes bankName and period`() {
        val decision = ImportDecision(
            statementId = "s1",
            accountId = "acc1",
            bankName = "Nequi",
            period = "Marzo 2025",
            imports = emptyList(),
            reconciliations = emptyList(),
            skipped = emptyList(),
        )
        val json = Json.encodeToString(decision)
        val decoded = Json.decodeFromString<ImportDecision>(json)
        assertEquals("Nequi", decoded.bankName)
        assertEquals("Marzo 2025", decoded.period)
    }
}
