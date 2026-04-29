package com.jvillada.movi.shared.model

import kotlinx.serialization.Serializable

@Serializable
data class Wallet(
    val id: String,
    val name: String,
    val balance: Double,
    val currency: String,
)

@Serializable
data class Transaction(
    val id: String,
    val walletId: String,
    val name: String,
    val amount: Double,
    val category: String,
    val type: TransactionType,
    val source: TransactionSource,
    val pending: Boolean = false,
    val timestamp: Long,
)

@Serializable
enum class TransactionType { INCOME, EXPENSE }

@Serializable
enum class TransactionSource { SMS, OCR, MANUAL }

@Serializable
data class TransactionDay(
    val date: String,
    val total: Double,
    val items: List<Transaction>,
)
