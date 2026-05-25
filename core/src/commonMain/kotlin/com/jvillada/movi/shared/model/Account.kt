package com.jvillada.movi.shared.model

import kotlinx.serialization.Serializable

@Serializable
enum class AccountType { CASH, CHECKING, SAVINGS, CREDIT_CARD, INVESTMENT }

@Serializable
data class Account(
    val id: String,
    val name: String,
    val type: AccountType,
    val balance: Long,      // in COP pesos (integer, no decimals)
    val currency: String = "COP",
)
