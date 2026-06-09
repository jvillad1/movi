package com.jvillada.movi.shared.model

import kotlinx.serialization.Serializable

@Serializable
enum class AccountType { CASH, CHECKING, SAVINGS, CREDIT_CARD, INVESTMENT }

@Serializable
data class Account(
    val id: String,
    val name: String,
    val type: AccountType,
    val balance: Long,      // COP component (derived on read)
    val currency: String = "COP",
    val balancesByCurrency: Map<String, Long> = emptyMap(),  // derived: per-currency balance
    val estimatedTotalCop: Long? = null,                     // derived: COP + foreign × TRM
)
