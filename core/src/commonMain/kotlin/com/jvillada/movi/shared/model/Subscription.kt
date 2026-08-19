package com.jvillada.movi.shared.model

import kotlinx.serialization.Serializable

@Serializable
enum class SubStatus { AUTO, CANDIDATE, CONFIRMED, DISMISSED }

@Serializable
enum class SubConfidence { HIGH, MEDIUM, LOW }

@Serializable
data class Subscription(
    val id: String,
    val merchantKey: String,    // canónico: "youtube", "anthropic_claude"
    val displayName: String,    // "YouTube", "Claude"
    val amount: Long,           // gasto mensual típico (mediana de la suma mensual, moneda nativa)
    val currency: String,       // "COP" | "USD"
    val dayOfMonth: Int,        // día típico de cobro
    val status: SubStatus,
    val confidence: SubConfidence,
    val firstSeen: Long,
    val lastSeen: Long,
    val occurrences: Int,       // meses distintos detectados
    val accountId: String? = null,
)

@Serializable
data class SubscriptionsResult(
    val subscriptions: List<Subscription>,
    val monthlyTotalCop: Long,  // suma AUTO+CONFIRMED en COP (USD × TRM)
)

/**
 * F38: alta manual — `POST /api/subscriptions`. La creó el dueño, así que nace CONFIRMED (no
 * hay nada que confirmar); el server deriva `merchantKey` del nombre normalizado con el prefijo
 * `manual_` (ver `SubscriptionRoutes.kt`) para que quede fuera del dominio del detector — este
 * nunca produce ese prefijo, así que un re-scan no la toca ni la duplica.
 */
@Serializable
data class CreateSubscriptionRequest(
    val displayName: String,
    val amount: Long,
    val currency: String,   // "COP" | "USD"
    val dayOfMonth: Int,
)
