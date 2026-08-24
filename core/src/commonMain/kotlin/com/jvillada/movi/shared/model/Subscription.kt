package com.jvillada.movi.shared.model

import kotlinx.serialization.Serializable

@Serializable
enum class SubStatus { AUTO, CANDIDATE, CONFIRMED, DISMISSED }

/**
 * F38: prefijo de [Subscription.merchantKey] que marca un alta MANUAL — la escribió el dueño,
 * no la encontró el detector.
 *
 * `normalizeMerchant` (SubscriptionDetector.kt) deriva su clave de la descripción del EVENTO
 * bancario y nunca antepone este prefijo, así que una fila `manual_*` queda estructuralmente
 * fuera de lo que el detector puede generar o re-escribir: un re-scan no la toca ni la duplica.
 *
 * Vive en `:core` (y no privado en el server, como nació) porque desde la Ola 8 el cliente
 * también lo necesita: en la lista única de Recurrentes, una suscripción SIN este prefijo lleva
 * la marca «la encontró Movi», y una con él no — es la única señal de origen que hay, porque
 * [SubStatus.CONFIRMED] cubre por igual «la detectó y la confirmé» y «la escribí yo».
 */
const val MANUAL_SUB_PREFIX = "manual_"

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
