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
    val amount: Double,
    val description: String,
    val type: TransactionType,
    val timestamp: Long,
)

@Serializable
enum class TransactionType { INCOME, EXPENSE, TRANSFER }
