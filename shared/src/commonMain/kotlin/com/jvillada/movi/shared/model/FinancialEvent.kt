package com.jvillada.movi.shared.model

import kotlinx.serialization.Serializable

@Serializable
enum class EventSource {
    SMS,
    OCR,
    MANUAL,
}

@Serializable
enum class ReconciliationStatus {
    PENDING,
    UNCONFIRMED,
    RECONCILED,
}

@Serializable
data class FinancialEvent(
    val id: String,
    val accountId: String,
    val type: TransactionType,
    val amount: Long,          // in COP pesos (integer, no decimals)
    val category: String,
    val merchant: String,
    val description: String?,  // nullable for income transactions
    val timestamp: Long,       // milliseconds since epoch
    val source: EventSource,
    val reconciliationStatus: ReconciliationStatus = ReconciliationStatus.PENDING,
)

@Serializable
data class VoidEvent(
    val id: String,
    val eventId: String,
    val reason: String,
    val timestamp: Long,       // milliseconds since epoch
)
