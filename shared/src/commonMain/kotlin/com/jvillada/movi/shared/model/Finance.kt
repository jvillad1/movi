package com.jvillada.movi.shared.model

import kotlinx.serialization.Serializable

@Serializable
enum class Scope { SELF, FAMILY }

@Serializable
data class FinanceSummary(
    val scope: Scope,
    val balance: Long,
    val ingresos: Long,
    val egresos: Long,
)

@Serializable
data class Holding(
    val name: String,
    val sub: String,
    val amount: Long,
    val change: Double,
)

@Serializable
data class Credit(
    val name: String,
    val bank: String,
    val total: Long,
    val paid: Long,
    val rate: String,
    val nextDate: String,
    val nextAmt: String,
)

@Serializable
data class Goal(
    val name: String,
    val target: Long,
    val saved: Long,
    val deadline: String,
    val monthly: Long,
)

@Serializable
data class RecurringRule(
    val id: String,
    val name: String,
    val category: String,
    val amount: Long,
    val dayOfMonth: Int,
    val type: TransactionType,
)

@Serializable
data class Budget(
    val category: String,
    val monthlyLimit: Long,
)

@Serializable
data class SmsMessage(
    val time: String,
    val bank: String,
    val text: String,
    val state: String,
    val det: String,
)
