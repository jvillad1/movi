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
data class CreditTerms(
    val accountId: String,
    val bank: String,
    val principal: Long,        // capital original (COP)
    val rateEa: Double,         // % EA, p.ej. 17.46
    val termMonths: Int,
    val installment: Long,      // cuota mensual total (incl. seguros)
    val dayOfMonth: Int,        // día de pago
    val startDate: String,      // ISO "2026-06-01" (desembolso)
    val notes: String? = null,
)

@Serializable
data class CreditSummary(
    val account: Account,       // cuenta LOAN con deuda derivada en balance
    val terms: CreditTerms?,    // null si la cuenta LOAN aún no tiene términos
    val paidPct: Double?,       // 1 − deuda/principal clampado a [0,1]; null sin términos
)

/**
 * Prefijo de los ids de las reglas recurrentes sintéticas derivadas de credit_terms.
 * Compartido entre el server (que las genera) y la UI (que las distingue de las reales).
 */
const val CREDIT_RULE_PREFIX = "credit_"

/** Alta atómica de un crédito: cuenta LOAN + evento de apertura + términos en una sola operación server-side. */
@Serializable
data class CreateCreditRequest(
    val name: String,           // nombre de la cuenta LOAN a crear
    val initialDebt: Long,      // deuda actual (COP) — genera el evento "Deuda inicial"
    val terms: CreditTerms,     // accountId se ignora; el server asigna el de la cuenta nueva
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
    val id: String,
    val time: String,
    val bank: String,
    val text: String,
    val state: String,
    val det: String,
)

@Serializable
data class ParsedSms(
    val amount: Double,
    val merchant: String,
    val type: TransactionType,
    val category: String,
)

@Serializable
enum class ChatRole { USER, ASSISTANT }

@Serializable
data class ChatMessage(
    val role: ChatRole,
    val content: String,
)

@Serializable
data class AiChatRequest(val messages: List<ChatMessage>)

@Serializable
data class AiChatResponse(val text: String)

@Serializable
enum class PaymentStatus { OVERDUE, DUE_TODAY, DUE_SOON, UPCOMING }

@Serializable
data class UpcomingPayment(
    val rule: RecurringRule,
    val dueDate: String,    // ISO "2026-06-05", current month
    val daysUntil: Int,     // negative if overdue
    val status: PaymentStatus,
)
