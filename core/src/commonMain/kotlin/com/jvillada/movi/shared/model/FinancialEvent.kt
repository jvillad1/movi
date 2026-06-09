package com.jvillada.movi.shared.model

import kotlinx.serialization.Serializable

@Serializable
enum class EventSource { MANUAL, SMS, OCR, STATEMENT }

@Serializable
enum class ReconciliationStatus { UNCONFIRMED, RECONCILED, UNMATCHED }

@Serializable
data class FinancialEvent(
    val id: String,
    val accountId: String,
    val type: TransactionType,          // INCOME | EXPENSE (reuse existing enum)
    val amount: Long,                   // in COP pesos
    val currency: String = "COP",       // native currency of the amount (e.g. "COP", "USD")
    val category: String,
    val description: String,
    val merchant: String? = null,
    val timestamp: Long,
    val source: EventSource = EventSource.MANUAL,
    val rawPayload: String? = null,
    val reconciliationStatus: ReconciliationStatus = ReconciliationStatus.UNCONFIRMED,
    val syncedAt: Long? = null,
)

@Serializable
data class VoidEvent(
    val id: String,
    val originalEventId: String,
    val reason: String? = null,
    val timestamp: Long,
)

// Day-grouped view (replaces TransactionDay)
@Serializable
data class EventDay(
    val date: String,
    val total: Long,
    val items: List<FinancialEvent>,
)
