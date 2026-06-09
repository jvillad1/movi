package com.jvillada.movi.shared.model

import kotlinx.serialization.Serializable

@Serializable
data class ParsedTransaction(
    val id: String,           // UUID, session-scoped
    val date: String,         // "2025-05-28"
    val merchant: String,
    val amount: Long,         // native currency, always positive
    val currency: String = "COP",
    val type: TransactionType,
    val category: String,
    val description: String,
    val rawText: String,
)

@Serializable
data class ReconciliationMatch(
    val parsed: ParsedTransaction,
    val existingEventId: String,
    val existingEvent: FinancialEvent,
    val matchConfidence: Float,
)

@Serializable
data class StatementParseResult(
    val statementId: String,
    val bankName: String,
    val period: String,
    val newTransactions: List<ParsedTransaction>,
    val matches: List<ReconciliationMatch>,
)

@Serializable
data class ReconciliationDecision(
    val parsedId: String,
    val existingEventId: String,
    val confirm: Boolean,
    val categorySource: FieldSource,
    val descriptionSource: FieldSource,
    val merchantSource: FieldSource,
    val parsed: ParsedTransaction,
)

@Serializable
enum class FieldSource { MANUAL, STATEMENT }

@Serializable
data class ImportDecision(
    val statementId: String,
    val accountId: String,
    val bankName: String = "",
    val period: String = "",
    val imports: List<ParsedTransaction>,
    val reconciliations: List<ReconciliationDecision>,
    val skipped: List<String>,
)

@Serializable
data class MerchantRule(
    val merchantPattern: String,
    val category: String,
)

@Serializable
data class StatementImport(
    val id: String,
    val accountId: String,
    val bankName: String,
    val period: String,
    val importedAt: Long,
    val importedCount: Int,
    val reconciledCount: Int,
)

@Serializable
data class StatementImportDetail(
    val import: StatementImport,
    val events: List<FinancialEvent>,
)
